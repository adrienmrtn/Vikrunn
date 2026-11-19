package com.atala.app.core.purchases

import com.atala.app.core.model.ProAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The three ways to subscribe, as on the iPhone. */
enum class PurchasePlan { Yearly, Monthly, Gift }

/** A plan as the store sells it in the user's country. */
data class PurchaseOffer(
    val price: String,
    val perMonth: String?,
    val perWeek: String?,
    val trialDays: Int?,
)

sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

/**
 * Atala Pro through RevenueCat (entitlement `atala_pro`, offerings `default` and `gift`).
 * Shared entry points: the paywall, the gift offer and the Pro locks use only these.
 * The monetisation owner fills them in; until then everything reads as "not Pro, no offer".
 */
object PurchaseService {
    private val _isInTrial = MutableStateFlow(false)
    private val _offers = MutableStateFlow<Map<PurchasePlan, PurchaseOffer>>(emptyMap())

    val isPro: StateFlow<Boolean> get() = ProAccess.isPro
    val isInTrial: StateFlow<Boolean> = _isInTrial.asStateFlow()
    val offers: StateFlow<Map<PurchasePlan, PurchaseOffer>> = _offers.asStateFlow()

    /** True when the store can sell (offers loaded). */
    val canPurchase: Boolean get() = _offers.value.isNotEmpty()

    fun start(context: android.content.Context, userId: String?) {}
    suspend fun refresh() {}
    suspend fun purchase(activity: android.app.Activity, plan: PurchasePlan): PurchaseOutcome =
        PurchaseOutcome.Failed("Not available yet")
    suspend fun restore(): Boolean = false
}
