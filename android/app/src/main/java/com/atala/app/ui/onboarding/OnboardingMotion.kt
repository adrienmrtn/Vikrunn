package com.atala.app.ui.onboarding

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.findActivity
import com.atala.app.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

// Shared motion of the entry flow (OnboardingMotion.swift): timings, easings, the circular reveal
// of the coach screens, the "Tap to continue" hint, staged appearance and counting numbers.

/** Timings of the coach explainer screens, in seconds (`OnboardingRevealTiming`). */
object OnboardingRevealTiming {
    /** Duration of the circular colour reveal. */
    const val REVEAL = 0.65
    /** How long the previous screen stays underneath while the circle grows. */
    const val HOLD = 0.75
    /** When the coach and the text start to fade in. */
    const val CONTENT = 0.42
    /** When the coach starts to speak. */
    const val SPEECH = 0.7
}

/** SwiftUI's curves, so the timings feel the same as on the iPhone. */
object OnboardingEasing {
    val easeOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val easeIn: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val easeInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    /** Quick start, long soft landing: how a word settles (`OnboardingWordReveal`). */
    val word: Easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
}

/**
 * The iPhone's `spring(response:dampingFraction:)` as a Compose spring: stiffness = (2π / response)².
 */
fun <T> onboardingSpring(response: Double, dampingFraction: Double, visibilityThreshold: T? = null) =
    spring(
        dampingRatio = dampingFraction.toFloat(),
        stiffness = (2 * Math.PI / response).let { it * it }.toFloat(),
        visibilityThreshold = visibilityThreshold,
    )

/** Milliseconds from the iPhone's seconds. */
fun seconds(value: Double): Int = (value * 1000).roundToInt()

// MARK: Staged appearance

/**
 * Raises a stage from 0 to `delays.size`, waiting `delays[n]` seconds before stage `n + 1`
 * (`onboardingStageSequence`). Starts once [isActive] is true (to chain it after another
 * animation); with reduced motion every stage is there at once. Read it as the current stage.
 */
@Composable
fun rememberOnboardingStages(delays: List<Double>, isActive: Boolean = true): Int {
    val reduceMotion = rememberReduceMotion()
    val total = delays.size
    // Kept across a rotation, so the sequence does not play again.
    var stage by rememberSaveable { mutableIntStateOf(if (reduceMotion && isActive) total else 0) }
    LaunchedEffect(isActive, total) {
        if (!isActive || stage >= total) return@LaunchedEffect
        if (reduceMotion) {
            stage = total
            return@LaunchedEffect
        }
        for (index in stage until total) {
            delay(seconds(maxOf(0.0, delays[index])).toLong())
            stage = index + 1
        }
    }
    return stage
}

/**
 * Fade and rise of the blocks that appear one after another (`OnboardingStagedModifier`): hidden
 * 14 dp below its place, then eased in over 0.45 s.
 */
@Composable
fun Modifier.onboardingStaged(visible: Boolean, rise: Float = 14f): Modifier {
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(450, easing = OnboardingEasing.easeOut),
        label = "staged",
    )
    val density = LocalDensity.current
    val risePx = with(density) { rise.dp.toPx() }
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * risePx
    }
}

/** Visible once [stage] has passed [index]. */
@Composable
fun Modifier.onboardingStaged(index: Int, stage: Int): Modifier = onboardingStaged(stage > index)

/**
 * Appears with a short spring, growing from its bottom edge (the coach figures of the level,
 * ready and commitment screens).
 */
@Composable
fun Modifier.onboardingSpringIn(visible: Boolean, from: Float = 0.92f, response: Double = 0.5, damping: Double = 0.72): Modifier {
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        if (reduceMotion) tween(0) else onboardingSpring(response, damping),
        label = "spring in",
    )
    return graphicsLayer {
        alpha = progress.coerceIn(0f, 1f)
        val scale = from + (1f - from) * progress
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0.5f, 1f)
    }
}

// MARK: Numbers

/**
 * A number that counts towards [value] (`OnboardingCountingValue`): each change runs from the
 * value shown to the new one over [durationMillis].
 */
@Composable
fun OnboardingCountingValue(
    value: Double,
    format: (Int) -> String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    durationMillis: Int = 900,
    easing: Easing = OnboardingEasing.easeOut,
) {
    val shown by animateFloatAsState(
        value.toFloat(),
        tween(durationMillis, easing = easing),
        label = "counting",
    )
    Text(
        format(shown.roundToInt()),
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

// MARK: Circular reveal

/**
 * Clips the content to a circle growing from below the bottom edge (centre bottom) until it covers
 * the whole rectangle (`OnboardingRevealShape`). At 1 nothing is clipped. [progress] is read at
 * draw time only, so the animation does not recompose.
 */
fun Modifier.onboardingRevealClip(progress: () -> Float): Modifier = drawWithContent {
    val value = progress()
    when {
        value >= 1f -> drawContent()
        value <= 0f -> Unit
        else -> {
            val drop = size.width * 0.3f
            val centre = Offset(size.width / 2, size.height + drop)
            val full = hypot(size.width / 2, size.height + drop) + 2f
            val radius = full * value
            val path = Path().apply {
                addOval(Rect(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius))
            }
            clipPath(path) { this@drawWithContent.drawContent() }
        }
    }
}

/** "Tap to continue" as plain text: fades in when [visible], then breathes gently (`OnboardingTapHint`). */
@Composable
fun OnboardingTapHint(color: Color, visible: Boolean, modifier: Modifier = Modifier, shadow: Boolean = false) {
    val reduceMotion = rememberReduceMotion()
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(450, easing = OnboardingEasing.easeOut), label = "hint")
    val breath = if (visible && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "hint breath")
        transition.animateFloat(
            1f, 0.45f,
            infiniteRepeatable(tween(1000, easing = OnboardingEasing.easeInOut), RepeatMode.Reverse),
            label = "breath",
        ).value
    } else {
        1f
    }
    Text(
        L("Tap to continue"),
        style = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 4f), 12f) else null,
        ),
        color = color,
        modifier = modifier
            .clearAndSetSemantics {}
            .graphicsLayer { alpha = shown * breath },
    )
}

// MARK: System bars

/**
 * While [active], the status bar is see-through so a full-bleed screen (welcome photo, coach
 * explainers) reaches the top edge, with light icons when [lightIcons]. Everything is put back
 * as soon as the screen stops being active.
 */
@Suppress("DEPRECATION")
@Composable
fun OnboardingSystemBars(active: Boolean, lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(active, lightIcons) {
        val window = view.context.findActivity()?.window
        if (!active || window == null) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previousColor = window.statusBarColor
        val previousLight = controller.isAppearanceLightStatusBars
        window.statusBarColor = AndroidColor.TRANSPARENT
        controller.isAppearanceLightStatusBars = !lightIcons
        onDispose {
            window.statusBarColor = previousColor
            controller.isAppearanceLightStatusBars = previousLight
        }
    }
}

/** True while the step a screen belongs to is the flow's current step (false while it animates out). */
@Composable
fun isCurrentOnboardingStep(): Boolean = LocalOnboardingNavigator.current.step == LocalOnboardingStep.current
