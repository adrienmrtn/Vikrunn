package com.atala.app.ui.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Streak
import com.atala.app.core.model.StreakEngine
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.core.store.AppState
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.paywall.ProLocked
import com.atala.app.ui.paywall.rememberReduceMotion
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay

/** The staged entrance plays once per launch, not at every switch of tab. */
private object ProgressStaging {
    var played = false
}

/**
 * Progress tab (Atala/Features/Progress/ProgressTabView.swift): the period summary, streak and
 * consistency, weekly distance, the goal, bests and the run history. Part of Atala Pro: without it
 * the tab is shown blurred behind a padlock card, with sample figures when the runner has few runs.
 */
@Composable
fun ProgressScreen(nav: Navigation) {
    val state by collectAppState()
    val isPro by PurchaseService.isPro.collectAsStateWithLifecycle()
    val reduceMotion = rememberReduceMotion()
    var period by rememberSaveable { mutableStateOf(ProgressPeriod.Week) }
    var revealed by remember { mutableStateOf(ProgressStaging.played) }
    LaunchedEffect(Unit) {
        if (revealed) return@LaunchedEffect
        // Let the screen settle before the first block moves.
        delay(120)
        ProgressStaging.played = true
        revealed = true
    }

    // Sample figures are only ever drawn behind the blur of the locked tab, for a runner who has
    // little or nothing of their own yet. With Atala Pro the tab always shows the real data.
    val sample = !isPro && state.runs.size < 4
    val runs = if (sample) ProgressSample.runs else state.runs
    val streak = if (sample) StreakEngine.weekly(runs, emptyList()) else state.streak
    val goal = state.plan?.let(ProgressStats::goal) ?: if (sample) ProgressSample.goal else null
    val consistency = consistency(state, sample)
    val weeks = ProgressStats.weeks(runs, state.plan)

    Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
        Text(
            L("Progress"),
            style = Theme.title(34),
            color = Theme.ink,
            modifier = Modifier
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 8.dp, bottom = 6.dp)
                .semantics { heading() },
        )
        ProLocked(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = L("Unlock your progress"),
            message = L("See your weekly distance, your streak, your personal bests and how close you are to your goal."),
            onUnlock = nav.onOpenPaywall,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(Modifier.staged(0, revealed, reduceMotion)) {
                    ProgressHero(ProgressStats.summary(runs, period), period, runs.isNotEmpty(), onPeriod = { period = it })
                }
                Box(Modifier.staged(1, revealed, reduceMotion)) { StreakCard(streak, consistency) }
                Box(Modifier.staged(2, revealed, reduceMotion)) {
                    ProgressChart(weeks, showsPlanned = state.plan != null, revealed = revealed, reduceMotion = reduceMotion)
                }
                Box(Modifier.staged(3, revealed, reduceMotion)) { ProgressGoalCard(goal) }
                Box(Modifier.staged(4, revealed, reduceMotion)) { ProgressBestsList(ProgressStats.bests(runs)) }
                Box(Modifier.staged(5, revealed, reduceMotion)) {
                    ProgressHistory(runs, isSample = sample, onOpen = nav.onOpenRunSummary)
                }
            }
        }
    }
}

private fun consistency(state: AppState, sample: Boolean): ProgressConsistency? {
    val plan = state.plan
    if (plan != null) {
        // Sessions whose day has passed (done or missed), plus the ones already done.
        val settled = plan.allWorkouts.filter { it.status != WorkoutStatus.Planned }
        if (settled.isNotEmpty() || !sample) {
            return ProgressConsistency(settled.count { it.status == WorkoutStatus.Done }, settled.size)
        }
    }
    return if (sample) ProgressConsistency(ProgressSample.SESSIONS_DONE, ProgressSample.SESSIONS_SETTLED) else null
}

/**
 * The summary of the week or the month: the distance in large, then runs, time and pace, and how
 * it compares with the period before.
 */
@Composable
private fun ProgressHero(summary: ProgressSummary, period: ProgressPeriod, hasRuns: Boolean, onPeriod: (ProgressPeriod) -> Unit) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Theme.forest.copy(alpha = 0.18f), spotColor = Theme.forest.copy(alpha = 0.18f))
            .background(Theme.forest, shape)
            .padding(20.dp),
    ) {
        Row(Modifier.background(Color.White.copy(alpha = 0.12f), CircleShape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ProgressPeriod.entries.forEach { option ->
                val selected = option == period
                val background by animateFloatAsState(if (selected) 1f else 0f, tween(250), label = "period")
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = background), CircleShape)
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Tab) {
                            haptics.select()
                            onPeriod(option)
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option.title, style = Theme.font(13, FontWeight.SemiBold),
                        color = if (selected) Theme.forest else Theme.greenOnDark, maxLines = 1)
                }
            }
        }
        Row(
            Modifier
                .padding(top = 18.dp)
                .clearAndSetSemantics { contentDescription = L("%@ kilometres", ProgressStats.number(summary.km)) },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(ProgressStats.oneDecimal(summary.km), style = Theme.font(60, FontWeight.Bold), color = Color.White, maxLines = 1)
            Text(" km", style = Theme.font(22, FontWeight.SemiBold), color = Theme.greenOnDark, modifier = Modifier.padding(bottom = 10.dp))
        }
        ChangeLine(summary, period, hasRuns, Modifier.padding(top = 2.dp))
        Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressFigure("${summary.runs}", if (summary.runs == 1) L("Run") else L("Runs"), Modifier.weight(1f),
                valueColor = Color.White, mutedColor = Theme.greenOnDark)
            ProgressFigure(Fmt.minutes(summary.seconds / 60), L("Time"), Modifier.weight(1f),
                valueColor = Color.White, mutedColor = Theme.greenOnDark)
            ProgressFigure(Fmt.pace(summary.pace), L("Average pace"), Modifier.weight(1f), unit = "/km",
                valueColor = Color.White, mutedColor = Theme.greenOnDark)
        }
    }
}

@Composable
private fun ChangeLine(summary: ProgressSummary, period: ProgressPeriod, hasRuns: Boolean, modifier: Modifier) {
    val isWeek = period == ProgressPeriod.Week
    val isAhead = summary.previousKm > 0 && summary.km >= summary.previousKm
    val icon = when {
        !hasRuns -> Icons.Filled.AddCircle
        summary.previousKm <= 0 -> Icons.Filled.CalendarMonth
        isAhead -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.Filled.Flag
    }
    val text = when {
        !hasRuns -> L("Tap + to record your first run.")
        summary.previousKm <= 0 -> if (isWeek) L("No runs last week to compare with") else L("No runs last month to compare with")
        isAhead -> {
            val percent = summary.changePercent ?: 0
            if (percent <= 0) {
                if (isWeek) L("Level with last week") else L("Level with last month")
            } else {
                val value = "$percent %"
                if (isWeek) L("%@ more than last week", value) else L("%@ more than last month", value)
            }
        }
        else -> {
            val gap = Fmt.km(summary.previousKm - summary.km)
            if (isWeek) L("%@ to match last week", gap) else L("%@ to match last month", gap)
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Theme.greenOnDark, modifier = Modifier.size(14.dp))
        Text(text, style = Theme.font(14, FontWeight.Medium), color = Theme.greenOnDark)
    }
}

/** The weekly streak, the best one and the share of sessions done, in one compact card. */
@Composable
private fun StreakCard(streak: Streak, consistency: ProgressConsistency?) {
    Column(Modifier.progressCard(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressFigure(StreakEngine.weeks(streak.current), L("Streak"), Modifier.weight(1f), size = 18,
                valueColor = if (streak.current > 0) Theme.orangeDeep else Theme.ink)
            ProgressDivider()
            ProgressFigure(StreakEngine.weeks(streak.best), L("Best streak"), Modifier.weight(1f), size = 18)
            if (consistency != null) {
                ProgressDivider()
                val percent = consistency.percent
                ProgressFigure(
                    percent?.toString() ?: "--", L("Sessions done"),
                    Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = L("%@ of %@ sessions done", consistency.done, consistency.settled)
                    },
                    unit = if (percent == null) null else "%", size = 18,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = if (streak.activeThisWeek) Theme.orange else Theme.faint,
                modifier = Modifier.size(16.dp))
            Text(StreakEngine.nudge(streak), style = Theme.font(14), color = Theme.body, modifier = Modifier.weight(1f))
        }
    }
}
