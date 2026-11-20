package com.atala.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme

/** The flow's navigator, for every onboarding screen: `LocalOnboardingNavigator.current.next()`. */
val LocalOnboardingNavigator = staticCompositionLocalOf<OnboardingNavigator> {
    error("No OnboardingNavigator: onboarding screens live inside OnboardingFlow")
}

/** The step a screen belongs to (stays the same while the screen animates out). */
val LocalOnboardingStep = compositionLocalOf { OnboardingStep.Welcome }

/** The whole entry flow, from the welcome carousel to the first day in the app (`OnboardingFlowView`). */
@Composable
fun OnboardingFlow() {
    val store = LocalAppStore.current
    // Built again with the tree on a language change: it restores itself on the saved step.
    val nav = remember(store) { OnboardingNavigator(store).apply { restore() } }
    // Answers move the skip rules, hence the bar: read the state so the header follows each change.
    val state by collectAppState()
    val progress = remember(nav.step, state.answers, state.account) { nav.barProgress() }
    val haptics = rememberHaptics()
    val shift = with(LocalDensity.current) { IntOffset(28.dp.roundToPx(), 6.dp.roundToPx()) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // System back follows the flow; on the welcome screen it leaves the app.
    BackHandler(enabled = nav.step != OnboardingStep.Welcome) { nav.back() }

    CompositionLocalProvider(LocalOnboardingNavigator provides nav) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            // Only the screen takes part in the transition.
            AnimatedContent(
                targetState = nav.step,
                transitionSpec = { stepTransition(targetState, if (rtl) IntOffset(-shift.x, shift.y) else shift) },
                label = "onboarding step",
            ) { step ->
                CompositionLocalProvider(LocalOnboardingStep provides step) {
                    OnboardingStepHost(step)
                }
            }
            // Fixed header, above every screen: never part of a step change.
            OnboardingFlowHeader(
                progress = progress,
                showsBar = nav.step.showsProgress,
                showsBack = nav.step.showsHeaderBack,
                onBack = {
                    haptics.tap()
                    nav.back()
                },
                modifier = Modifier.statusBarsPadding(),
            )
        }
    }
}

/**
 * Quick fade with a short slide and settle, identical forward and back. Explainer screens appear
 * at once (they draw their own circular reveal) while the screen underneath only dims.
 */
private fun stepTransition(target: OnboardingStep, shift: IntOffset): ContentTransform {
    if (target.isTapScreen) {
        return ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = fadeOut(tween(750), targetAlpha = 0.65f),
            targetContentZIndex = 1f,
            sizeTransform = null,
        )
    }
    // spring(response: 0.46, dampingFraction: 0.88) of the iPhone.
    val enter = fadeIn(spring(0.88f, 187f)) +
        scaleIn(spring(0.88f, 187f), initialScale = 0.97f) +
        slideIn(spring(0.88f, 187f)) { shift }
    return ContentTransform(enter, fadeOut(spring(0.88f, 187f)), targetContentZIndex = 0f, sizeTransform = null)
}

/** Height of the fixed header (below the status bar). Screens leave it empty: see [OnboardingHeaderSpace]. */
val OnboardingHeaderHeight = 44.dp

/** Back chevron + progress bar, owned by the flow: it stays still while the screens change underneath. */
@Composable
fun OnboardingFlowHeader(
    /** 0..1, animated from one step to the next. */
    progress: Double,
    showsBar: Boolean,
    showsBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backAlpha by animateFloatAsState(if (showsBack) 1f else 0f, tween(220), label = "back")
    val barAlpha by animateFloatAsState(if (showsBar) 1f else 0f, tween(220), label = "bar")
    val value by animateFloatAsState(progress.toFloat().coerceIn(0f, 1f), tween(400, easing = FastOutSlowInEasing), label = "progress")
    // The chevron's 44 dp touch area overhangs the screen margin by 12 dp, as on the iPhone.
    Row(
        modifier
            .fillMaxWidth()
            .height(OnboardingHeaderHeight)
            .padding(start = Theme.screenPadding - 12.dp, end = Theme.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .alpha(backAlpha)
                .clickable(enabled = showsBack, role = Role.Button, onClickLabel = L("Back"), interactionSource = null, indication = null, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = if (showsBack) L("Back") else null, tint = Theme.ink, modifier = Modifier.size(18.dp))
        }
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .alpha(barAlpha)
                .clearAndSetSemantics {}
                .background(Theme.hairline, CircleShape),
        ) {
            Box(
                Modifier
                    .widthIn(min = 4.dp)
                    .fillMaxWidth(value)
                    .height(4.dp)
                    .background(Theme.ink, CircleShape),
            )
        }
        Spacer(Modifier.width(32.dp))
    }
}
