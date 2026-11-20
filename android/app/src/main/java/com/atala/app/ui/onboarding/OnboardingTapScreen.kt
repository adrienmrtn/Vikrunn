package com.atala.app.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.CoachPose
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.fontRoom
import com.atala.app.ui.components.screenFitScale
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.components.rememberReduceMotion
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme

/**
 * Full-screen moment with the coach (`OnboardingTapScreen`): the background takes over the screen
 * as a circle growing from the bottom, then the coach and the text fade in. The whole screen is
 * one button that moves on, but only once [canContinue] is true (the coach has finished speaking).
 *
 * @param lightContent white back chevron and light status bar icons, for dark backgrounds.
 * @param hintColor colour of "Tap to continue": white on dark backgrounds, ink on light ones by default.
 * @param pose the picture of the coach shown on this screen.
 * @param backdrop drawn edge to edge (under the status bar), clipped by the growing circle.
 * @param top what the coach says, at the top of the screen.
 */
@Composable
fun OnboardingTapScreen(
    lightContent: Boolean = false,
    hintColor: Color? = null,
    hintShadow: Boolean = false,
    canContinue: Boolean = true,
    pose: CoachPose = CoachPose.Wave,
    /** Light status bar icons (defaults to [lightContent]): for a dark photo behind the bar. */
    lightStatusBar: Boolean = lightContent,
    backdrop: @Composable BoxScope.() -> Unit,
    top: @Composable () -> Unit,
) {
    val nav = LocalOnboardingNavigator.current
    val step = LocalOnboardingStep.current
    val state by collectAppState()
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current

    // State-driven, so the reveal always runs to the end even if the screen change is interrupted.
    val reveal = remember { Animatable(0f) }
    var contentShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (reveal.value >= 1f) return@LaunchedEffect
        if (reduceMotion) {
            reveal.snapTo(1f)
            contentShown = true
            return@LaunchedEffect
        }
        reveal.animateTo(1f, tween(seconds(OnboardingRevealTiming.REVEAL), easing = OnboardingEasing.easeInOut))
    }
    LaunchedEffect(Unit) {
        if (contentShown) return@LaunchedEffect
        kotlinx.coroutines.delay(seconds(OnboardingRevealTiming.CONTENT).toLong())
        contentShown = true
    }
    val shown by animateFloatAsState(
        if (contentShown) 1f else 0f,
        if (reduceMotion) tween(0) else tween(450, easing = OnboardingEasing.easeOut),
        label = "tap content",
    )
    // The coach has finished speaking: a soft tick says the screen can be tapped.
    var wasReady by remember { mutableStateOf(canContinue) }
    LaunchedEffect(canContinue) {
        if (canContinue && !wasReady) haptics.play(HapticKind.Soft)
        wasReady = canContinue
    }
    // The status bar follows the backdrop once the circle has covered the top of the screen.
    OnboardingSystemBars(active = contentShown && isCurrentOnboardingStep(), lightIcons = lightStatusBar)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rise = with(density) { 18.dp.toPx() }
    val fontRoom = fontRoom()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Small phones (and large system fonts): the words and the space above them shrink a
        // little, so the coach keeps a good share of the screen. 1 on regular phones.
        val scale = screenFitScale(maxHeight.value, TAP_SCREEN_REGULAR_HEIGHT * fontRoom, minimum = 0.72f)
        // The backdrop, edge to edge, clipped by the growing circle.
        Box(
            Modifier
                .fillMaxSize()
                .onboardingRevealClip { reveal.value },
            content = backdrop,
        )
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = shown * if (canContinue && pressed) 0.94f else 1f
                    translationY = (1f - shown) * rise
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (canContinue) L("Tap to continue") else null,
                ) {
                    if (!canContinue) return@clickable
                    haptics.tap()
                    nav.next()
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = if (scale < 1f) 48.dp else 56.dp),
            ) {
                CompositionLocalProvider(LocalOnboardingTextScale provides scale) { top() }
            }
            // The figure takes the room left, up to 500 dp high; at least 12 dp above it.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp * scale),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Image(
                    painterResource(AtalaImages.coachPose(state.coach, pose)),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .heightIn(max = 500.dp)
                        .fillMaxSize()
                        .drawBehind {
                            // Soft shadow on the ground, under the feet.
                            val radius = 100.dp.toPx() * scale
                            val centre = Offset(size.width / 2, size.height + 4.dp.toPx())
                            withTransform({ scale(1f, 0.12f, pivot = centre) }) {
                                drawCircle(
                                    Brush.radialGradient(
                                        listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.16f), Color.Transparent),
                                        center = centre,
                                        radius = radius,
                                    ),
                                    radius = radius,
                                    center = centre,
                                )
                            }
                        },
                )
            }
            OnboardingTapHint(
                color = hintColor ?: if (lightContent) Color.White else Theme.ink,
                visible = canContinue,
                shadow = hintShadow,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp * scale, bottom = 14.dp),
            )
        }
        if (step.allowsBack) {
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 8.dp)
                    .size(44.dp)
                    .graphicsLayer { alpha = shown }
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = L("Back")) {
                        haptics.tap()
                        nav.back()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = L("Back"),
                    tint = if (lightContent) Color.White else Theme.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Full screen height (dp) from which nothing is shrunk on the tap screens. */
private const val TAP_SCREEN_REGULAR_HEIGHT = 780f
