package com.atala.app.ui.paywall

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.L
import com.atala.app.core.purchases.ProWelcomeState
import com.atala.app.core.purchases.PurchasePlan
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.core.review.ReviewPromptState
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay

/*
 * Atala Pro pieces shown by other screens (ProViews.swift, ProLock.swift, GiftOfferView.swift).
 */

/** Small gold "PRO" mark shown next to the name of a subscriber. Shows nothing for a free user. */
@Composable
fun ProChip(modifier: Modifier = Modifier) {
    val isPro by PurchaseService.isPro.collectAsStateWithLifecycle()
    if (!isPro) return
    Row(
        modifier
            .background(Theme.yellow, CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .clearAndSetSemantics { contentDescription = L("Atala Pro member") },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CrownIcon, null, tint = Theme.forest, modifier = Modifier.size(12.dp))
        Text("PRO", style = Theme.font(11, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = Theme.forest)
    }
}

/**
 * For people without Atala Pro: a compact card with what Pro adds and one button to the paywall.
 * Hidden for subscribers. It sits below the session of the day, never above it.
 */
@Composable
fun ProUpsellCard(onOpenPaywall: () -> Unit, modifier: Modifier = Modifier) {
    val isPro by PurchaseService.isPro.collectAsStateWithLifecycle()
    val state by collectAppState()
    val haptics = rememberHaptics()
    if (isPro || state.plan == null) return
    val features = listOf(
        Icons.Filled.Forum to L("Talk to your coach any time"),
        Icons.Filled.GraphicEq to L("%@'s voice in your ears", state.coach.name),
        Icons.AutoMirrored.Filled.ShowChart to L("Your progress and predictions"),
        Icons.AutoMirrored.Filled.ListAlt to L("Every detail of your runs"),
    )
    val shape = RoundedCornerShape(Theme.cardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Theme.green, Theme.forest)), shape)
            .clickable(role = Role.Button, onClickLabel = L("Shows what Atala Pro includes")) {
                haptics.tap()
                onOpenPaywall()
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Theme.yellow, CircleShape), contentAlignment = Alignment.Center) {
                Icon(CrownIcon, null, tint = Theme.forest, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Atala Pro", style = Theme.font(17, FontWeight.Bold), color = Color.White)
                Text(L("Go further with your coach"), style = Theme.font(13), color = Theme.greenOnDark, maxLines = 2)
            }
            Box(
                Modifier.height(36.dp).background(Color.White, CircleShape).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(L("Discover"), style = Theme.font(14, FontWeight.Bold), color = Theme.forest, maxLines = 1)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            features.forEach { (icon, title) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Theme.yellow, modifier = Modifier.size(14.dp))
                    }
                    Text(title, style = Theme.font(14, FontWeight.Medium), color = Color.White, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Round gift chip with a live countdown at the top of Today; reopens the gift offer. */
@Composable
fun GiftOfferChip(modifier: Modifier = Modifier) {
    GiftChip(modifier)
}

/**
 * A screen that needs Atala Pro: [content] blurred under a padlock card with a button to the paywall,
 * or shown as it is for a subscriber.
 */
@Composable
fun ProLocked(
    title: String,
    message: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ProLocked(icon = Icons.Filled.Lock, title = title, message = message, onUnlock = onUnlock, modifier = modifier, content = content)
}

/** [ProLocked] with the symbol of the feature on the "Atala Pro" label of the card. */
@Composable
fun ProLocked(
    icon: ImageVector,
    title: String,
    message: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    buttonTitle: String = L("Unlock with Atala Pro"),
    content: @Composable () -> Unit,
) {
    val isPro by PurchaseService.isPro.collectAsStateWithLifecycle()
    if (isPro) {
        Box(modifier) { content() }
        return
    }
    // Blur exists from Android 12; before it, an almost opaque veil keeps the content hidden.
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier) {
        Box(
            Modifier
                .then(if (canBlur) Modifier.blur(9.dp) else Modifier)
                .clearAndSetSemantics {},
        ) { content() }
        Box(
            Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = if (canBlur) 0.35f else 0.94f))
                // Nothing under the veil can be touched.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            ProLockCard(icon, title, message, buttonTitle, onUnlock)
        }
    }
}

@Composable
private fun ProLockCard(icon: ImageVector, title: String, message: String, buttonTitle: String, onUnlock: () -> Unit) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 340.dp)
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .background(Color.White, shape)
            .border(1.dp, Theme.cardBorder, shape)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp).background(Theme.forest, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Row(
            Modifier.height(28.dp).background(Theme.greenTint, CircleShape).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Theme.green, modifier = Modifier.size(14.dp))
            Text(L("Atala Pro"), style = Theme.font(13, FontWeight.Bold), color = Theme.green)
        }
        Text(title, style = Theme.font(22, FontWeight.Bold), color = Theme.ink, textAlign = TextAlign.Center)
        Text(message, style = Theme.font(15).copy(lineHeight = 21.sp), color = Theme.body, textAlign = TextAlign.Center)
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(Theme.buttonHeight)
                .clip(CircleShape)
                .background(Theme.forest, CircleShape)
                .clickable(role = Role.Button) {
                    haptics.tap()
                    onUnlock()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(buttonTitle, style = Theme.font(17, FontWeight.Bold), color = Color.White)
        }
    }
}

/** How many paywalls are on screen: the gift and the welcome never show over one. */
internal object PaywallPresence {
    var count by mutableIntStateOf(0)
}

/** Full-screen gift offer and Pro welcome, presented over the main app when due. */
@Composable
fun MainAppOverlays() {
    MainAppOverlays(canPresent = true)
}

/**
 * Full-screen gift offer and Pro welcome, presented over the main app when due, never over each
 * other, a paywall, or whatever [canPresent] says is on screen (a run, a sheet, a pushed screen).
 */
@Composable
fun MainAppOverlays(canPresent: Boolean) {
    val isPro by PurchaseService.isPro.collectAsStateWithLifecycle()
    val giftShown by GiftOfferCenter.isPresented.collectAsStateWithLifecycle()
    var welcomeShown by remember { mutableStateOf(false) }
    // When the main screen appeared: nothing shows over the launch splash.
    val mountedAt = remember { SystemClock.elapsedRealtime() }
    val free by rememberUpdatedState(canPresent && PaywallPresence.count == 0)

    LaunchedEffect(Unit) {
        delay(GiftOfferSchedule.DELAY_MS)
        // The Google Play rating request comes first: on the launch where it is made, the gift
        // waits a few more seconds behind it.
        ReviewPromptState.waitBeforeOffer()
        // The subscription state may still be loading at launch: ask once more first.
        PurchaseService.refresh()
        if (PurchaseService.isPro.value || !GiftOfferSchedule.isDue()) return@LaunchedEffect
        // With the store connected, only offer the gift when the store really has it.
        if (PurchaseService.canPurchase && PurchaseService.offers.value[PurchasePlan.Gift] == null) return@LaunchedEffect
        // Never on top of something else: it waits for the next launch instead.
        if (!free || welcomeShown) return@LaunchedEffect
        GiftOfferCenter.presentWhenDue()
    }

    LaunchedEffect(isPro, canPresent) {
        // The flag is reset by the purchase service once the store confirms there is no subscription.
        if (!isPro || ProWelcomeState.wasPlayed) return@LaunchedEffect
        val early = GiftOfferSchedule.DELAY_MS - (SystemClock.elapsedRealtime() - mountedAt)
        if (early > 0) delay(early)
        // Never over the Google Play rating request, made a moment after arriving.
        ReviewPromptState.waitBeforeOffer()
        // The paywall or the gift is still closing: wait until nothing is on screen.
        var ready = false
        for (attempt in 0 until 8) {
            delay(800)
            if (free && !GiftOfferCenter.isPresented.value) {
                ready = true
                break
            }
        }
        if (!ready) return@LaunchedEffect
        ProWelcomeState.wasPlayed = true
        welcomeShown = true
    }

    if (giftShown) {
        FullScreenCover(onDismiss = GiftOfferCenter::dismiss) {
            GiftOfferScreen(onClose = GiftOfferCenter::dismiss)
        }
    }
    if (welcomeShown && !giftShown) {
        FullScreenCover(onDismiss = { welcomeShown = false }) {
            ProWelcomeScreen(onClose = { welcomeShown = false })
        }
    }
}

/** A dark green screen over everything, edge to edge (`fullScreenCover`). Back closes it. */
@Composable
private fun FullScreenCover(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        Box(Modifier.semantics {}) { content() }
    }
}
