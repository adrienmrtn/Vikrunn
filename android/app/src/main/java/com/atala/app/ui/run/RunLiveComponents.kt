package com.atala.app.ui.run

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.core.model.WorkoutStep
import com.atala.app.core.run.RunFormat
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme

/** One segment per step of the session, coloured by kind. The current one is taller. */
@Composable
fun RunSegmentBar(steps: List<WorkoutStep>, currentIndex: Int, complete: Boolean, preview: Boolean = false) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(10.dp).clearAndSetSemantics {}) {
        val spacing = (if (steps.size > 24) 1 else 3).dp.toPx()
        val total = steps.sumOf { RunFormat.weight(it).toDouble() }.toFloat()
        val available = maxOf(0f, size.width - spacing * maxOf(0, steps.size - 1))
        var x = 0f
        steps.forEachIndexed { index, step ->
            val isCurrent = !preview && !complete && index == currentIndex
            val isDone = !preview && (complete || index < currentIndex)
            val width = if (total > 0) maxOf(2.dp.toPx(), available * RunFormat.weight(step) / total) else 2.dp.toPx()
            val height = (if (isCurrent) 10 else 6).dp.toPx()
            val color = if (preview || isCurrent || isDone) RunColors.segment(step.kind) else Color(0xFFD9D9D6)
            val left = if (rtl) size.width - x - width else x
            drawRoundRect(
                color = color.copy(alpha = if (isDone) 0.45f else 1f),
                topLeft = Offset(left, (size.height - height) / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(height / 2, height / 2),
            )
            x += width + spacing
        }
    }
}

/** Current pace against the target range: green zone in the middle, black marker for the runner. */
@Composable
fun RunPaceGauge(pace: Int, paceFast: Int, paceSlow: Int, showsLabels: Boolean = true) {
    val position = if (pace <= 0 || paceSlow <= paceFast) 0.5f else {
        val ratio = (paceSlow - pace).toFloat() / (paceSlow - paceFast)
        (0.35f + ratio * 0.30f).coerceIn(0.02f, 0.98f)
    }
    val animated by animateFloatAsState(position, tween(600), label = "gauge")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val label = L("Pace %@, target %@ to %@", Fmt.pace(pace), Fmt.pace(paceSlow), Fmt.pace(paceFast))
    Column(Modifier.clearAndSetSemantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val bar = 10.dp.toPx()
            val top = (size.height - bar) / 2
            drawRoundRect(Theme.cardBorder, Offset(0f, top), Size(size.width, bar), CornerRadius(bar / 2))
            drawRoundRect(Theme.greenSoft, Offset(size.width * 0.35f, top), Size(size.width * 0.30f, bar), CornerRadius(bar / 2))
            if (pace > 0) {
                val marker = 6.dp.toPx()
                val x = if (rtl) size.width * (1 - animated) else size.width * animated
                drawRoundRect(Theme.ink, Offset(x - marker / 2, 0f), Size(marker, size.height), CornerRadius(marker / 2))
            }
        }
        if (showsLabels) {
            Row(Modifier.fillMaxWidth()) {
                Text(L("Too slow"), style = Theme.font(12), color = Theme.muted)
                Spacer(Modifier.weight(1f))
                Text(L("%@ to %@", Fmt.pace(paceSlow), Fmt.pace(paceFast)), style = Theme.font(12), color = Theme.muted)
                Spacer(Modifier.weight(1f))
                Text(L("Too fast"), style = Theme.font(12), color = Theme.muted)
            }
        }
    }
}

/** The coach's last words: avatar and a white speech bubble. */
@Composable
fun RunCoachBubble(coach: Coach, text: String, showsSpeaker: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        CoachAvatar(coach, size = 36.dp)
        Row(
            Modifier
                .weight(1f, fill = false)
                .shadow(5.dp, RunBubbleShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
                .background(Color.White, RunBubbleShape)
                .border(1.dp, Theme.cardBorder, RunBubbleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showsSpeaker) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Theme.green, modifier = Modifier.size(16.dp))
            }
            Text(text, style = Theme.font(15).copy(lineHeight = 20.sp), color = Theme.ink)
        }
    }
}

/** Round white control (lock, skip). */
@Composable
fun RunRoundButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .size(56.dp)
            .background(Color.White, CircleShape)
            .border(1.dp, Theme.controlBorder, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Theme.ink, modifier = Modifier.size(22.dp))
    }
}

/** Covers the live screen so nothing is pressed by accident. A long press unlocks. */
@Composable
fun RunLockOverlay(onUnlock: () -> Unit) {
    var pressing by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressing) 1.12f else 1f, tween(200), label = "lock")
    val label = L("Unlock the screen")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .scale(scale)
                    .background(Color.White, CircleShape)
                    .semantics {
                        contentDescription = label
                        onClick(label) { onUnlock(); true }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressing = true
                                val start = System.currentTimeMillis()
                                val released = tryAwaitRelease()
                                pressing = false
                                if (released && System.currentTimeMillis() - start >= 800) onUnlock()
                            },
                            onLongPress = {
                                pressing = false
                                onUnlock()
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, null, tint = Theme.ink, modifier = Modifier.size(28.dp))
            }
            Text(L("Screen locked. Hold to unlock."), style = Theme.font(15, FontWeight.SemiBold), color = Color.White)
        }
    }
}

/**
 * The step being run, white on black so it reads in full sunlight: what is left of it, a bar that
 * fills as it goes, and what comes next.
 */
@Composable
fun RunStepCard(
    caption: String,
    value: String,
    unit: String?,
    total: String,
    progress: Double,
    next: String,
    position: String,
    fast: Boolean,
) {
    val fraction by animateFloatAsState(progress.coerceIn(0.0, 1.0).toFloat(), tween(1000, easing = LinearEasing), label = "step")
    val accent = if (fast) Theme.orange else Theme.greenOnDark
    val faded = Color.White.copy(alpha = 0.78f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.ink, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FitText(caption, Theme.font(14, FontWeight.SemiBold), faded, Modifier.weight(1f), minScale = 0.8f)
            Spacer(Modifier.width(8.dp))
            Text(position, style = Theme.font(14, FontWeight.SemiBold), color = faded, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FitText(value, Theme.font(58, FontWeight.Bold).digits().tracking(-1.5f), Color.White, Modifier.weight(1f, fill = false))
                if (unit != null) {
                    Text(unit, style = Theme.font(22, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            FitText(total, Theme.font(15), faded, Modifier.padding(bottom = 12.dp).widthIn(max = 160.dp), minScale = 0.7f)
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            drawRoundRect(Color.White.copy(alpha = 0.22f), cornerRadius = CornerRadius(size.height / 2))
            val width = maxOf(8.dp.toPx(), size.width * fraction)
            drawRoundRect(accent, Offset(if (rtl) size.width - width else 0f, 0f), Size(width, size.height), CornerRadius(size.height / 2))
        }
        FitText(next, Theme.font(16, FontWeight.SemiBold), Color.White, minScale = 0.7f)
    }
}

/** One of the big figures of the live and pause screens: the label on top, a very large value. */
@Composable
fun RunLiveFigure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    size: Int = 46,
    centered: Boolean = false,
    labelColor: Color = Theme.muted,
) {
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(label, style = Theme.font(14, FontWeight.SemiBold), color = labelColor, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FitText(
                value,
                Theme.font(size, FontWeight.Bold).digits().tracking(if (size >= 70) -2.5f else -1f),
                Theme.ink,
                Modifier.weight(1f, fill = false),
                textAlign = if (centered) TextAlign.Center else null,
            )
            if (unit != null) {
                Text(
                    unit,
                    style = Theme.font(maxOf(16, (size * 0.3f).toInt()), FontWeight.Bold),
                    color = Theme.ink,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = (size * 0.12f).dp),
                )
            }
        }
    }
}

/** Three small figures side by side (total time, distance, average pace). */
@Composable
fun RunStatsRow(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (value, label) ->
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FitText(value, Theme.font(24, FontWeight.Bold).digits(), Theme.ink, minScale = 0.6f)
                Text(label, style = Theme.font(12), color = Theme.muted)
            }
        }
    }
}

/** A 44 dp tap target for a plain text action ("Settings", "Skip straight to the cool-down"). */
@Composable
fun RunTextButton(title: String, color: Color = Theme.ink, size: Int = 14, minHeight: Dp = 44.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        modifier
            .height(minHeight)
            .clickable(role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = Theme.font(size, FontWeight.SemiBold), color = color)
    }
}
