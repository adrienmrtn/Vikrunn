package com.atala.app.ui.paywall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.L
import com.atala.app.core.purchases.PlaceholderOffer
import com.atala.app.core.purchases.ProPreferences
import com.atala.app.core.purchases.PurchaseOutcome
import com.atala.app.core.purchases.PurchasePlan
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.findActivity
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.components.rememberReduceMotion
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil

/**
 * When the welcome gift is shown: to people who are not subscribed, shortly after the app opens.
 * From the first time it is shown the offer runs for 24 hours: a small chip on the home screen
 * counts the time down and opens the gift again. A property of this phone, never of the account.
 */
object GiftOfferSchedule {
    private const val COUNT_KEY = "atala.gift.shownCount"
    private const val LAST_KEY = "atala.gift.lastShown"
    private const val END_KEY = "atala.gift.offerEnd"
    const val MAX_SHOWS = 3
    private const val MIN_INTERVAL = 8 * 3600L
    /** How long the offer stays available once it was shown for the first time, in seconds. */
    const val WINDOW = 24 * 3600L
    /** Milliseconds after the main screen appears: the launch splash and the entrance are over by then. */
    const val DELAY_MS = 4000L

    private fun now(): Double = System.currentTimeMillis() / 1000.0

    /** When the offer ends, in seconds since 1970. Null while the gift was never shown. */
    val endDate: Long? get() = ProPreferences.seconds(END_KEY).takeIf { it > 0 }

    /** Seconds left before the offer ends. Zero once it is over, or while it has not started. */
    fun remaining(now: Double = now()): Double {
        val end = endDate ?: return 0.0
        return maxOf(0.0, end - now)
    }

    /** True from the first time the gift was shown until its 24 hours are over. */
    val isRunning: Boolean get() = remaining() > 0

    /**
     * The full-screen gift shows by itself before the offer has started, and again (once every
     * 8 hours at most) while it runs. Never once the countdown has reached zero.
     */
    fun isDue(now: Double = now()): Boolean {
        if (ProPreferences.int(COUNT_KEY) >= MAX_SHOWS) return false
        if (endDate != null && remaining(now) <= 0) return false
        val last = ProPreferences.seconds(LAST_KEY)
        return last == 0L || now - last >= MIN_INTERVAL
    }

    /** The gift was shown by itself: counts it, and starts the 24 hours the first time. */
    fun noteShown(now: Double = now()) {
        ProPreferences.setInt(COUNT_KEY, ProPreferences.int(COUNT_KEY) + 1)
        ProPreferences.setSeconds(LAST_KEY, now.toLong())
        if (endDate == null) ProPreferences.setSeconds(END_KEY, (now + WINDOW).toLong())
    }

    /** "23:59:41" */
    fun clock(seconds: Double): String {
        val total = maxOf(0, ceil(seconds).toInt())
        return String.format(Locale.ROOT, "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }
}

/**
 * Shared between the presenter (over the main app) and the chip of the home screen: whether the
 * gift is on screen, and when the offer ends.
 */
object GiftOfferCenter {
    private val _isPresented = MutableStateFlow(false)
    private val _endDate = MutableStateFlow<Long?>(null)
    private var loaded = false

    val isPresented: StateFlow<Boolean> = _isPresented.asStateFlow()

    /** Copy of [GiftOfferSchedule.endDate], so the chip appears as soon as the offer starts. */
    val endDate: StateFlow<Long?>
        get() {
            if (!loaded) {
                loaded = true
                _endDate.value = GiftOfferSchedule.endDate
            }
            return _endDate.asStateFlow()
        }

    /** The gift shows by itself: the first time starts the countdown. */
    fun presentWhenDue() {
        GiftOfferSchedule.noteShown()
        loaded = true
        _endDate.value = GiftOfferSchedule.endDate
        _isPresented.value = true
    }

    /** The chip was tapped: the same gift again, while the offer runs. */
    fun reopen() {
        if (!GiftOfferSchedule.isRunning) return
        _isPresented.value = true
    }

    fun dismiss() {
        _isPresented.value = false
    }
}

/** The seconds left on the offer, ticking every second. */
@Composable
internal fun rememberGiftRemaining(): State<Double> = produceState(GiftOfferSchedule.remaining()) {
    while (true) {
        value = GiftOfferSchedule.remaining()
        delay(1000L - System.currentTimeMillis() % 1000L)
    }
}

/** The chip on Today: the time left on the offer, a tap opens the gift again. */
@Composable
internal fun GiftChip(modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val isPro by PurchaseService.isPro.collectAsStateWithLifecycle()
    val endDate by GiftOfferCenter.endDate.collectAsStateWithLifecycle()
    val offers by PurchaseService.offers.collectAsStateWithLifecycle()
    val placeholder by PurchaseService.isPlaceholder.collectAsStateWithLifecycle()
    // Not for subscribers, and only when the gift can really be bought (always in placeholder mode).
    val available = !isPro && endDate != null && (placeholder || offers[PurchasePlan.Gift] != null)
    if (!available) return
    val left by rememberGiftRemaining()
    if (left <= 0) return
    val clock = GiftOfferSchedule.clock(left)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                haptics.tap()
                GiftOfferCenter.reopen()
            }
            .clearAndSetSemantics {
                contentDescription = L("Your gift. The offer ends in %@", clock)
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(30.dp)
                .background(Theme.yellowTint, CircleShape)
                .border(1.dp, Theme.yellow.copy(alpha = 0.6f), CircleShape)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CardGiftcard, null, tint = Theme.yellowDeep, modifier = Modifier.size(14.dp))
            Text(
                clock,
                style = Theme.font(12, FontWeight.Bold).copy(fontFeatureSettings = "tnum"),
                color = Theme.forest,
                maxLines = 1,
            )
        }
    }
}

/**
 * "We have a gift for you": the yearly plan at a lower price, paid from the first day. The box
 * drops in, shakes, opens in a burst, then the offer appears with the time left on it.
 */
@Composable
internal fun GiftOfferScreen(onClose: () -> Unit) {
    val state by collectAppState()
    val offers by PurchaseService.offers.collectAsStateWithLifecycle()
    val placeholder by PurchaseService.isPlaceholder.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    /** 0 nothing, 1 the box is there, 2 it shakes, 3 it has opened, 4 the offer and the buttons. */
    var stage by remember { mutableIntStateOf(0) }
    var isBuying by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }

    // Store values when the gift is loaded, the placeholder ones otherwise.
    val gift = offers[PurchasePlan.Gift]
    val weeklyPrice = gift?.perWeek ?: PlaceholderOffer.GIFT_WEEKLY_PRICE
    val yearlyPrice = gift?.price ?: PlaceholderOffer.GIFT_YEARLY_PRICE
    val fullPrice = offers[PurchasePlan.Yearly]?.price ?: PlaceholderOffer.YEARLY_PRICE
    // Rounded down to a multiple of five, so the badge never promises more than the real discount.
    val discountPercent: Int = run {
        val full = offers[PurchasePlan.Yearly]
        if (gift == null || full == null || full.amountMicros <= 0) return@run 50
        val ratio = gift.amountMicros.toDouble() / full.amountMicros
        maxOf(0, ((1 - ratio) * 100).toInt() / 5 * 5)
    }

    LaunchedEffect(Unit) {
        if (stage != 0) return@LaunchedEffect
        if (reduceMotion) {
            stage = 4
            return@LaunchedEffect
        }
        stage = 1
        delay(700)
        haptics.play(HapticKind.Soft)
        stage = 2
        shake.animateTo(9f, repeatable(5, tween(110), RepeatMode.Reverse))
        shake.snapTo(0f)
        haptics.play(HapticKind.Success)
        stage = 3
        delay(450)
        stage = 4
    }

    fun close() {
        haptics.tap()
        onClose()
    }

    fun claim() {
        haptics.confirm()
        if (placeholder) {
            // No product in the store yet: the button stands in for the purchase.
            PurchaseService.setPlaceholderPro(true)
            onClose()
            return
        }
        if (isBuying) return
        val activity = context.findActivity() ?: return
        isBuying = true
        scope.launch {
            val outcome = PurchaseService.purchase(activity, PurchasePlan.Gift)
            isBuying = false
            when (outcome) {
                PurchaseOutcome.Purchased -> {
                    haptics.play(HapticKind.Success)
                    onClose()
                }
                PurchaseOutcome.Cancelled -> Unit
                is PurchaseOutcome.Failed -> errorText = outcome.message
            }
        }
    }

    val revealed = stage >= 4
    val offerAlpha by animateFloatAsState(if (revealed) 1f else 0f, tween(450), label = "offer")
    val offerShift by animateFloatAsState(if (revealed) 0f else 18f, tween(450), label = "offerShift")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Theme.green, Theme.forest))),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(44.dp)
                        .alpha(offerAlpha)
                        .clickable(enabled = revealed, role = Role.Button, onClick = ::close)
                        .semantics { contentDescription = L("Close") },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(32.dp).background(Color.White.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
            GiftBox(stage = stage, rotation = shake.value)
            Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        alpha = offerAlpha
                        translationY = offerShift.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    L("We have a gift for you, %@", state.firstName),
                    style = Theme.font(28, FontWeight.Bold).copy(lineHeight = 34.sp, textAlign = TextAlign.Center),
                    color = Color.White,
                    modifier = Modifier.semantics { heading() },
                )
                if (discountPercent > 0) {
                    Text(
                        L("%@% OFF", discountPercent),
                        style = Theme.font(13, FontWeight.Bold).copy(letterSpacing = 1.sp),
                        color = Theme.forest,
                        modifier = Modifier.background(Theme.yellow, CircleShape).padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(L("All of Atala Pro for only"), style = Theme.font(17), color = Theme.greenOnDark, textAlign = TextAlign.Center)
                    // The amount charged is the largest figure; the weekly equivalent comes under it.
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 46.sp, fontWeight = FontWeight.Bold)) { append(yearlyPrice) }
                            withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)) { append(L(" /year")) }
                        },
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        buildAnnotatedString {
                            append(L("Billed %@ a year, instead of ", yearlyPrice))
                            appendStruck(fullPrice)
                            append(".")
                        },
                        style = Theme.font(17, FontWeight.SemiBold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(L("%@ a week", weeklyPrice), style = Theme.font(15), color = Theme.greenOnDark, textAlign = TextAlign.Center)
                }
                GiftCountdown()
            }
            Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Theme.screenPadding, end = Theme.screenPadding, bottom = 10.dp)
                    .alpha(offerAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(CircleShape)
                        .background(Color.White, CircleShape)
                        .clickable(enabled = revealed && !isBuying, role = Role.Button, onClick = ::claim),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isBuying) L("One moment…") else L("Claim my gift"), style = Theme.font(17, FontWeight.Bold), color = Theme.forest)
                }
                Box(
                    Modifier
                        .height(44.dp)
                        .clickable(enabled = revealed && !isBuying, role = Role.Button, onClick = ::close)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(L("No thanks"), style = Theme.font(15, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.85f))
                }
                Text(
                    if (gift != null) L("Yearly subscription, no free trial. Renews automatically, cancel any time.")
                    else L("Price shown as an example. Nothing is charged in this version."),
                    style = Theme.font(12),
                    color = Theme.greenOnDark,
                    textAlign = TextAlign.Center,
                )
                LinkedText(legalLinks, Theme.font(12), Theme.greenOnDark, Color.White)
            }
        }
    }

    errorText?.let { message ->
        AlertDialog(
            onDismissRequest = { errorText = null },
            title = { Text(L("We couldn't complete that")) },
            text = { Text(message.ifEmpty { L("Please try again.") }) },
            confirmButton = { TextButton(onClick = { errorText = null }) { Text(L("OK"), color = Theme.ink) } },
            containerColor = Color.White,
        )
    }
}

/** The time left on the offer, counted down every second. Left out once it is over. */
@Composable
private fun GiftCountdown() {
    val left by rememberGiftRemaining()
    if (left <= 0) return
    Row(
        Modifier
            .height(32.dp)
            .background(Color.White.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Schedule, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(
            L("Offer ends in %@", GiftOfferSchedule.clock(left)),
            style = Theme.font(14, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
            color = Color.White,
        )
    }
}

/** The box: drops in, shakes, then opens with a burst of rays behind it. */
@Composable
private fun GiftBox(stage: Int, rotation: Float) {
    val opened = stage >= 3
    val bouncy = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
    val boxScale by animateFloatAsState(
        when {
            stage == 0 -> 0.2f
            opened -> 1.12f
            else -> 1f
        },
        bouncy, label = "boxScale",
    )
    val boxAlpha by animateFloatAsState(if (stage == 0) 0f else 1f, tween(250), label = "boxAlpha")
    val burst by animateFloatAsState(if (opened) 1f else 0f, bouncy, label = "burst")
    val haloScale by animateFloatAsState(if (opened) 1f else 0.7f, bouncy, label = "halo")
    Box(Modifier.fillMaxWidth().height(260.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Rays(progress = burst, from = 70.dp, to = 118.dp, short = 4.dp, long = 26.dp)
        Box(Modifier.size(190.dp).scale(haloScale).background(Color.White.copy(alpha = 0.12f), CircleShape))
        Icon(
            Icons.Filled.CardGiftcard,
            contentDescription = null,
            tint = Theme.yellow,
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer {
                    scaleX = boxScale
                    scaleY = boxScale
                    alpha = boxAlpha
                    rotationZ = rotation
                },
        )
    }
}

/** Twelve yellow rays around a centre, growing outwards as [progress] goes from 0 to 1. */
@Composable
internal fun Rays(progress: Float, from: Dp, to: Dp, short: Dp, long: Dp) {
    Canvas(Modifier.size((to + long) * 2)) {
        if (progress <= 0f) return@Canvas
        val width = 5.dp.toPx()
        val length = (short + (long - short) * progress).toPx()
        val distance = (from + (to - from) * progress).toPx()
        val color = Theme.yellow.copy(alpha = 0.85f * progress.coerceIn(0f, 1f))
        for (index in 0 until 12) {
            rotate(index * 30f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center.x - width / 2, center.y - distance - length / 2),
                    size = Size(width, length),
                    cornerRadius = CornerRadius(width / 2, width / 2),
                )
            }
        }
    }
}
