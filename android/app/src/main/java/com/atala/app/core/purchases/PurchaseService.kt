package com.atala.app.core.purchases

import android.content.Context
import com.atala.app.core.l10n.L
import com.atala.app.core.model.ProAccess
import com.atala.app.core.store.AppStore
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.Period
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** The three ways to subscribe, as on the iPhone. */
enum class PurchasePlan { Yearly, Monthly, Gift }

/** A plan as the store sells it in the user's country. */
data class PurchaseOffer(
    /** "€49.99" */
    val price: String,
    /** The same price per month ("€4.17"), when the store can work it out. */
    val perMonth: String?,
    /** The same price per week ("€0.48"), when the store can work it out. */
    val perWeek: String?,
    /** Length of the free trial in days, null when the user is not offered one. */
    val trialDays: Int?,
    /** The price in millionths of the currency, to compare plans. */
    val amountMicros: Long = 0,
)

sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

/**
 * Atala Pro through RevenueCat (entitlement `atala_pro`, offerings `default` and `gift`), as
 * PurchaseService.swift. `isPro` is mirrored in [ProAccess].
 *
 * Placeholder mode: while the store has no products (the Google Play products do not exist yet),
 * the paywall and the gift show example prices with a notice, and their buttons stand in for a
 * purchase ([setPlaceholderPro]), exactly like an iPhone build without the RevenueCat package.
 * A network failure is not the placeholder: buying then fails with a message.
 */
object PurchaseService {
    /** Public SDK key of the RevenueCat app "Atala (Android)". Public keys are meant to ship in the app. */
    const val API_KEY = "goog_BQBaBcfIfbfjsaMjNQQqNsLrPvO"
    /** Identifier of the entitlement that Atala Pro unlocks. */
    const val ENTITLEMENT_ID = "atala_pro"
    /** Identifier of the RevenueCat offering that holds the welcome gift. */
    const val GIFT_OFFERING_ID = "gift"

    private const val PLACEHOLDER_PRO_KEY = "atala.placeholder.pro"
    private const val TRIAL_KEY = "atala.pro.inTrial"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var configured = false
    private var observing = false

    private val _isInTrial = MutableStateFlow(false)
    private val _offers = MutableStateFlow<Map<PurchasePlan, PurchaseOffer>>(emptyMap())
    // Only a build without a RevenueCat key shows example prices; a store build never does.
    private val _isPlaceholder = MutableStateFlow(API_KEY.isBlank())
    /** What the store says about the entitlement (trial included). */
    private var entitled = false

    val isPro: StateFlow<Boolean> get() = ProAccess.isPro
    val isInTrial: StateFlow<Boolean> = _isInTrial.asStateFlow()
    val offers: StateFlow<Map<PurchasePlan, PurchaseOffer>> = _offers.asStateFlow()

    /** True while the store has no products: example prices, and the buttons stand in for a purchase. */
    val isPlaceholder: StateFlow<Boolean> = _isPlaceholder.asStateFlow()

    /** True once the yearly plan is loaded from the store and can be bought. */
    val canPurchase: Boolean get() = _offers.value[PurchasePlan.Yearly] != null

    /** How much cheaper [plan] is than paying month by month for a year, in percent (58 for 58 %). */
    fun savingPercent(plan: PurchasePlan, offers: Map<PurchasePlan, PurchaseOffer> = _offers.value): Int? {
        val yearly = offers[plan] ?: return null
        val monthly = offers[PurchasePlan.Monthly] ?: return null
        if (monthly.amountMicros <= 0) return null
        val fullYear = monthly.amountMicros * 12.0
        val paid = yearly.amountMicros.toDouble()
        if (paid >= fullYear) return null
        return ((1 - paid / fullYear) * 100).roundToInt()
    }

    /**
     * Starts the SDK once, then logs in with the account's identifier (null for a guest) and loads
     * the offers and the subscription state.
     */
    fun start(context: android.content.Context, userId: String?) {
        ProPreferences.init(context)
        if (!configured) {
            configured = true
            _isInTrial.value = ProPreferences.bool(TRIAL_KEY)
            publish()
            runCatching {
                if (!Purchases.isConfigured) {
                    val builder = PurchasesConfiguration.Builder(context.applicationContext, API_KEY)
                    if (!userId.isNullOrEmpty()) builder.appUserID(userId)
                    Purchases.configure(builder.build())
                }
                // A subscription can start, renew or end while the app is away: the SDK tells us.
                Purchases.sharedInstance.updatedCustomerInfoListener =
                    UpdatedCustomerInfoListener { info -> scope.launch { apply(info) } }
            }
        }
        scope.launch {
            val purchases = shared() ?: return@launch
            if (!userId.isNullOrEmpty() && purchases.appUserID != userId) {
                runCatching { purchases.awaitLogIn(userId) }
            }
            refresh()
        }
    }

    /**
     * Configures the SDK at launch and follows the account: signing in links the purchases to it,
     * signing out (or erasing the phone) forgets the subscription state. Called once by the application.
     */
    fun observe(context: Context, store: AppStore) {
        val realUser = { state: com.atala.app.core.store.AppState -> state.account?.takeIf { it.isReal }?.userId }
        start(context, realUser(store.current))
        if (observing) return
        observing = true
        scope.launch {
            var previous: Pair<String?, Boolean>? = null
            store.state
                .map { realUser(it) to (it.account != null || it.hasCompletedOnboarding) }
                .distinctUntilChanged()
                .collect { current ->
                    val before = previous
                    previous = current
                    if (before == null) return@collect
                    val (userId, hasData) = current
                    when {
                        userId != null -> start(context, userId)
                        before.first != null || (before.second && !hasData) -> signOut()
                    }
                }
        }
    }

    /**
     * Call when the account is signed out or erased: the next person on this phone must not inherit
     * the subscription state.
     */
    fun signOut() {
        setPlaceholderPro(false)
        if (!configured) return
        entitled = false
        _isInTrial.value = false
        ProPreferences.setBool(TRIAL_KEY, false)
        publish()
        scope.launch {
            val purchases = shared() ?: return@launch
            if (!purchases.isAnonymous) runCatching { purchases.awaitLogOut() }
            refresh()
        }
    }

    /** Placeholder only: pretends the subscription was bought (or not), on this phone. */
    fun setPlaceholderPro(active: Boolean) {
        ProPreferences.setBool(PLACEHOLDER_PRO_KEY, active)
        if (!active) ProWelcomeState.wasPlayed = false
        publish()
    }

    /** Reloads the plans and whether Atala Pro is active. */
    suspend fun refresh() {
        val purchases = shared() ?: return
        runCatching { purchases.awaitCustomerInfo() }.getOrNull()?.let { info ->
            apply(info)
            // Confirmed by the store: the next subscription gets its welcome again.
            if (!isPro.value) ProWelcomeState.wasPlayed = false
        }
        val offerings = try {
            purchases.awaitOfferings()
        } catch (error: PurchasesException) {
            // No product in the store (RevenueCat reports a configuration error) or no network:
            // the paywall says so and offers to try again, it never falls back to example prices.
            return
        } catch (error: Exception) {
            return
        }
        val loaded = mutableMapOf<PurchasePlan, PurchaseOffer>()
        offerings.current?.annual?.let { loaded[PurchasePlan.Yearly] = offer(it) }
        offerings.current?.monthly?.let { loaded[PurchasePlan.Monthly] = offer(it) }
        offerings.getOffering(GIFT_OFFERING_ID)?.annual?.let { loaded[PurchasePlan.Gift] = offer(it) }
        _offers.value = loaded
        publish()
    }

    /** Buys a plan (the free trial starts when the plan has one and the user is eligible). */
    suspend fun purchase(activity: android.app.Activity, plan: PurchasePlan): PurchaseOutcome {
        val unavailable = PurchaseOutcome.Failed(L("The subscription is not available right now. Please try again in a moment."))
        val purchases = shared() ?: return unavailable
        val offerings = runCatching { purchases.awaitOfferings() }.getOrNull() ?: return unavailable
        val pack = packageFor(plan, offerings) ?: return unavailable
        return try {
            val result = purchases.awaitPurchase(PurchaseParams.Builder(activity, pack).build())
            apply(result.customerInfo)
            if (isPro.value) {
                PurchaseOutcome.Purchased
            } else {
                PurchaseOutcome.Failed(L("The purchase went through but Atala Pro is not active yet. Tap Restore purchases in a moment."))
            }
        } catch (error: PurchasesTransactionException) {
            if (error.userCancelled || error.code == PurchasesErrorCode.PurchaseCancelledError) {
                PurchaseOutcome.Cancelled
            } else {
                PurchaseOutcome.Failed(error.message ?: L("Please try again."))
            }
        } catch (error: Exception) {
            PurchaseOutcome.Failed(error.message ?: L("Please try again."))
        }
    }

    /** Brings back a subscription bought earlier with the same Google account. True when Pro is active. */
    suspend fun restore(): Boolean {
        val purchases = shared() ?: return isPro.value
        runCatching { purchases.awaitRestore() }.getOrNull()?.let(::apply)
        return isPro.value
    }

    // Store state

    private fun shared(): Purchases? = if (configured && Purchases.isConfigured) Purchases.sharedInstance else null

    /** Reads the subscription state from what the store says. */
    private fun apply(info: CustomerInfo) {
        val entitlement = info.entitlements[ENTITLEMENT_ID]
        entitled = entitlement?.isActive == true
        _isInTrial.value = entitled && entitlement?.periodType == PeriodType.TRIAL
        // Kept on the phone: if the store cannot be reached at the next launch, someone in a free
        // trial is still known as such.
        ProPreferences.setBool(TRIAL_KEY, _isInTrial.value)
        publish()
    }

    /** Pro: the store's entitlement, or the stand-in purchase while the store has no products. */
    private fun publish() {
        val placeholderPro = _isPlaceholder.value && ProPreferences.bool(PLACEHOLDER_PRO_KEY)
        ProAccess.setPro(entitled || placeholderPro)
    }

    private fun packageFor(plan: PurchasePlan, offerings: Offerings): Package? = when (plan) {
        PurchasePlan.Yearly -> offerings.current?.annual
        PurchasePlan.Monthly -> offerings.current?.monthly
        PurchasePlan.Gift -> offerings.getOffering(GIFT_OFFERING_ID)?.annual
    }

    /**
     * The free trial is only announced to people who can still have it: Google Play leaves the trial
     * out of the default offer of someone who already had one.
     */
    private fun offer(pack: Package): PurchaseOffer {
        val product = pack.product
        val locale = Locale.getDefault()
        val trial = product.defaultOption?.freePhase?.billingPeriod?.let(::days)
        return PurchaseOffer(
            price = product.price.formatted,
            perMonth = runCatching { product.pricePerMonth(locale)?.formatted }.getOrNull(),
            perWeek = runCatching { product.pricePerWeek(locale)?.formatted }.getOrNull(),
            trialDays = trial?.takeIf { it > 0 },
            amountMicros = product.price.amountMicros,
        )
    }

    private fun days(period: Period): Int = when (period.unit) {
        Period.Unit.DAY -> period.value
        Period.Unit.WEEK -> period.value * 7
        Period.Unit.MONTH -> period.value * 30
        Period.Unit.YEAR -> period.value * 365
        else -> period.value
    }
}
