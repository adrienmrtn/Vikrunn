package com.atala.app.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.core.model.CoachPose
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.CoachPoseImage
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Geometry of the coach carousel, computed from the container size only (`OnboardingCoachCarouselMetrics`). */
private class CoachCarouselMetrics(width: Dp, height: Dp, fontScale: Float) {
    val figureWidth: Dp
    val figureHeight: Dp
    /** Distance between the centres of two coaches. */
    val spacing: Dp
    /** Room for the name and the trait under the picture: taller with a larger system font. */
    val labelHeight: Dp = (LABEL_HEIGHT * fontScale.coerceAtLeast(1f)).dp

    init {
        val byHeight = max(140f, height.value - labelHeight.value - 8)
        val byWidth = width.value * 0.62f / ASPECT
        val h = min(byHeight, byWidth)
        val w = h * ASPECT
        figureHeight = h.dp
        figureWidth = w.dp
        // The neighbour's outer edge stays 6 dp inside the screen.
        spacing = max(60f, width.value / 2 - 6 - w * NEIGHBOUR_SCALE / 2).dp
    }

    companion object {
        /** Scale of the coach on each side of the selected one. */
        const val NEIGHBOUR_SCALE = 0.55f
        const val LABEL_HEIGHT = 54f
        /** Width / height of the room given to a coach picture. */
        const val ASPECT = 0.628f
    }
}

/** The carousel's spring (response 0.42, damping 0.86). */
private fun coachSnap() = onboardingSpring<Float>(0.42, 0.86)

/**
 * The coaches on one line (`OnboardingCoachCarouselView`): the selected one centred and large,
 * one smaller, faded neighbour on each side. [position] is the continuous place of the line
 * (the index of the coach in the middle), so the drag and the snap move the same value.
 */
@Composable
private fun CoachCarousel(index: Int, position: Animatable<Float, *>, onIndex: (Int) -> Unit, modifier: Modifier = Modifier) {
    val coaches = Coach.all
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val metrics = CoachCarouselMetrics(maxWidth, maxHeight, density.fontScale)
        val spacingPx = with(density) { metrics.spacing.toPx() }

        fun select(target: Int) {
            if (target == index || target !in coaches.indices) return
            haptics.select()
            onIndex(target)
            scope.launch { position.animateTo(target.toFloat(), coachSnap()) }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(spacingPx, index, rtl) {
                    val tracker = VelocityTracker()
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            drag = 0f
                            tracker.resetTracking()
                        },
                        onDragCancel = {
                            scope.launch { position.animateTo(index.toFloat(), coachSnap()) }
                        },
                        onDragEnd = {
                            // Positive when the line moves towards the start edge (next coach).
                            val velocity = tracker.calculateVelocity().x * if (rtl) 1 else -1
                            val moved = -drag
                            // About the distance a flick would carry the line (predictedEndTranslation).
                            val predicted = moved + velocity * 0.2f
                            var target = index
                            if (moved > spacingPx * 0.5f || predicted > spacingPx * 0.45f) {
                                target += 1
                            } else if (moved < -spacingPx * 0.5f || predicted < -spacingPx * 0.45f) {
                                target -= 1
                            }
                            target = target.coerceIn(0, coaches.lastIndex)
                            // One tick per coach change, none when the line snaps back.
                            if (target != index) {
                                haptics.select()
                                onIndex(target)
                            }
                            scope.launch { position.animateTo(target.toFloat(), coachSnap()) }
                        },
                    ) { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        drag += if (rtl) -amount else amount
                        var pull = (drag / spacingPx).coerceIn(-1f, 1f)
                        val atStart = index == 0 && pull > 0
                        val atEnd = index == coaches.lastIndex && pull < 0
                        if (atStart || atEnd) pull *= 0.3f
                        scope.launch { position.snapTo(index - pull) }
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            coaches.forEachIndexed { place, coach ->
                val offset = place - position.value
                // 0 when centred, 1 for the direct neighbour, 2 and more further away.
                val distance = abs(offset)
                val near = min(1f, distance)
                val scale = 1f - (1f - CoachCarouselMetrics.NEIGHBOUR_SCALE) * near
                val figureAlpha = if (distance <= 1f) 1f - 0.55f * distance else max(0f, 0.45f * (2f - distance))
                val reachable = abs(place - index) <= 1
                Column(
                    Modifier
                        .width(metrics.figureWidth)
                        .offset { IntOffset((offset * spacingPx).roundToInt(), 0) }
                        .zIndex(1f - near)
                        .then(
                            if (reachable) {
                                Modifier
                                    .semantics {
                                        contentDescription = "${coach.name}, ${coach.trait}"
                                        selected = place == index
                                    }
                                    .clickable(interactionSource = null, indication = null, role = Role.Button) { select(place) }
                            } else {
                                Modifier.clearAndSetSemantics {}
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CoachPoseImage(
                        coach,
                        pose = CoachPose.Wave,
                        modifier = Modifier
                            .width(metrics.figureWidth)
                            .height(metrics.figureHeight)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = figureAlpha
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .clearAndSetSemantics {},
                    )
                    Column(
                        Modifier
                            .height(metrics.labelHeight - 8.dp)
                            .alpha(1f - near)
                            .clearAndSetSemantics {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // Never cut: the line may run wider than the picture, as on the iPhone (`fixedSize`).
                        Text(
                            coach.name,
                            style = Theme.font(22, FontWeight.Bold),
                            color = Theme.ink,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                        Text(
                            coach.trait,
                            style = Theme.font(15),
                            color = Theme.muted,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
    }
}

/** Screen 3 (step `Coach`): swipe through the coaches, written to `answers.coachId`. */
@Composable
fun OnboardingCoachScreen() {
    val store = LocalAppStore.current
    val nav = LocalOnboardingNavigator.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val coaches = Coach.all
    val initial = remember { coaches.indexOfFirst { it.id == store.current.answers.coachId }.coerceAtLeast(0) }
    var index by remember { mutableIntStateOf(initial) }
    val position = remember { Animatable(initial.toFloat()) }
    val current = coaches[index.coerceIn(0, coaches.lastIndex)]

    // The centred coach is the chosen one as soon as it is there.
    LaunchedEffect(index) {
        val id = coaches.getOrNull(index)?.id ?: return@LaunchedEffect
        if (id != store.current.answers.coachId) store.updateAnswers { it.copy(coachId = id) }
    }

    fun move(delta: Int) {
        val target = index + delta
        if (target !in coaches.indices) return
        haptics.select()
        index = target
        scope.launch { position.animateTo(target.toFloat(), coachSnap()) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        OnboardingHeaderSpace()
        Text(
            L("First, let's choose your coach"),
            style = Theme.title(28),
            color = Theme.ink,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 10.dp)
                .semantics { heading() },
        )
        CoachCarousel(
            index = index,
            position = position,
            onIndex = { index = it },
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowButton(Icons.AutoMirrored.Filled.ArrowBackIos, L("Previous coach"), enabled = index > 0) { move(-1) }
            OnboardingFitText(L("Swipe to change coach"), Theme.font(13), Theme.muted, Modifier.weight(1f, fill = false), minScale = 0.75f)
            ArrowButton(Icons.AutoMirrored.Filled.ArrowForwardIos, L("Next coach"), enabled = index < coaches.lastIndex) { move(1) }
        }
        BottomBar(Modifier.navigationBarsPadding()) {
            PrimaryButton(
                L("Choose %@", current.name),
                onClick = {
                    store.updateAnswers { it.copy(coachId = current.id) }
                    nav.next()
                },
            )
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(Color.White, CircleShape)
            .border(1.dp, Theme.controlBorder, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Theme.ink else Theme.faint, modifier = Modifier.size(15.dp))
    }
}
