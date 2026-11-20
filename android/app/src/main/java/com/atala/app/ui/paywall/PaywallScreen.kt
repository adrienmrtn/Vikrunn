package com.atala.app.ui.paywall

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Gender
import com.atala.app.core.model.StreakEngine
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.plan.PlanEngine
import com.atala.app.core.purchases.PlaceholderOffer
import com.atala.app.core.purchases.PurchaseOutcome
import com.atala.app.core.purchases.PurchasePlan
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.SelectionMark
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Atala Pro paywall (`PaywallView` in Atala/Features/Onboarding/OnboardingPaywallScreen.swift).
 * Shared by the main app (opened with `nav.onOpenPaywall`) and the onboarding's paywall step.
 *
 * @param closeDelay seconds before the close button appears (7 in the onboarding, 0 elsewhere).
 * @param onFinished closed, subscribed or not.
 */
@Composable
fun PaywallScreen(closeDelay: Double = 0.0, onFinished: () -> Unit) {
    val state by collectAppState()
    val offers by PurchaseService.offers.collectAsStateWithLifecycle()
    val placeholder by PurchaseService.isPlaceholder.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    var closeShown by remember { mutableStateOf(false) }
    var isBuying by remember { mutableStateOf(false) }
    var purchaseError by remember { mutableStateOf<String?>(null) }
    var plan by rememberSaveable { mutableStateOf(PurchasePlan.Yearly) }

    // Store values when the offers are loaded, the placeholder ones otherwise.
    val canPurchase = offers[PurchasePlan.Yearly] != null
    val trialDays: Int? = if (canPurchase) offers[PurchasePlan.Yearly]?.trialDays else PlaceholderOffer.TRIAL_DAYS
    val yearlyPerMonth = offers[PurchasePlan.Yearly]?.perMonth ?: PlaceholderOffer.MONTHLY_PRICE
    val yearlyPrice = offers[PurchasePlan.Yearly]?.price ?: PlaceholderOffer.YEARLY_PRICE
    val monthPrice = offers[PurchasePlan.Monthly]?.price ?: PlaceholderOffer.MONTH_PRICE
    val yearlySaving: Int? = if (canPurchase) PurchaseService.savingPercent(PurchasePlan.Yearly, offers) else PlaceholderOffer.YEARLY_SAVING
    // The monthly plan is offered when the store has it, and always in the placeholder.
    val showsMonthly = !canPurchase || offers[PurchasePlan.Monthly] != null
    val startsWithTrial = plan == PurchasePlan.Yearly && trialDays != null

    LaunchedEffect(Unit) { PurchaseService.refresh() }
    DisposableEffect(Unit) {
        PaywallPresence.count += 1
        onDispose { PaywallPresence.count -= 1 }
    }
    LaunchedEffect(Unit) {
        // The delay is a rule of the screen, so it is kept with reduced motion; only the fade is dropped.
        delay((closeDelay * 1000).toLong())
        closeShown = true
    }
    LaunchedEffect(showsMonthly) {
        // The monthly card can go away once the real offers load: fall back to the yearly plan.
        if (!showsMonthly) plan = PurchasePlan.Yearly
    }
    // Back closes the screen like the cross, and only once the cross is there.
    BackHandler { if (closeShown) onFinished() }

    fun startTrial() {
        haptics.confirm()
        if (placeholder) {
            // No product in the store yet: the button stands in for the purchase.
            PurchaseService.setPlaceholderPro(true)
            onFinished()
            return
        }
        if (isBuying) return
        val activity = context.findActivity() ?: return
        isBuying = true
        scope.launch {
            val outcome = PurchaseService.purchase(activity, plan)
            isBuying = false
            when (outcome) {
                PurchaseOutcome.Purchased -> {
                    haptics.play(HapticKind.Success)
                    onFinished()
                }
                PurchaseOutcome.Cancelled -> Unit
                is PurchaseOutcome.Failed -> purchaseError = outcome.message
            }
        }
    }

    fun restorePurchases() {
        if (isBuying) return
        isBuying = true
        scope.launch {
            val restored = PurchaseService.restore()
            isBuying = false
            if (restored) {
                onFinished()
            } else {
                purchaseError = L("No active Atala Pro subscription was found for this Google account.")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                PhotoStrip(photoNames(state.answers.gender), Modifier.padding(top = 52.dp))
                Headline(state.firstName, PaywallCopy.goalLine(state.answers, state.plan))
                FeatureList(features(state), Modifier.padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 20.dp))
                Column(
                    Modifier.padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // The amount charged comes first and largest; the monthly equivalent sits under it.
                    PlanCard(
                        selected = plan == PurchasePlan.Yearly,
                        title = L("Yearly"),
                        price = L("%@ billed yearly", yearlyPrice),
                        detail = L("%@ a month", yearlyPerMonth),
                        badge = trialDays?.let { L("%@ DAYS FREE", it) },
                        saving = yearlySaving?.let { L("Save %@%", it) },
                        onClick = { plan = PurchasePlan.Yearly },
                    )
                    if (showsMonthly) {
                        PlanCard(
                            selected = plan == PurchasePlan.Monthly,
                            title = L("Monthly"),
                            price = L("%@ a month", monthPrice),
                            detail = L("Billed every month, no free trial"),
                            badge = null,
                            saving = null,
                            onClick = { plan = PurchasePlan.Monthly },
                        )
                    }
                    // What is charged after a trial, and how to cancel, next to the price.
                    Text(
                        if (startsWithTrial) {
                            L("Free for %@ days, then %@ a year. Renews automatically. Cancel any time in your Google Play settings.", trialDays!!, yearlyPrice)
                        } else {
                            L("Renews automatically. Cancel any time in your Google Play settings.")
                        },
                        style = Theme.font(13).copy(lineHeight = 18.sp),
                        color = Theme.body,
                    )
                    if (!canPurchase) {
                        Text(
                            L("Prices shown as an example. Nothing is charged in this version."),
                            style = Theme.font(12),
                            color = Theme.muted,
                        )
                    }
                }
            }
            TopBar(closeShown = closeShown, reduceMotion = reduceMotion, onClose = {
                haptics.tap()
                onFinished()
            })
        }
        CallToAction(
            title = when {
                isBuying -> L("One moment…")
                startsWithTrial -> L("Start my free trial")
                else -> L("Subscribe")
            },
            isBuying = isBuying,
            onBuy = ::startTrial,
            onRestore = ::restorePurchases,
        )
    }

    purchaseError?.let { message ->
        AlertDialog(
            onDismissRequest = { purchaseError = null },
            title = { Text(L("We couldn't complete that")) },
            text = { Text(message.ifEmpty { L("Please try again.") }) },
            confirmButton = { TextButton(onClick = { purchaseError = null }) { Text(L("OK"), color = Theme.ink) } },
            containerColor = Color.White,
        )
    }
}

private class PaywallFeature(val icon: ImageVector, val title: String, val detail: String)

private fun features(state: com.atala.app.core.store.AppState): List<PaywallFeature> {
    val weeks = state.plan?.weeks?.size ?: PlanEngine.MAX_WEEKS
    // Someone who does not run yet has no level or pace to speak of: plain words instead.
    val starting = state.answers.weeklyVolume == WeeklyVolume.NotYet
    val weeksText = StreakEngine.weeks(weeks)
    return listOf(
        PaywallFeature(
            Icons.Filled.CalendarMonth, L("Your personal plan"),
            if (starting) L("%@ built around your days, starting gently", weeksText)
            else L("%@ built around your days and your level", weeksText),
        ),
        PaywallFeature(Icons.Filled.Forum, L("%@, your coach, 24/7", state.coach.name), L("Ask anything, move a session, lighten a week")),
        PaywallFeature(
            Icons.Filled.GraphicEq, L("Voice guidance on every run"),
            if (starting) L("Your coach in your ears, from the first step") else L("Your pace and your intervals, in your ears"),
        ),
        PaywallFeature(Icons.Filled.SelfImprovement, L("Warm-ups, strength and stretching"), L("Get every warm-up and stretch you need")),
        PaywallFeature(Icons.AutoMirrored.Filled.ShowChart, L("Progress you can see"), L("Splits, elevation, personal bests and streaks")),
    )
}

/** Photos for the moving strip, chosen from the gender given in onboarding (`AtalaPhotos.strip`). */
private fun photoNames(gender: Gender?): List<String> = when (gender) {
    Gender.Man -> listOf("hero_today_man", "photo_trio", "photo_community")
    Gender.Woman -> listOf("hero_today_woman", "photo_trio")
    else -> listOf("photo_trio", "hero_today_woman", "hero_today_man", "photo_community")
}

/** A strip of running photos gliding from right to left, endlessly. Still with reduced motion. */
@Composable
internal fun PhotoStrip(
    names: List<String>,
    modifier: Modifier = Modifier,
    tileWidth: Dp = 132.dp,
    height: Dp = 176.dp,
    /** dp per second. */
    speed: Float = 22f,
) {
    val reduceMotion = rememberReduceMotion()
    // Enough tiles to cover any phone twice over, so the loop never shows a gap.
    val tiles = remember(names) {
        buildList { if (names.isNotEmpty()) while (size < 8) addAll(names) }
    }
    val spacing = 10.dp
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { elapsed = (it - start) / 1000f }
    }
    // The strip moves the same way in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxWidth().height(height).clipToBounds()) {
            Row(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .graphicsLayer {
                        val loop = tiles.size * (tileWidth + spacing).toPx()
                        val shift = if (loop > 0) (elapsed * speed.dp.toPx()) % loop else 0f
                        translationX = -shift
                    },
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                // Twice the list: when the first copy has left, the second one is exactly in its place.
                (tiles + tiles).forEach { name ->
                    Box(
                        Modifier
                            .size(tileWidth, height)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF1B1B1B)),
                    ) {
                        AtalaImages[name]?.let { id ->
                            Image(painterResource(id), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Headline(firstName: String, goalLine: String) {
    Column(
        Modifier.padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            buildAnnotatedString {
                append(L("%@, finally get the ", firstName))
                withStyle(SpanStyle(color = Theme.green)) { append(L("guidance")) }
                append(L(" you've been looking for."))
            },
            style = Theme.font(28, FontWeight.Bold).copy(lineHeight = 34.sp),
            color = Theme.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(goalLine, style = Theme.font(16, FontWeight.SemiBold), color = Theme.green)
    }
}

@Composable
private fun FeatureList(features: List<PaywallFeature>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        features.forEach { feature ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(feature.icon, size = 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(feature.title, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
                    Text(feature.detail, style = Theme.font(13), color = Theme.muted)
                }
            }
        }
    }
}

/** One plan to choose: its name and badges, the amount charged in large, the detail under it. */
@Composable
private fun PlanCard(
    selected: Boolean,
    title: String,
    price: String,
    detail: String,
    badge: String?,
    saving: String?,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Theme.greenTint else Color.White, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Theme.green else Theme.controlBorder, shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton) {
                haptics.select()
                onClick()
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = Theme.font(15, FontWeight.Bold), color = Theme.ink)
                if (badge != null) {
                    Text(
                        badge,
                        style = Theme.font(11, FontWeight.Bold).copy(letterSpacing = 0.6.sp),
                        color = Color.White,
                        modifier = Modifier.background(Theme.green, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (saving != null) {
                    Text(
                        saving,
                        style = Theme.font(11, FontWeight.Bold),
                        color = Theme.orangeDeep,
                        modifier = Modifier.background(Theme.orangeTint, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(price, style = Theme.font(20, FontWeight.Bold), color = Theme.ink)
            Text(detail, style = Theme.font(13), color = Theme.muted)
        }
        Spacer(Modifier.width(8.dp))
        SelectionMark(selected)
    }
}

/** Logo at the start, close cross at the end (hidden for the first seconds). */
@Composable
private fun TopBar(closeShown: Boolean, reduceMotion: Boolean, onClose: () -> Unit) {
    val alpha by animateFloatAsState(
        if (closeShown) 1f else 0f,
        if (reduceMotion) tween(0) else tween(450),
        label = "close",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtalaImages["logo_atala"]?.let { id ->
            Image(
                painterResource(id), contentDescription = "Atala",
                modifier = Modifier.padding(start = 12.dp).height(20.dp).width(85.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(44.dp)
                .alpha(alpha)
                .clickable(
                    enabled = closeShown,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(32.dp).background(Theme.surface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = if (closeShown) L("Close") else null, tint = Theme.muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CallToAction(title: String, isBuying: Boolean, onBuy: () -> Unit, onRestore: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(thickness = 1.dp, color = Theme.hairline)
        Column(
            Modifier.padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(CircleShape)
                    .background(Theme.green, CircleShape)
                    .clickable(enabled = !isBuying, role = Role.Button, onClick = onBuy),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = Theme.font(17, FontWeight.Bold), color = Color.White)
                Text(L("Easy to cancel, at any time"), style = Theme.font(13), color = Theme.greenOnDark)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = Theme.muted, modifier = Modifier.size(12.dp))
                Text(L("Secured by Google Play"), style = Theme.font(12), color = Theme.muted)
                Text("·", style = Theme.font(12), color = Theme.muted)
                Text(
                    L("Restore purchases"),
                    style = Theme.font(12, FontWeight.SemiBold),
                    color = Theme.muted,
                    modifier = Modifier
                        .widthIn(min = 44.dp)
                        .clickable(enabled = !isBuying, role = Role.Button, onClick = onRestore)
                        .padding(vertical = 6.dp),
                )
            }
            // Both links next to the price of a subscription.
            LinkedText(legalLinks, Theme.font(12), Theme.muted, Theme.ink)
        }
    }
}

/**
 * The paywall's personal reminder (`OnboardingPaywallCopy.goalLine`): the goal in the user's own
 * terms. Nothing here promises a result.
 */
internal object PaywallCopy {
    /** "1:45" for a round time over an hour, "1:45:30" otherwise, "27:30" under the hour. */
    fun targetTime(seconds: Int): String {
        val total = maxOf(0, seconds)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        val format = { pattern: String, args: Array<Int> -> String.format(java.util.Locale.ROOT, pattern, *args) }
        if (hours > 0) {
            return if (secs == 0) format("%d:%02d", arrayOf(hours, minutes)) else format("%d:%02d:%02d", arrayOf(hours, minutes, secs))
        }
        return format("%d:%02d", arrayOf(minutes, secs))
    }

    private fun goalNoun(goal: com.atala.app.core.model.GoalType): String = when (goal) {
        com.atala.app.core.model.GoalType.FiveK -> "5K"
        com.atala.app.core.model.GoalType.TenK -> "10K"
        com.atala.app.core.model.GoalType.Half -> L("half marathon")
        com.atala.app.core.model.GoalType.Regular -> L("running routine")
        com.atala.app.core.model.GoalType.Shape -> L("fitness routine")
    }

    fun goalLine(answers: com.atala.app.core.model.OnboardingAnswers, plan: com.atala.app.core.model.TrainingPlan?): String {
        val goal = answers.goal ?: plan?.goal ?: com.atala.app.core.model.GoalType.Regular
        if (goal.isOpenEnded) {
            return when (answers.runDays.size) {
                0 -> L("Running every week, for good.")
                1 -> L("Running once a week, for good.")
                2 -> L("Running twice a week, for good.")
                else -> L("Running %@ times a week, for good.", answers.runDays.size)
            }
        }
        val noun = goalNoun(goal)
        val raceDate = plan?.raceDate ?: answers.raceDate?.takeIf { answers.hasRace }
        val day = raceDate?.let { com.atala.app.core.l10n.Fmt.day(it, "d MMMM") }
        val target = answers.targetSeconds ?: plan?.targetSeconds
        if (target != null) {
            val time = targetTime(target)
            return if (day != null) L("Your %@ in %@, on %@.", noun, time, day) else L("Your %@ in %@.", noun, time)
        }
        return if (day != null) L("Your %@, on %@.", noun, day) else L("Your %@, one session at a time.", noun)
    }
}
