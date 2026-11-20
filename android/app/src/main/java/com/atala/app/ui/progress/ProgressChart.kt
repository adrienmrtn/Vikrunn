package com.atala.app.ui.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import java.time.Instant

private val pastColor = Color(0xFF8DB8A8)
private val chartHeight = 120.dp

/**
 * Weekly distance of the last weeks (ProgressTabChart.swift): one bar per week, the current one
 * highlighted, the planned distance of the plan weeks as a light track behind, and a dashed line
 * at the average. Drawn on a Canvas; a tap on a bar shows its week.
 */
@Composable
internal fun ProgressChart(weeks: List<ProgressWeek>, showsPlanned: Boolean, revealed: Boolean, reduceMotion: Boolean) {
    val hasDistance = weeks.any { it.km > 0 }
    // Average of the weeks before the current one, which is still under way.
    val past = weeks.filter { !it.isCurrent }
    val average = if (past.isEmpty()) 0.0 else past.sumOf { it.km } / past.size
    val top = maxOf(1.0, maxOf(weeks.maxOfOrNull { it.km } ?: 0.0, if (showsPlanned) weeks.maxOfOrNull { it.plannedKm } ?: 0.0 else 0.0))
    var selected by remember { mutableStateOf<Instant?>(null) }
    val trailing = if (hasDistance && average > 0) L("Average %@ km", ProgressStats.number(average)) else null

    ProgressSection(L("Weekly distance"), trailing) {
        Column(Modifier.progressCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!hasDistance) {
                ProgressHint(Icons.Filled.BarChart, L("Your weekly distance builds up here, one bar per week."))
                return@Column
            }
            val shown = weeks.firstOrNull { it.monday == selected } ?: weeks.lastOrNull()
            if (shown != null) {
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom) {
                    Text(ProgressStats.number(shown.km), style = Theme.font(28, FontWeight.Bold), color = Theme.ink)
                    Text(" km", style = Theme.font(15, FontWeight.SemiBold), color = Theme.muted,
                        modifier = Modifier.weight(1f))
                    Text(
                        if (shown.isCurrent) L("This week") else L("Week of %@", Fmt.day(shown.monday, "d MMM")),
                        style = Theme.font(13), color = Theme.muted, maxLines = 1,
                    )
                }
            }
            Bars(weeks, showsPlanned, top, average, selected, revealed || reduceMotion, reduceMotion) { week ->
                selected = if (selected == week.monday) null else week.monday
            }
            Row {
                weeks.firstOrNull()?.let { Text(Fmt.day(it.monday, "d MMM"), style = Theme.font(11), color = Theme.muted) }
                Spacer(Modifier.weight(1f))
                Text(L("This week"), style = Theme.font(11, FontWeight.SemiBold), color = Theme.ink)
            }
            if (showsPlanned) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendItem(Theme.greenTint, L("Planned"))
                    LegendItem(Theme.green, L("Done"))
                }
            }
        }
    }
}

@Composable
private fun Bars(
    weeks: List<ProgressWeek>,
    showsPlanned: Boolean,
    top: Double,
    average: Double,
    selected: Instant?,
    grown: Boolean,
    reduceMotion: Boolean,
    onTap: (ProgressWeek) -> Unit,
) {
    val haptics = rememberHaptics()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // One clock drives every bar: each starts 30 ms after the one before and grows for 550 ms.
    val clock = remember { Animatable(if (grown && reduceMotion) 2f else 0f) }
    LaunchedEffect(grown) {
        if (!grown) return@LaunchedEffect
        if (reduceMotion) clock.snapTo(2f) else clock.animateTo(2f, tween(2000, easing = LinearEasing))
    }
    val description = weeks.joinToString(". ") { week ->
        val date = Fmt.day(week.monday, "d MMMM")
        if (showsPlanned && week.plannedKm > 0) {
            L("Week of %@: %@ of %@ kilometres", date, ProgressStats.number(week.km), ProgressStats.number(week.plannedKm))
        } else {
            L("Week of %@: %@ kilometres", date, ProgressStats.number(week.km))
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(chartHeight)
            .semantics { contentDescription = description }
            .pointerInput(weeks) {
                detectTapGestures { offset ->
                    if (weeks.isEmpty()) return@detectTapGestures
                    val slot = size.width.toFloat() / weeks.size
                    var index = (offset.x / slot).toInt().coerceIn(0, weeks.size - 1)
                    if (rtl) index = weeks.size - 1 - index
                    haptics.select()
                    onTap(weeks[index])
                }
            },
    ) {
        if (weeks.isEmpty()) return@Canvas
        val spacing = 6.dp.toPx()
        val count = weeks.size
        val barWidth = (size.width - spacing * (count - 1)) / count
        val radius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        val minBar = 4.dp.toPx()
        val elapsedMs = clock.value * 1000
        weeks.forEachIndexed { index, week ->
            val slot = if (rtl) count - 1 - index else index
            val x = slot * (barWidth + spacing)
            if (showsPlanned && week.plannedKm > 0) {
                val h = maxOf(minBar, size.height * (week.plannedKm / top).toFloat())
                drawRoundRect(Theme.greenTint, Offset(x, size.height - h), Size(barWidth, h), radius)
            }
            if (week.km > 0) {
                val full = maxOf(minBar, size.height * (week.km / top).toFloat())
                val t = ((elapsedMs - 200 - index * 30) / 550f).coerceIn(0f, 1f)
                // Ease out, like the spring of the iPhone.
                val eased = 1 - (1 - t) * (1 - t) * (1 - t)
                val h = full * eased
                if (h > 0) {
                    val highlighted = week.isCurrent || week.monday == selected
                    drawRoundRect(if (highlighted) Theme.green else pastColor, Offset(x, size.height - h), Size(barWidth, h), radius)
                }
            }
        }
        if (average > 0) {
            val y = size.height - size.height * minOf(average / top, 1.0).toFloat()
            drawLine(
                Theme.ink.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(title, style = Theme.font(12), color = Theme.muted)
    }
}
