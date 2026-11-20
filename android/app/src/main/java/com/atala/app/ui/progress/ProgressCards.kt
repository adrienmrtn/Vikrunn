package com.atala.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSource
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import java.util.UUID

// Goal

/**
 * The goal: the runner's level today on the goal distance, how far the goal is, and the paces the
 * plan works from (ProgressTabGoalCard.swift).
 */
@Composable
internal fun ProgressGoalCard(goal: ProgressGoal?) {
    ProgressSection(L("Your goal"), goal?.title) {
        Column(Modifier.progressCard()) {
            if (goal == null) {
                ProgressHint(Icons.Filled.SportsScore, L("Build your plan to see where you stand today and where it can take you."))
                return@Column
            }
            Text(L("Your level today"), style = Theme.font(13), color = Theme.muted)
            val current = goal.currentSeconds
            if (current != null) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.duration(current), style = Theme.font(40, FontWeight.Bold), color = Theme.ink, maxLines = 1,
                        modifier = Modifier.weight(1f))
                    val target = goal.goalSeconds
                    if (target != null) {
                        val gap = current - target
                        Text(
                            if (gap <= 0) L("Goal within reach") else L("− %@ to the goal", Fmt.duration(gap)),
                            style = Theme.font(12, FontWeight.SemiBold),
                            color = Theme.green,
                            maxLines = 1,
                            modifier = Modifier.background(Theme.greenTint, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                goal.goalSeconds?.let { target ->
                    ProgressBar(
                        if (current > 0) target.toDouble() / current else 0.0,
                        L("Level compared with the goal"),
                        Modifier.padding(top = 10.dp),
                    )
                }
                Text(goal.footnote, style = Theme.font(13), color = Theme.muted, modifier = Modifier.padding(top = 10.dp))
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider(thickness = 1.dp, color = Theme.hairline)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProgressFigure(ProgressStats.oneDecimal(goal.vmaKmh), "VMA", Modifier.weight(1f), unit = "km/h", size = 18)
                        ProgressDivider()
                        ProgressFigure(Fmt.pace(goal.easyPace), L("Easy pace"), Modifier.weight(1f), unit = "/km", size = 18)
                    }
                }
            } else {
                Row(Modifier.padding(top = 2.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom) {
                    Text(ProgressStats.oneDecimal(goal.vmaKmh), style = Theme.font(40, FontWeight.Bold), color = Theme.ink)
                    Text(" km/h", style = Theme.font(17, FontWeight.SemiBold), color = Theme.muted, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    L("Estimated VMA · easy pace %@/km", Fmt.pace(goal.easyPace)),
                    style = Theme.font(13), color = Theme.muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

// Personal bests

private fun bestIcon(id: String): ImageVector = when (id) {
    "longest" -> Icons.Filled.SwapHoriz
    "pace" -> Icons.Filled.Bolt
    "time" -> Icons.Filled.Schedule
    else -> Icons.Filled.MilitaryTech
}

/** Personal bests, one row each, in one card. */
@Composable
internal fun ProgressBestsList(bests: List<ProgressBest>) {
    ProgressSection(L("Personal bests")) {
        Column(Modifier.progressCard(padding = 0.dp).padding(horizontal = 16.dp)) {
            if (bests.isEmpty()) {
                ProgressHint(Icons.Filled.MilitaryTech, L("Your bests will appear here after your first run."), Modifier.padding(vertical = 18.dp))
            }
            bests.forEachIndexed { index, best ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(bestIcon(best.id), size = 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(best.title, style = Theme.font(15, FontWeight.Medium), color = Theme.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(best.detail, style = Theme.font(12), color = Theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(best.value, style = Theme.font(17, FontWeight.Bold), color = Theme.ink, maxLines = 1)
                }
                if (index < bests.size - 1) HorizontalDivider(thickness = 1.dp, color = Theme.hairline)
            }
        }
    }
}

// History

private const val COLLAPSED_COUNT = 10

/** Run history (ProgressTabHistory.swift). A run opens its summary; the sample runs open nothing. */
@Composable
internal fun ProgressHistory(runs: List<RunRecord>, isSample: Boolean, onOpen: (UUID) -> Unit) {
    val haptics = rememberHaptics()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val sorted = runs.sortedByDescending { it.date }
    val visible = if (showAll) sorted else sorted.take(COLLAPSED_COUNT)
    val count = when {
        runs.isEmpty() -> null
        runs.size == 1 -> L("%@ run", runs.size)
        else -> L("%@ runs", runs.size)
    }
    ProgressSection(L("History"), count) {
        Column(Modifier.progressCard(padding = 0.dp)) {
            if (runs.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconTile(Icons.AutoMirrored.Filled.DirectionsRun, size = 48.dp)
                    Text(L("No runs yet"), style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink)
                    Text(L("Tap + to record your first run."), style = Theme.font(14), color = Theme.muted, textAlign = TextAlign.Center)
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    visible.forEachIndexed { index, run ->
                        RunRow(run, isLast = index == visible.size - 1, onClick = if (isSample) null else {
                            {
                                haptics.tap()
                                onOpen(run.id)
                            }
                        })
                    }
                }
            }
        }
        if (runs.size > COLLAPSED_COUNT) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button) {
                        haptics.tap()
                        showAll = !showAll
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (showAll) L("Show fewer") else L("Show all %@ runs", runs.size),
                    style = Theme.font(14, FontWeight.SemiBold), color = Theme.green,
                )
            }
        }
    }
}

private fun sourceIcon(source: RunSource): ImageVector = when (source) {
    RunSource.Gps -> Icons.Filled.NearMe
    RunSource.Manual -> Icons.Filled.Edit
    RunSource.Health -> Icons.Filled.Favorite
}

/**
 * One run of the history: the day, the title with time and pace, the source kept quiet, and the
 * distance as the figure that stands out.
 */
@Composable
private fun RunRow(run: RunRecord, isLast: Boolean, onClick: (() -> Unit)?) {
    val title = if (run.title.isEmpty()) L("Run") else L(run.title)
    val spoken = "$title, ${Fmt.day(run.date, "EEEE d MMMM")}, ${Fmt.km(run.distanceKm)}, " +
        "${Fmt.duration(run.durationSec)}, ${Fmt.pace(run.avgPace)}/km, ${ProgressStats.sourceTitle(run.source)}"
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.size(44.dp, 46.dp).background(Theme.surface, RoundedCornerShape(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(Fmt.day(run.date, "d"), style = Theme.font(17, FontWeight.Bold), color = Theme.ink, maxLines = 1)
                Text(Fmt.day(run.date, "MMM"), style = Theme.font(11, FontWeight.Medium), color = Theme.muted, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Fmt.duration(run.durationSec)} · ${Fmt.pace(run.avgPace)}/km", style = Theme.font(13), color = Theme.muted, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(sourceIcon(run.source), null, tint = Theme.faint, modifier = Modifier.size(10.dp))
                    Text(ProgressStats.sourceTitle(run.source), style = Theme.font(11), color = Theme.faint, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(ProgressStats.number(run.distanceKm), style = Theme.font(20, FontWeight.Bold), color = Theme.ink, maxLines = 1)
                Text(" km", style = Theme.font(12, FontWeight.SemiBold), color = Theme.muted, modifier = Modifier.padding(bottom = 3.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Theme.faint, modifier = Modifier.size(18.dp))
        }
        if (!isLast) HorizontalDivider(thickness = 1.dp, color = Theme.hairline)
    }
}
