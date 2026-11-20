package com.atala.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.rememberReduceMotion
import com.atala.app.ui.theme.Theme

/**
 * Right after the coach's reply to the "why" (step `Consistency`): the reason Atala exists. The
 * logo settles in, the two sentences write themselves one after the other, then the button arrives.
 */
@Composable
fun OnboardingConsistencyScreen() {
    val reduceMotion = rememberReduceMotion()
    var logoShown by remember { mutableStateOf(false) }
    var headlineDone by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { logoShown = true }
    val logo by animateFloatAsState(
        if (logoShown) 1f else 0f,
        if (reduceMotion) tween(0) else tween(600, delayMillis = 120, easing = OnboardingEasing.easeOut),
        label = "logo",
    )
    val rise = with(LocalDensity.current) { 8.dp.toPx() }
    // Grows from its leading edge.
    val originX = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else 0f
    OnboardingScaffold(buttonsVisible = finished) {
        OnboardingBrandLogo(
            52.dp,
            Theme.forest,
            Modifier
                .padding(top = 20.dp)
                .graphicsLayer {
                    alpha = logo
                    val scale = 0.86f + 0.14f * logo
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - logo) * rise
                    transformOrigin = TransformOrigin(originX, 0.5f)
                },
            mark = true,
        )
        OnboardingEmphasisText(
            L("Being consistent is hard."),
            size = 38,
            lineSpacing = 2.dp,
            wordInterval = 0.11,
            startDelay = 0.45,
            isHeading = true,
            onFinished = { headlineDone = true },
        )
        // Starts once the headline is there, after a short breath, whatever its length.
        // The words between the two `*` are the highlighted ones: the markers stay in the translations.
        OnboardingEmphasisText(
            L("That's why Atala exists: *a coach in your pocket,* always available, built to finally get you to your goal."),
            size = 26,
            lineSpacing = 6.dp,
            wordInterval = 0.085,
            isActive = headlineDone,
            startDelay = 0.2,
            onFinished = { finished = true },
        )
    }
}
