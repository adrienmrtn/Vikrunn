package com.atala.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Experience
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.RecentResult
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.plan.PlanEngine
import com.atala.app.core.store.AppStore
import com.atala.app.ui.components.ChoiceDialog
import com.atala.app.ui.components.DialogChoice
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

// MARK: - Rebuilding the plan (MainPlanRenewal.swift)

/**
 * Rebuilds the plan from the current answers, from this week on, and keeps what was already done
 * coherent: earlier days of the week are not recreated, sessions done since Monday stay in the week
 * (or tick the new session of the same day), and runs that pointed to a session that no longer
 * exists become plain runs of the history.
 */
internal fun AppStore.mainRebuildPlan() {
    val today = PlanCalendar.startOfDay(Instant.now())
    val previous = current.allWorkouts

    // A race date that is behind us cannot drive a new plan.
    val raceDate = current.answers.raceDate
    if (current.answers.hasRace && raceDate != null && PlanCalendar.startOfDay(raceDate) < today) {
        updateAnswers { it.copy(hasRace = false, raceDate = null) }
    }

    generatePlan()
    val rebuilt = current.plan ?: return

    val firstDay = PlanCalendar.startOfDay(rebuilt.startDate)
    val done = previous.filter { it.status == WorkoutStatus.Done && it.date >= firstDay }
    // Run id -> id of the new session it now belongs to.
    val runLinks = mutableMapOf<UUID, UUID>()
    val weeks = rebuilt.weeks.map { it.workouts.toMutableList() }.toMutableList()
    for (old in done) {
        val w = rebuilt.weeks.indexOfFirst { week ->
            val start = PlanCalendar.startOfDay(week.startDate)
            old.date >= start && old.date < PlanCalendar.addDays(start, 7)
        }
        if (w < 0) continue
        val sessions = weeks[w]
        val match = sessions.indexOfFirst { candidate ->
            candidate.status == WorkoutStatus.Planned && PlanCalendar.isSameDay(candidate.date, old.date) &&
                (if (old.kind.isRun) candidate.kind.isRun else candidate.kind == old.kind)
        }
        if (match >= 0) {
            sessions[match] = sessions[match].copy(status = WorkoutStatus.Done, runId = old.runId)
            old.runId?.let { runLinks[it] = sessions[match].id }
        } else {
            sessions += old.copy(weekIndex = rebuilt.weeks[w].index)
            sessions.sortBy { it.date }
        }
    }
    val plan = rebuilt.copy(weeks = rebuilt.weeks.mapIndexed { index, week -> week.copy(workouts = weeks[index]) })
    val validIds = plan.allWorkouts.map { it.id }.toSet()
    update { state ->
        val runs = state.runs.map { run ->
            val newId = runLinks[run.id]
            when {
                newId != null -> run.copy(workoutId = newId)
                run.workoutId != null && run.workoutId !in validIds -> run.copy(workoutId = null)
                else -> run
            }
        }
        state.copy(plan = plan, runs = runs)
    }
    refreshMissed()
}

/**
 * Starts the plan that follows a finished one: same goal and days, with the level updated from
 * what was actually run (race result, recent weekly volume).
 */
internal fun AppStore.mainBuildNextPlan() {
    val state = current
    val today = PlanCalendar.startOfDay(Instant.now())
    var updated = state.answers
    val plan = state.plan
    if (plan != null) {
        // The race that was just run is the best measure of today's level.
        val km = plan.goal.distanceKm
        val race = plan.allWorkouts.lastOrNull { it.kind == WorkoutKind.Race && it.status == WorkoutStatus.Done }
        val record = state.run(race?.runId)
        if (km != null && record != null && record.durationSec > 0 &&
            record.distanceKm >= km * 0.97 && record.distanceKm <= km * 1.1
        ) {
            val seconds = (record.durationSec / record.distanceKm * km).roundToInt()
            updated = updated.copy(recentResult = RecentResult(distanceKm = km, seconds = seconds))
        }
        // Weekly volume really run over the last weeks of the plan. Never lowered here.
        val lastWeeks = plan.weeks.takeLast(3)
        if (lastWeeks.isNotEmpty()) {
            val average = lastWeeks.sumOf { state.mainDoneKm(it) } / lastWeeks.size
            if (average >= 25) {
                updated = updated.copy(weeklyVolume = WeeklyVolume.Over25)
            } else if (average >= 10 && (updated.weeklyVolume ?: WeeklyVolume.Under10) in
                listOf(WeeklyVolume.NotYet, WeeklyVolume.Under10)
            ) {
                updated = updated.copy(weeklyVolume = WeeklyVolume.From10To25)
            }
        }
        // Someone who completed a plan is no longer "just starting".
        val runsDone = plan.allWorkouts.count { it.kind.isRun && it.status == WorkoutStatus.Done }
        if (runsDone >= 8 && (updated.experience ?: Experience.Starting) == Experience.Starting) {
            updated = updated.copy(experience = Experience.UnderSixMonths)
        }
    }
    val raceDate = updated.raceDate
    if (updated.hasRace && raceDate != null && PlanCalendar.startOfDay(raceDate) <= today) {
        updated = updated.copy(hasRace = false, raceDate = null)
    }
    val answers = updated
    updateAnswers { answers }
    mainRebuildPlan()
}

// MARK: - "Build my next plan"

/** Primary button shown when the plan is over. Asks before replacing it. */
@Composable
internal fun MainNextPlanButton(onOpenProfile: () -> Unit) {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    var confirm by rememberSaveable { mutableStateOf(false) }
    PrimaryButton(L("Build my next plan"), onClick = { confirm = true })
    if (confirm) {
        ChoiceDialog(
            title = L("Build your next plan?"),
            message = L(
                "A new plan of up to %@ weeks starts this week, from your level today. Your runs stay in your history.",
                PlanEngine.MAX_WEEKS,
            ),
            choices = listOf(
                DialogChoice(L("Build it with the same goal")) {
                    haptics.play(HapticKind.Success)
                    store.mainBuildNextPlan()
                },
                DialogChoice(L("Change my goal first")) { onOpenProfile() },
            ),
            onDismiss = { confirm = false },
        )
    }
}

/** Glass card for a finished plan, used on Today and Plan. */
@Composable
internal fun MainPlanEndedCard(onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    val state by collectAppState()
    val workouts = state.allWorkouts
    val raceWasRun = workouts.any { it.kind == WorkoutKind.Race && it.status == WorkoutStatus.Done }
    val done = workouts.count { it.status == WorkoutStatus.Done }
    val total = workouts.size
    Column(
        modifier
            .fillMaxWidth()
            .mainGlass(22.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (raceWasRun) L("Race done. Well run.") else L("Plan complete"),
            style = Theme.font(24, FontWeight.Bold).copy(letterSpacing = (-0.5).sp),
            color = Theme.ink,
        )
        Text(
            if (raceWasRun) {
                L("You did %@ of the %@ sessions of this plan. Take a few easy days, then start again whenever you are ready.", done, total)
            } else {
                L("You did %@ of the %@ sessions of this plan. Your next plan starts from your level today.", done, total)
            },
            style = Theme.font(14).copy(lineHeight = lineHeight(14, 4)),
            color = Theme.body,
        )
        MainNextPlanButton(onOpenProfile)
    }
}
