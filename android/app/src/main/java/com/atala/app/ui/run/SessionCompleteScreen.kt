package com.atala.app.ui.run

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Stages of the "Session complete" sequence, in the order they are reached. */
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
class SessionCompleteStat(val value: Double, val label: String, val format: (Double) -> String)

/**
 * The "Session complete" moment shared by the end of a run and the end of a guided session
 * (SessionCompleteScreen.swift and SessionCompleteHero.swift). The photo hero fills the top; the
 * check mark draws itself, the title and the figures follow, then a white sheet slides up with the
 * stats, the coach, the caller's extra content and, last, the primary button. With the system
 * animations off everything is there at once.
 */
@Composable
fun SessionCompleteScreen(
    dataLine: String,
    stats: List<SessionCompleteStat>,
    coach: Coach,
    coachLine: String,
    buttonTitle: String,
    onPrimary: () -> Unit,
    eyebrow: String? = null,
    extras: @Composable ColumnScope.() -> Unit = {},
) {
    val reduceMotion = rememberReduceMotion()
    val haptics = rememberHaptics()
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var burst by remember { mutableLongStateOf(0L) }

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
        delay(250)
        stage = Stage.CHECK
        // The check mark lands: the one success haptic of the moment, and the confetti.
        delay(350)
        haptics.play(HapticKind.Success)
        burst = withFrameNanos { it }
        for ((target, wait) in listOf(
            Stage.TITLE to 50L, Stage.DATA_LINE to 300L, Stage.SHEET to 250L, Stage.FIRST_STAT to 300L,
            Stage.FIRST_STAT + 1 to 120L, Stage.FIRST_STAT + 2 to 120L, Stage.COACH to 200L, Stage.EXTRAS to 200L,
            Stage.BUTTON to 250L,
        )) {
            delay(wait)
            stage = target
        }
        delay(400)
        burst = 0L
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Theme.forest)) {
        val heroHeight = maxOf(260.dp, maxHeight * 0.45f)
        val overlap = 30.dp
        // Small phones: the avatar instead of the full-body coach.
        val compact = maxHeight < 700.dp
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val textTop = heroTextTop(heroHeight, topInset, overlap)
        Hero(heroHeight, textTop, eyebrow, dataLine, stage, reduceMotion)
        if (burst != 0L) Confetti(burst, textTop + 34.dp, Modifier.fillMaxWidth().height(heroHeight))

        val sheetShown = stage >= Stage.SHEET
        val sheetOffset by animateFloatAsState(
            if (sheetShown) 0f else 1f,
            if (reduceMotion) tween(0) else spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow),
            label = "sheet",
        )
        val screenHeight = with(LocalDensity.current) { maxHeight.toPx() }
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // The hero stays visible through this empty room.
                Spacer(Modifier.height(heroHeight - overlap))
                Column(
                    Modifier
                        .graphicsLayer { translationY = sheetOffset * screenHeight }
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .padding(horizontal = Theme.screenPadding)
                        .padding(top = 26.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(Modifier.fillMaxWidth().card(radius = 24.dp).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.forEachIndexed { index, stat ->
                            StatView(stat, stage >= Stage.FIRST_STAT + minOf(index, 2), reduceMotion, Modifier.weight(1f))
                        }
                    }
                    Staged(stage >= Stage.COACH) { CoachRow(coach, coachLine, compact) }
                    Staged(stage >= Stage.EXTRAS) { Column(content = extras) }
                }
                // White under the sheet, so the dark backdrop never shows below the content.
                Spacer(Modifier.fillMaxWidth().height(400.dp).graphicsLayer { translationY = sheetOffset * screenHeight }.background(Color.White))
            }
            val ready = stage >= Stage.BUTTON
            Box(Modifier.graphicsLayer { alpha = if (sheetShown) 1f else 0f }.background(Color.White).windowInsetsPadding(WindowInsets.navigationBars)) {
                BottomBar {
                    Box(Modifier.graphicsLayer { alpha = if (ready) 1f else 0f }) {
                        PrimaryButton(buttonTitle, onClick = { if (ready) onPrimary() })
                    }
                }
            }
        }
    }
}

/** Distance from the top of the hero to the top of the check mark, clear of the status bar. */
private fun heroTextTop(height: Dp, topInset: Dp, overlap: Dp): Dp {
    val inset = maxOf(topInset, 54.dp)
    val free = height - overlap - inset - 180.dp
    return inset + maxOf(8.dp, free * 0.3f)
}

@Composable
private fun Staged(visible: Boolean, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "staged")
    val density = LocalDensity.current
    Box(Modifier.graphicsLayer {
        alpha = progress
        translationY = (1 - progress) * with(density) { 14.dp.toPx() }
    }) { content() }
}

@Composable
private fun StatView(stat: SessionCompleteStat, shown: Boolean, reduceMotion: Boolean, modifier: Modifier) {
    val value by animateFloatAsState(if (shown) stat.value.toFloat() else 0f, if (reduceMotion) tween(0) else tween(600), label = "count")
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(400), label = "stat")
    val density = LocalDensity.current
    val description = "${stat.label}: ${stat.format(stat.value)}"
    Column(
        modifier
            .graphicsLayer {
                alpha = appear
                translationY = (1 - appear) * with(density) { 12.dp.toPx() }
            }
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FitText(stat.format(if (shown && value >= stat.value.toFloat() - 0.001f) stat.value else value.toDouble()),
            Theme.font(24, FontWeight.Bold).digits(), Theme.ink, minScale = 0.6f)
        Text(stat.label, style = Theme.font(12), color = Theme.muted)
    }
}

@Composable
private fun CoachRow(coach: Coach, line: String, compact: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        if (compact) CoachAvatar(coach, size = 40.dp)
        else CoachPoseImage(coach, Modifier.width(74.dp).height(118.dp), CoachPose.Cheer)
        Column(
            Modifier
                .weight(1f, fill = false)
                .background(Theme.bubble, RunBubbleShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(coach.name, style = Theme.font(13, FontWeight.SemiBold), color = Theme.green)
            Text(line, style = Theme.font(15).copy(lineHeight = 21.sp), color = Theme.ink)
        }
    }
}

/** The night-track photo edge to edge, darkened at the top, with the check, the title and the figures. */
@Composable
private fun Hero(height: Dp, textTop: Dp, eyebrow: String?, dataLine: String, stage: Int, reduceMotion: Boolean) {
    var settled by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) { settled = true }
    val zoom by animateFloatAsState(if (settled) 1f else 1.08f, tween(if (reduceMotion) 0 else 6000, easing = FastOutSlowInEasing), label = "zoom")
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        AtalaImages["photo_session_complete"]?.let { id ->
            Image(
                painterResource(id), null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.68f),
                    0.55f to Color.Black.copy(alpha = 0.36f),
                    0.92f to Color.Black.copy(alpha = 0f),
                ),
            ),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = textTop),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Check(stage >= Stage.CHECK, reduceMotion, Modifier.padding(bottom = 8.dp))
            Title(stage >= Stage.TITLE, reduceMotion)
            val lineShown by animateFloatAsState(if (stage >= Stage.DATA_LINE) 1f else 0f, tween(400), label = "line")
            val density = LocalDensity.current
            Column(
                Modifier.graphicsLayer {
                    alpha = lineShown
                    translationY = (1 - lineShown) * with(density) { 10.dp.toPx() }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 8f)
                FitText(dataLine, Theme.font(18, FontWeight.SemiBold).digits().copy(shadow = shadow), Color.White,
                    minScale = 0.7f, textAlign = TextAlign.Center)
                if (eyebrow != null) {
                    FitText(eyebrow, Theme.font(14).copy(shadow = shadow), Color.White.copy(alpha = 0.82f), minScale = 0.7f, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** "Session complete", one word after the other, rising into place. */
@Composable
private fun Title(shown: Boolean, reduceMotion: Boolean) {
    val title = L("Session complete")
    val words = title.split(" ")
    val density = LocalDensity.current
    Row(
        Modifier.clearAndSetSemantics { heading(); contentDescription = title },
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        words.forEachIndexed { index, word ->
            val progress by animateFloatAsState(
                if (shown) 1f else 0f,
                if (reduceMotion) tween(0) else tween(400, delayMillis = index * 120),
                label = "word",
            )
            Text(
                word,
                style = Theme.font(36, FontWeight.Bold).copy(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 8f)),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    alpha = progress
                    translationY = (1 - progress) * with(density) { 14.dp.toPx() }
                },
            )
        }
    }
}

/** A white check mark that draws itself inside a ring, with a small pop. */
@Composable
private fun Check(drawn: Boolean, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val trim = remember { Animatable(0f) }
    val pop by animateFloatAsState(
        if (drawn) 1f else 0f,
        if (reduceMotion) tween(0) else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "pop",
    )
    LaunchedEffect(drawn) {
        if (drawn) trim.animateTo(1f, if (reduceMotion) tween(0) else tween(450)) else trim.snapTo(0f)
    }
    Canvas(
        modifier
            .size(68.dp)
            .graphicsLayer {
                val scale = 0.6f + 0.4f * pop
                scaleX = scale
                scaleY = scale
                alpha = pop.coerceIn(0f, 1f)
            },
    ) {
        drawCircle(Color.White.copy(alpha = 0.16f))
        val ring = 3.dp.toPx()
        drawArc(
            Color.White, -90f, 360f * trim.value, false,
            topLeft = Offset(ring / 2, ring / 2), size = Size(size.width - ring, size.height - ring),
            style = Stroke(ring, cap = StrokeCap.Round),
        )
        val tick = Path().apply {
            moveTo(size.width * 0.29f, size.height * 0.53f)
            lineTo(size.width * 0.44f, size.height * 0.67f)
            lineTo(size.width * 0.72f, size.height * 0.37f)
        }
        val measure = PathMeasure().apply { setPath(tick, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * trim.value, part, true)
        drawPath(part, Color.White, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A short burst of small pieces in the brand colours, flying up from one point and falling back. */
@Composable
private fun Confetti(start: Long, originY: Dp, modifier: Modifier) {
    var now by remember { mutableLongStateOf(start) }
    LaunchedEffect(start) {
        while (true) {
            now = withFrameNanos { it }
            if ((now - start) / 1e9 > 1.2) break
        }
    }
    val colors = listOf(Theme.green, Theme.orange, Theme.cream)
    fun unit(index: Int, salt: Double): Double {
        val raw = sin(index * 12.9898 + salt * 78.233) * 43758.5453
        return raw - floor(raw)
    }
    Canvas(modifier) {
        val time = (now - start) / 1e9
        if (time < 0 || time > 1.2) return@Canvas
        val fade = minOf(1.0, (1.2 - time) / 0.4).toFloat()
        val origin = originY.toPx()
        for (index in 0 until 26) {
            val angle = -Math.PI / 2 + (unit(index, 1.0) - 0.5) * 2.6
            val speed = (150 + 230 * unit(index, 2.0)) * density
            val dx = cos(angle) * speed * time
            val dy = sin(angle) * speed * time + 0.5 * 560 * density * time * time
            val side = ((5 + 4 * unit(index, 3.0)) * density).toFloat()
            val spin = ((unit(index, 4.0) - 0.5) * 14 * time * 180 / Math.PI).toFloat()
            val color = colors[index % colors.size].copy(alpha = fade)
            translate(size.width / 2 + dx.toFloat(), origin + dy.toFloat()) {
                rotate(spin, pivot = Offset.Zero) {
                    if (index % 2 == 0) {
                        drawCircle(color, side / 2, Offset.Zero)
                    } else {
                        drawRoundRect(color, Offset(-side / 2, -side * 0.3f), Size(side, side * 0.6f), CornerRadius(1.5f * density))
                    }
                }
            }
        }
    }
}
