package com.atala.app.ui.run

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSplit
import com.atala.app.core.run.RunAnalysis
import com.atala.app.core.run.RunElevationPoint
import com.atala.app.core.run.RunFormat
import com.atala.app.core.run.RunKmSplit
import com.atala.app.core.run.RunPacePoint
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private class SummaryFigure(val icon: ImageVector, val label: String, val value: String, val unit: String?)

/**
 * The key figures of a run (RunSummaryFigures.swift): the distance in very large type, then a grid
 * with the time and the average pace (free), and with Atala Pro the elevation gain, the heart rate
 * and the fastest kilometre when the run has them.
 */
@Composable
fun RunSummaryFiguresCard(run: RunRecord, analysis: RunAnalysis, isPro: Boolean) {
    val figures = buildList {
        add(SummaryFigure(Icons.Filled.Timer, L("Time"), Fmt.duration(run.durationSec), null))
        add(SummaryFigure(Icons.Filled.Speed, L("Average pace"), Fmt.pace(run.avgPace), "/km"))
        if (isPro) {
            analysis.elevationGainM?.let { add(SummaryFigure(Icons.Filled.Terrain, L("Elevation gain"), "+$it", "m")) }
            run.avgHeartRate?.let { add(SummaryFigure(Icons.Filled.Favorite, L("Average heart rate"), "$it", "bpm")) }
            run.maxHeartRate?.let { add(SummaryFigure(Icons.Filled.MonitorHeart, L("Max heart rate"), "$it", "bpm")) }
            analysis.fastestSplit?.let { add(SummaryFigure(Icons.Filled.Bolt, L("Fastest kilometre"), Fmt.pace(it.pace), "/km")) }
        }
    }
    Column(Modifier.fillMaxWidth().card(radius = 24.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(L("Distance"), style = Theme.font(13, FontWeight.SemiBold), color = Theme.muted)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FitText(RunFormat.kmValue(run.distanceM), Theme.font(60, FontWeight.Bold).digits().tracking(-2f), Theme.ink, Modifier.weight(1f, fill = false))
                Text("km", style = Theme.font(22, FontWeight.Bold), color = Theme.green, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Theme.cardBorder))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            figures.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { FigureCell(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FigureCell(item: SummaryFigure, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, null, tint = Theme.green, modifier = Modifier.size(13.dp))
            FitText(item.label, Theme.font(13), Theme.muted, minScale = 0.7f)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            FitText(item.value, Theme.font(26, FontWeight.Bold).digits(), Theme.ink, Modifier.weight(1f, fill = false), minScale = 0.6f)
            item.unit?.let { Text(it, style = Theme.font(14, FontWeight.SemiBold), color = Theme.muted, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp)) }
        }
    }
}

/** Top of the summary for a run without a route: the title and the date on the brand green. */
@Composable
fun RunSummaryPlainHeader(icon: ImageVector, dateLine: String, title: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.forest, RoundedCornerShape(24.dp))
            .padding(20.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(46.dp).background(Theme.cream, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Theme.forest, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dateLine, style = Theme.font(13, FontWeight.SemiBold), color = Theme.greenOnDark)
            Text(title, style = Theme.title(28), color = Color.White)
        }
    }
}

/** What a free runner sees when the run has no splits to show blurred: a simple locked row. */
@Composable
fun RunSummaryLockedRow(onUnlock: () -> Unit) {
    val haptics = rememberHaptics()
    val label = L("Splits, charts and heart rate: see the detail of every run with Atala Pro")
    Row(
        Modifier
            .fillMaxWidth()
            .card(radius = 22.dp)
            .clickable(role = Role.Button) {
                haptics.tap()
                onUnlock()
            }
            .clearAndSetSemantics { contentDescription = label }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Filled.Lock, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(L("Splits, charts and heart rate"), style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
            Text(L("See the detail of every run with Atala Pro"), style = Theme.font(13), color = Theme.muted)
        }
        RunProTag()
    }
}

/** Bars for the fast repetitions: the faster the rep, the taller the bar (RunRepsCard). */
@Composable
fun RunRepsCard(splits: List<RunSplit>) {
    val paces = splits.map { it.pace }
    val fastest = paces.filter { it > 0 }.minOrNull() ?: 0
    val slowest = paces.maxOrNull() ?: 0
    val onTarget = splits.count { it.onTarget }
    val distances = splits.map { it.distanceM }
    val shortest = distances.minOrNull() ?: 0.0
    // Distance-based reps show their time; time-based reps show their pace.
    val showsTime = shortest > 0 && (distances.maxOrNull() ?: 0.0) / shortest < 1.08
    val metres = splits.sumOf { it.distanceM }
    val seconds = splits.sumOf { it.durationSec }
    val averagePace = if (metres > 0) (seconds / (metres / 1000)).toInt() else 0
    var footnote = if (showsTime) {
        L("Average %@, that is %@ per km.", Fmt.duration(seconds / maxOf(1, splits.size)), Fmt.pace(averagePace))
    } else {
        L("Average %@ per km.", Fmt.pace(averagePace))
    }
    val first = splits.firstOrNull()
    if (first?.paceFast != null && first.paceSlow != null) {
        footnote += " " + L("Target: %@ to %@.", Fmt.pace(first.paceFast), Fmt.pace(first.paceSlow))
    }
    val summary = L("%@ fast reps, %@ on target. Fastest pace %@ per kilometre.", splits.size, onTarget, Fmt.pace(fastest))
    Column(Modifier.fillMaxWidth().card(radius = 24.dp).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Your %@ fast reps", splits.size), style = Theme.font(17, FontWeight.Bold), color = Theme.ink, modifier = Modifier.weight(1f))
            Text(L("%@ of %@ on target", onTarget, splits.size), style = Theme.font(13, FontWeight.SemiBold),
                color = if (onTarget * 2 >= splits.size) Theme.green else Theme.orangeDeep)
        }
        Row(
            Modifier.fillMaxWidth().height(108.dp).clearAndSetSemantics { contentDescription = summary },
            horizontalArrangement = Arrangement.spacedBy(if (splits.size > 8) 4.dp else 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            splits.forEachIndexed { index, split ->
                val height = if (split.pace <= 0 || slowest <= fastest) 55f
                else 40f + 25f * ((slowest - split.pace).toFloat() / (slowest - fastest)).coerceIn(0f, 1f)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FitText(if (showsTime) Fmt.duration(split.durationSec) else Fmt.pace(split.pace),
                        Theme.font(12, FontWeight.SemiBold).digits(), Theme.ink, minScale = 0.6f)
                    Box(Modifier.fillMaxWidth().height(height.dp).background(if (split.pace == fastest) Theme.orangeDeep else Theme.orange, RoundedCornerShape(8.dp)))
                    Text("${index + 1}", style = Theme.font(12), color = Theme.muted)
                }
            }
        }
        Text(footnote, style = Theme.font(13), color = Theme.muted)
    }
}

/** Kilometre by kilometre, as a table with a bar per kilometre (RunSplitsCard). */
@Composable
fun RunSplitsCard(splits: List<RunKmSplit>, fastestId: Int?, averagePace: Int = 0) {
    val paces = splits.map { it.pace }.filter { it > 0 }
    val fastest = paces.minOrNull() ?: 0
    val slowest = paces.maxOrNull() ?: 0
    Column(Modifier.fillMaxWidth().card(radius = 24.dp).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Splits"), style = Theme.font(17, FontWeight.Bold), color = Theme.ink, modifier = Modifier.weight(1f))
            if (fastestId != null) Text(L("Fastest: km %@", fastestId), style = Theme.font(13, FontWeight.SemiBold), color = Theme.green)
        }
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val style = Theme.font(12, FontWeight.SemiBold)
            Text("km", style = style, color = Theme.muted, modifier = Modifier.width(30.dp))
            Spacer(Modifier.weight(1f))
            Text(L("Pace"), style = style, color = Theme.muted, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
            if (averagePace > 0) FitText(L("vs avg"), style, Theme.muted, Modifier.width(50.dp), minScale = 0.7f, textAlign = TextAlign.End)
        }
        Column {
            splits.forEach { split ->
                val label = if (split.isPartial) String.format(Locale.ROOT, "%.1f", split.distanceM / 1000) else "${split.id}"
                val isFastest = split.id == fastestId
                val isFaster = averagePace > 0 && split.pace in 1..averagePace
                val ratio = if (split.pace <= 0 || slowest <= fastest) 1f
                else 0.4f + 0.6f * ((slowest - split.pace).toFloat() / (slowest - fastest)).coerceIn(0f, 1f)
                val description = L("Kilometre %@, %@ per kilometre", label, Fmt.pace(split.pace))
                Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = Theme.font(14, FontWeight.SemiBold).digits(), color = Theme.muted, modifier = Modifier.width(30.dp))
                        Canvas(Modifier.weight(1f).height(10.dp)) {
                            val width = maxOf(8.dp.toPx(), size.width * ratio)
                            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                            drawRoundRect(if (isFastest) Theme.green else Theme.greenSoft.copy(alpha = 0.55f), Offset(left, 0f), Size(width, size.height), CornerRadius(size.height / 2))
                        }
                        Text(Fmt.pace(split.pace), style = Theme.font(15, if (isFastest) FontWeight.Bold else FontWeight.SemiBold).digits(),
                            color = Theme.ink, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
                        if (averagePace > 0) {
                            val difference = split.pace - averagePace
                            val gap = (if (difference < 0) "−" else "+") + String.format(Locale.ROOT, "%d:%02d", abs(difference) / 60, abs(difference) % 60)
                            FitText(if (split.pace > 0) gap else "", Theme.font(13, FontWeight.SemiBold).digits(),
                                if (isFaster) Theme.green else Theme.muted, Modifier.width(50.dp), minScale = 0.7f, textAlign = TextAlign.End)
                        }
                    }
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Theme.cardBorder))
                }
            }
        }
    }
}

/** A line over the width of the chart, x from 0 to 1, y from 0 (top) to 1 (bottom). */
private fun DrawScope.chartPath(points: List<Pair<Float, Float>>, closed: Boolean): Path {
    val rtl = layoutDirection == LayoutDirection.Rtl
    fun x(value: Float) = if (rtl) size.width * (1 - value) else size.width * value
    return Path().apply {
        points.forEachIndexed { index, (px, py) ->
            if (index == 0) moveTo(x(px), size.height * py) else lineTo(x(px), size.height * py)
        }
        if (closed) {
            lineTo(x(1f), size.height)
            lineTo(x(0f), size.height)
            close()
        }
    }
}

/** The pace along the run as a line: the higher, the faster. A dashed line marks the average. */
@Composable
fun RunPaceChartCard(points: List<RunPacePoint>, averagePace: Int) {
    val fastest = points.minOfOrNull { it.pace } ?: 0.0
    val slowest = points.maxOfOrNull { it.pace } ?: 0.0
    // A very even run still gets a visible band: at least thirty seconds from bottom to top.
    val span = maxOf(30.0, slowest - fastest)
    val top = fastest - (span - (slowest - fastest)) / 2
    fun height(pace: Double): Float = (0.92 - 0.84 * (1 - (pace - top) / span).coerceIn(0.0, 1.0)).toFloat()
    val first = points.firstOrNull()?.distanceM ?: 0.0
    val length = (points.lastOrNull()?.distanceM ?: 0.0) - first
    val line = if (points.size >= 2 && length > 0) points.map { ((it.distanceM - first) / length).toFloat() to height(it.pace) } else emptyList()
    val description = L("Pace along the run: average %@ per kilometre, fastest %@, slowest %@",
        Fmt.pace(averagePace), Fmt.pace(fastest.roundToInt()), Fmt.pace(slowest.roundToInt()))
    Column(
        Modifier.fillMaxWidth().card(radius = 24.dp).padding(horizontal = 18.dp, vertical = 16.dp).clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Pace"), style = Theme.font(17, FontWeight.Bold), color = Theme.ink, modifier = Modifier.weight(1f))
            Text(L("Average %@/km", Fmt.pace(averagePace)), style = Theme.font(13, FontWeight.SemiBold).digits(), color = Theme.green)
        }
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            if (line.isEmpty()) return@Canvas
            drawPath(chartPath(line, true), Brush.verticalGradient(listOf(Theme.green.copy(alpha = 0.30f), Theme.green.copy(alpha = 0.03f))))
            if (averagePace > 0) {
                val y = size.height * height(averagePace.toDouble())
                drawLine(Theme.faint, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }
            drawPath(chartPath(line, false), Theme.green, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row {
            Text(L("Fastest %@", Fmt.pace(fastest.roundToInt())), style = Theme.font(12).digits(), color = Theme.muted)
            Spacer(Modifier.weight(1f))
            Text(L("Slowest %@", Fmt.pace(slowest.roundToInt())), style = Theme.font(12).digits(), color = Theme.muted)
        }
    }
}

/** The ups and downs of the route, with the total climb. */
@Composable
fun RunElevationCard(points: List<RunElevationPoint>, gainM: Int) {
    val lowest = points.minOfOrNull { it.altitudeM } ?: 0.0
    val highest = points.maxOfOrNull { it.altitudeM } ?: 0.0
    // A flat run still gets a visible band: at least ten metres from bottom to top.
    val span = maxOf(10.0, highest - lowest)
    val base = lowest - (span - (highest - lowest)) / 2
    val last = points.lastOrNull()?.distanceM ?: 0.0
    val line = if (points.size >= 2 && last > 0) points.map { (it.distanceM / last).toFloat() to (0.92 - 0.84 * (it.altitudeM - base) / span).toFloat() } else emptyList()
    val description = L("Elevation: %@ metres of climb", gainM)
    Column(
        Modifier.fillMaxWidth().card(radius = 24.dp).padding(horizontal = 18.dp, vertical = 16.dp).clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Elevation"), style = Theme.font(17, FontWeight.Bold), color = Theme.ink, modifier = Modifier.weight(1f))
            Text("+$gainM m", style = Theme.font(15, FontWeight.Bold).digits(), color = Theme.orangeDeep)
        }
        Canvas(Modifier.fillMaxWidth().height(92.dp)) {
            if (line.isEmpty()) return@Canvas
            drawPath(chartPath(line, true), Brush.verticalGradient(listOf(Theme.orange.copy(alpha = 0.35f), Theme.orange.copy(alpha = 0.05f))))
            drawPath(chartPath(line, false), Theme.orange, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row {
            Text(L("Lowest %@ m", lowest.roundToInt()), style = Theme.font(12), color = Theme.muted)
            Spacer(Modifier.weight(1f))
            Text(L("Highest %@ m", highest.roundToInt()), style = Theme.font(12), color = Theme.muted)
        }
    }
}
