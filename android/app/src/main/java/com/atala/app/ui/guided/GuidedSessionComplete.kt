package com.atala.app.ui.guided

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.core.model.CoachPose
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.CoachPoseImage
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme
import com.atala.app.ui.today.FitText
import com.atala.app.ui.today.lineHeight
import com.atala.app.ui.today.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * The "Session complete" moment (SessionCompleteScreen.swift and SessionCompleteHero.swift), as used
 * by the end of a guided session. The run feature keeps its own copy in ui/run.
 */

/** Stages of the sequence, in the order they are reached. */
private object Stage {
    const val CHECK = 1
    const val TITLE = 2
    const val DATA_LINE = 3
    const val SHEET = 4
    /** One stage per stat: 5, 6 and 7. */
    const val FIRST_STAT = 5
    const val COACH = 8
    const val EXTRAS = 9
    const val BUTTON = 10
}

/** One figure of the content sheet. It counts from zero to [value] when it appears. */
internal class SessionCompleteStat(val value: Double, val label: String, val format: (Double) -> String)

private val OVERLAP = 30.dp
private val CHECK_SIZE = 68.dp
/** Rough height of the check, the title and the line, used to place them. */
private val TEXT_HEIGHT = 180.dp

/**
 * The photo hero fills the top; the check mark draws itself, the title and the figures follow, then
 * a white sheet slides up with the stats, the coach, [extras] and, last, the primary button.
 * With reduced motion everything is there at once.
 */
@Composable
internal fun GuidedSessionCompleteScreen(
    eyebrow: String?,
    dataLine: String,
    stats: List<SessionCompleteStat>,
    coach: Coach,
    coachLine: String,
    buttonTitle: String,
    onPrimary: () -> Unit,
    extras: @Composable () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val haptics = rememberHaptics()
    var stage by rememberSaveable { mutableIntStateOf(0) }
    /** True while the confetti is on screen. */
    var burst by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (stage != 0) {
            // Interrupted earlier (the screen went away and came back): never leave it half shown.
            stage = Stage.BUTTON
            return@LaunchedEffect
        }
        if (reduceMotion) {
            stage = Stage.BUTTON
            haptics.play(HapticKind.Success)
            return@LaunchedEffect
        }
        delay(250); stage = Stage.CHECK
        // The check mark lands: the one success haptic of the moment, and the confetti.
        delay(350)
        haptics.play(HapticKind.Success)
        burst = true
        delay(50); stage = Stage.TITLE
        delay(300); stage = Stage.DATA_LINE
        delay(250); stage = Stage.SHEET
        delay(300); stage = Stage.FIRST_STAT
        delay(120); stage = Stage.FIRST_STAT + 1
        delay(120); stage = Stage.FIRST_STAT + 2
        delay(200); stage = Stage.COACH
        delay(200); stage = Stage.EXTRAS
        delay(250); stage = Stage.BUTTON
        delay(400)
        burst = false
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Theme.forest)) {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val heroHeight = maxOf(260.dp, maxHeight * 0.45f)
        // Small phones: the avatar instead of the full-body coach.
        val compact = maxHeight < 700.dp
        val screenHeight = maxHeight
        val textTop = textTop(heroHeight, topInset)
        Hero(heroHeight, textTop, eyebrow, dataLine, stage, reduceMotion)
        if (burst) {
            Confetti(Modifier.fillMaxWidth().height(heroHeight), originY = textTop + CHECK_SIZE / 2)
        }
        val sheetShown = stage >= Stage.SHEET
        val hidden = with(LocalDensity.current) { screenHeight.toPx() }
        val sheetOffset by animateFloatAsState(
            if (sheetShown) 0f else 1f,
            if (reduceMotion) tween(0) else spring(dampingRatio = 0.86f, stiffness = 130f),
            label = "sheet",
        )
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // The hero stays visible through this empty room.
                Spacer(Modifier.height(heroHeight - OVERLAP))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = screenHeight - heroHeight + OVERLAP)
                        .graphicsLayer { translationY = sheetOffset * hidden }
                        .background(Color.White, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 26.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().card(radius = 24.dp).padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        stats.forEachIndexed { index, stat ->
                            StatView(stat, stage >= Stage.FIRST_STAT + minOf(index, 2), reduceMotion, Modifier.weight(1f))
                        }
                    }
                    Staged(stage >= Stage.COACH) { CoachRow(coach, coachLine, compact) }
                    Staged(stage >= Stage.EXTRAS) { extras() }
                }
            }
            // Hidden with the sheet, and the button only becomes tappable at the last stage.
            val ready = stage >= Stage.BUTTON
            Box(Modifier.alpha(if (sheetShown) 1f else 0f).background(Color.White).navigationBarsPadding()) {
                BottomBar {
                    Box(Modifier.alpha(if (ready) 1f else 0f)) {
                        PrimaryButton(buttonTitle, onClick = { if (ready) onPrimary() }, haptic = if (ready) HapticKind.Confirm else null)
                    }
                }
            }
        }
    }
}

/** Distance from the top of the hero to the top of the check mark, clear of the status bar. */
private fun textTop(height: Dp, topInset: Dp): Dp {
    val inset = maxOf(topInset, 54.dp)
    val free = height - OVERLAP - inset - TEXT_HEIGHT
    return inset + maxOf(8.dp, free * 0.3f)
}

/** Fades and rises into place (`OnboardingStagedModifier`). */
@Composable
private fun Staged(visible: Boolean, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(if (visible) 1f else 0f, tween(400, easing = LinearOutSlowInEasing), label = "staged")
    val rise = with(LocalDensity.current) { 14.dp.toPx() }
    Box(Modifier.graphicsLayer { alpha = progress; translationY = (1 - progress) * rise }) { content() }
}

@Composable
private fun StatView(stat: SessionCompleteStat, shown: Boolean, reduceMotion: Boolean, modifier: Modifier) {
    val value by animateFloatAsState(
        if (shown) stat.value.toFloat() else 0f,
        if (reduceMotion) tween(0) else tween(600, easing = LinearOutSlowInEasing),
        label = "count",
    )
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(400), label = "stat")
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    val description = "${stat.label}: ${stat.format(stat.value)}"
    Column(
        modifier
            .graphicsLayer { alpha = appear; translationY = (1 - appear) * rise }
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FitText(stat.format(value.toDouble()), Theme.font(24, FontWeight.Bold).copy(fontFeatureSettings = "tnum"), Theme.ink, minScale = 0.6f)
        Text(stat.label, style = Theme.font(12), color = Theme.muted)
    }
}

@Composable
private fun CoachRow(coach: Coach, line: String, compact: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        if (compact) {
            CoachAvatar(coach, size = 40.dp)
        } else {
            CoachPoseImage(coach, Modifier.size(74.dp, 118.dp).clearAndSetSemantics {}, pose = CoachPose.Cheer)
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .background(Theme.bubble, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(coach.name, style = Theme.font(13, FontWeight.SemiBold), color = Theme.green)
            Text(line, style = Theme.font(15).copy(lineHeight = lineHeight(15, 3)), color = Theme.ink)
        }
    }
}

// MARK: - Hero

/** The night-track photo edge to edge, darkened at the top, with the check mark, the title and one line of figures. */
@Composable
private fun Hero(height: Dp, textTop: Dp, eyebrow: String?, dataLine: String, stage: Int, reduceMotion: Boolean) {
    var settled by rememberSaveable { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) { settled = true }
    val zoom by animateFloatAsState(if (settled) 1f else 1.08f, tween(6000, easing = LinearOutSlowInEasing), label = "zoom")
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Theme.forest)
            .clipToBounds(),
    ) {
        // Anchored to the bottom, so the runners in the lower part of the picture are never cropped.
        AtalaImages["photo_session_complete"]?.let { id ->
            Image(
                painterResource(id), null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.68f),
                        0.55f to Color.Black.copy(alpha = 0.36f),
                        0.92f to Color.Black.copy(alpha = 0f),
                    ),
                ),
        )
        HeroTexts(
            eyebrow, dataLine, stage, reduceMotion,
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = textTop),
        )
    }
}

private val heroShadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 4f), blurRadius = 16f)

@Composable
private fun HeroTexts(eyebrow: String?, dataLine: String, stage: Int, reduceMotion: Boolean, modifier: Modifier) {
    val lineShown by animateFloatAsState(if (stage >= Stage.DATA_LINE) 1f else 0f, tween(400), label = "line")
    val rise = with(LocalDensity.current) { 10.dp.toPx() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SessionCompleteCheck(stage >= Stage.CHECK, reduceMotion, Modifier.padding(bottom = 8.dp))
        HeroTitle(stage >= Stage.TITLE, reduceMotion)
        Column(
            Modifier.graphicsLayer { alpha = lineShown; translationY = (1 - lineShown) * rise },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FitText(
                dataLine,
                TextStyle(shadow = heroShadow).merge(Theme.font(18, FontWeight.SemiBold)).copy(fontFeatureSettings = "tnum"),
                Color.White, minScale = 0.7f, textAlign = TextAlign.Center,
            )
            if (eyebrow != null) {
                FitText(
                    eyebrow, TextStyle(shadow = heroShadow).merge(Theme.font(14)),
                    Color.White.copy(alpha = 0.82f), minScale = 0.7f, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** "Session complete", one word after the other, rising into place. */
@Composable
private fun HeroTitle(shown: Boolean, reduceMotion: Boolean) {
    val title = L("Session complete")
    val words = title.split(" ").filter { it.isNotEmpty() }
    val rise = with(LocalDensity.current) { 14.dp.toPx() }
    val size = if (title.length > 20) 30 else 36
    Row(
        Modifier.clearAndSetSemantics {
            contentDescription = title
            heading()
        },
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        words.forEachIndexed { index, word ->
            val progress by animateFloatAsState(
                if (shown) 1f else 0f,
                if (reduceMotion) tween(0) else tween(400, delayMillis = index * 120, easing = LinearOutSlowInEasing),
                label = "word",
            )
            Text(
                word,
                style = TextStyle(shadow = heroShadow).merge(Theme.font(size, FontWeight.Bold)),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = progress; translationY = (1 - progress) * rise },
            )
        }
    }
}

/** A white check mark that draws itself inside a ring, with a small pop (`SessionCompleteCheck`). */
@Composable
private fun SessionCompleteCheck(drawn: Boolean, reduceMotion: Boolean, modifier: Modifier) {
    val progress by animateFloatAsState(
        if (drawn) 1f else 0f,
        if (reduceMotion) tween(0) else tween(450, easing = LinearOutSlowInEasing),
        label = "check",
    )
    val pop by animateFloatAsState(
        if (drawn) 1f else 0f,
        if (reduceMotion) tween(0) else spring(dampingRatio = 0.6f, stiffness = 195f),
        label = "pop",
    )
    Canvas(
        modifier
            .size(CHECK_SIZE)
            .scale(0.6f + 0.4f * pop)
            .alpha(pop.coerceIn(0f, 1f)),
    ) {
        val w = size.width
        drawCircle(Color.White.copy(alpha = 0.16f))
        val ring = 3.dp.toPx()
        drawArc(
            Color.White, -90f, 360f * progress, false,
            topLeft = Offset(ring / 2, ring / 2), size = Size(w - ring, w - ring),
            style = Stroke(ring, cap = StrokeCap.Round),
        )
        val tick = Path().apply {
            moveTo(w * 0.29f, w * 0.53f)
            lineTo(w * 0.44f, w * 0.67f)
            lineTo(w * 0.72f, w * 0.37f)
        }
        val measure = PathMeasure().apply { setPath(tick, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress, partial, true)
        drawPath(partial, Color.White, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A short burst of small pieces in the brand colours, flying up from one point and falling back. */
@Composable
private fun Confetti(modifier: Modifier, originY: Dp) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (time <= CONFETTI_DURATION) {
            withFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }
    val colors = listOf(Theme.green, Theme.orange, Theme.cream)
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = time.toDouble()
        if (t < 0 || t > CONFETTI_DURATION) return@Canvas
        val fade = minOf(1.0, (CONFETTI_DURATION - t) / 0.4).toFloat()
        val origin = originY.toPx()
        val scale = density
        for (index in 0 until 26) {
            val angle = -Math.PI / 2 + (unit(index, 1.0) - 0.5) * 2.6
            val speed = (150 + 230 * unit(index, 2.0)) * scale
            val dx = cos(angle) * speed * t
            val dy = sin(angle) * speed * t + 0.5 * 560 * scale * t * t
            val side = ((5 + 4 * unit(index, 3.0)) * scale).toFloat()
            val spin = ((unit(index, 4.0) - 0.5) * 14 * t * 180 / Math.PI).toFloat()
            val color = colors[index % colors.size].copy(alpha = fade)
            translate(size.width / 2 + dx.toFloat(), origin + dy.toFloat()) {
                rotate(spin, pivot = Offset.Zero) {
                    if (index % 2 == 0) {
                        drawCircle(color, radius = side / 2, center = Offset.Zero)
                    } else {
                        drawRoundRect(
                            color, topLeft = Offset(-side / 2, -side * 0.3f), size = Size(side, side * 0.6f),
                            cornerRadius = CornerRadius(1.5f * scale),
                        )
                    }
                }
            }
        }
    }
}

private const val CONFETTI_DURATION = 1.2f

/** A fixed pseudo-random value in 0..<1 for a piece: the burst is the same every time. */
private fun unit(index: Int, salt: Double): Double {
    val raw = sin(index * 12.9898 + salt * 78.233) * 43758.5453
    return raw - floor(raw)
}
