package com.atala.app.ui.guided

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.atala.app.core.exercise.ExerciseInfo
import com.atala.app.core.exercise.GuidedExerciseKind
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.rememberReduceMotion
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme
import com.atala.app.ui.today.FitText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The symbol of an exercise kind (`GuidedExerciseKind.symbol`). */
internal val GuidedExerciseKind.icon: ImageVector
    get() = if (this == GuidedExerciseKind.Stretch) Icons.Filled.SelfImprovement else Icons.Filled.FitnessCenter

/**
 * An exercise picture of the chosen coach (coach 1 has the plain names, the others
 * `exercise_c<coach>_...`), or a neutral symbol when the image is missing (`GuidedExerciseImage`).
 */
@Composable
internal fun GuidedExerciseImage(name: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val state by collectAppState()
    val id = AtalaImages.exercise(name, state.coach.id)
    if (id != null) {
        Image(painterResource(id), null, contentScale = ContentScale.Fit, modifier = modifier.fillMaxSize())
    } else {
        BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val size = maxOf(12.dp, min(maxWidth, maxHeight) * 0.42f)
            Icon(icon, null, tint = Theme.greenSoft, modifier = Modifier.size(size))
        }
    }
}

/** Small rounded picture of an exercise, for lists (`GuidedExerciseThumb`). */
@Composable
internal fun GuidedExerciseThumb(info: ExerciseInfo, size: Dp = 56.dp) {
    Box(
        Modifier
            .size(size)
            .background(Theme.surface, RoundedCornerShape(size * 0.28f))
            .padding(size * 0.08f),
    ) {
        GuidedExerciseImage(info.imageStart, info.kind.icon)
    }
}

/** The two frames of an exercise, alternating in a loop: start, deep position, back (`GuidedExerciseAnimation`). */
@Composable
internal fun GuidedExerciseAnimation(info: ExerciseInfo, playing: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val deep = remember(info.id) { Animatable(0f) }
    LaunchedEffect(info.id, playing) {
        deep.snapTo(0f)
        if (!playing) return@LaunchedEffect
        val tempo = info.tempo
        fun ms(seconds: Double) = (maxOf(0.05, seconds) * 1000).toLong()
        suspend fun show(value: Float) {
            if (reduceMotion) deep.snapTo(value)
            else deep.animateTo(value, tween(ms(tempo.fade).toInt(), easing = FastOutSlowInEasing))
        }
        while (isActive) {
            delay(ms(tempo.startHold))
            show(1f)
            delay(ms(tempo.deepHold))
            show(0f)
        }
    }
    val description = L("%@: %@", info.name, info.summary)
    Box(modifier.clearAndSetSemantics { contentDescription = description }) {
        GuidedExerciseImage(info.imageStart, info.kind.icon, Modifier.alpha(1f - deep.value))
        GuidedExerciseImage(info.imageEnd, info.kind.icon, Modifier.alpha(deep.value))
    }
}

/** Circular countdown with a large number in the middle (`GuidedTimerRing`). */
@Composable
internal fun GuidedTimerRing(progress: Double, text: String, caption: String, tint: Color = Theme.green, size: Dp = 132.dp) {
    val left by animateFloatAsState((1 - progress).coerceIn(0.0, 1.0).toFloat(), tween(200, easing = LinearEasing), label = "ring")
    val description = L("%@ %@ left", text, caption)
    Box(Modifier.size(size).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 10.dp.toPx()
            val inset = width / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - width, this.size.height - width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(Theme.hairline, 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
            drawArc(tint, -90f, 360f * left, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
        }
        Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(text, Theme.font((size.value * 0.36f).toInt(), FontWeight.Bold).copy(fontFeatureSettings = "tnum"), Theme.ink, minScale = 0.5f)
            Text(caption, style = Theme.font(12), color = Theme.muted)
        }
    }
}

/** One segment per exercise: done, current (taller) and to come (`GuidedProgressBar`). */
@Composable
internal fun GuidedProgressBar(count: Int, currentIndex: Int, complete: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(10.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until maxOf(1, count)) {
            val isCurrent = !complete && index == currentIndex
            val isDone = complete || index < currentIndex
            val height by animateDpAsState(if (isCurrent) 10.dp else 6.dp, tween(250), label = "segment")
            val color = when {
                isCurrent -> Theme.green
                isDone -> Theme.greenSoft
                else -> Color(0xFFD9D9D6)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .alpha(if (isDone) 0.55f else 1f)
                    .background(color, CircleShape),
            )
        }
    }
}

/** Small rounded label ("Left leg", "Set 2 of 3") (`GuidedChip`). */
@Composable
internal fun GuidedChip(text: String, foreground: Color = Theme.green, background: Color = Color.White) {
    Text(
        text,
        style = Theme.font(14, FontWeight.Bold),
        color = foreground,
        maxLines = 1,
        modifier = Modifier
            .background(background, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
