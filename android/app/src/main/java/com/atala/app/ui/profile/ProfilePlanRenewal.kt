package com.atala.app.ui.profile

import com.atala.app.core.model.Experience
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanWeek
import com.atala.app.core.model.RecentResult
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.store.AppState
import com.atala.app.core.store.AppStore
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/*
 * Rebuilding the plan from the profile (MainPlanRenewal.swift). The Today and Plan screens use the
 * same rules ("Build my next plan"); these copies keep the profile independent of them.
 */

/** Bounds of the target-time wheels, per race distance (OnboardingTimeRules in Swift). */
internal object ProfileTimeRules {
    fun range(km: Double): IntRange = when {
        km < 7.5 -> (12 * 60)..(70 * 60)
        km < 15 -> (25 * 60)..(140 * 60)
        else -> (60 * 60)..(215 * 60)
    }

    fun clamp(seconds: Int, km: Double): Int = seconds.coerceIn(range(km))

    fun defaultSeconds(km: Double): Int = when {
        km < 7.5 -> 30 * 60
        km < 15 -> 60 * 60
        else -> 130 * 60
    }
}

/** True when nothing is left to do: every session from today on is done (or there is none). */
internal val AppState.profilePlanEnded: Boolean
    get() {
        val plan = plan ?: return false
        val today = PlanCalendar.startOfDay(Instant.now())
        return plan.allWorkouts.none { it.status == WorkoutStatus.Planned && it.date >= today }
    }

/**
 * Rebuilds the plan from the current answers, from this week on, and keeps what was already done
 * coherent: earlier days of the week are not recreated, sessions done since Monday stay in the
 * week (or tick the new session of the same day), runs pointing to a session that no longer
 * exists become plain runs of the history.
 */
internal fun AppStore.profileRebuildPlan() {
    val today = PlanCalendar.startOfDay(Instant.now())
    val previous = current.plan?.allWorkouts.orEmpty()

    // A race date that is behind us cannot drive a new plan.
    val answers = current.answers
    val raceDate = answers.raceDate
    if (answers.hasRace && raceDate != null && PlanCalendar.startOfDay(raceDate) < today) {
        updateAnswers { it.copy(hasRace = false, raceDate = null) }
    }

    generatePlan()
    val rebuilt = current.plan ?: return
    val firstDay = PlanCalendar.startOfDay(rebuilt.startDate)
    val done = previous.filter { it.status == WorkoutStatus.Done && it.date >= firstDay }
    val weeks = rebuilt.weeks.map { it.workouts.toMutableList() }.toMutableList()
    /** Run id to the id of the new session it now belongs to. */
    val runLinks = mutableMapOf<UUID, UUID>()
    for (old in done) {
        val w = rebuilt.weeks.indexOfFirst { week ->
            val start = PlanCalendar.startOfDay(week.startDate)
            val end = PlanCalendar.addDays(start, 7)
            old.date >= start && old.date < end
        }
        if (w < 0) continue
        val workouts = weeks[w]
        val i = workouts.indexOfFirst { candidate ->
            candidate.status == WorkoutStatus.Planned && PlanCalendar.isSameDay(candidate.date, old.date) &&
                if (old.kind.isRun) candidate.kind.isRun else candidate.kind == old.kind
        }
        if (i >= 0) {
            workouts[i] = workouts[i].copy(status = WorkoutStatus.Done, runId = old.runId)
            old.runId?.let { runLinks[it] = workouts[i].id }
        } else {
            workouts.add(old.copy(weekIndex = rebuilt.weeks[w].index))
            workouts.sortBy { it.date }
        }
    }
    val plan = rebuilt.copy(weeks = rebuilt.weeks.mapIndexed { index, week -> week.copy(workouts = weeks[index]) })
    val validIds = plan.allWorkouts.map { it.id }.toSet()
    update { state ->
        var changed = false
        val runs = state.runs.map { run ->
            val newId = runLinks[run.id]
            when {
                newId != null && run.workoutId != newId -> run.copy(workoutId = newId).also { changed = true }
                newId == null && run.workoutId != null && run.workoutId !in validIds ->
                    run.copy(workoutId = null).also { changed = true }
                else -> run
            }
        }
        state.copy(plan = plan, runs = if (changed) runs else state.runs)
    }
    refreshMissed()
}

/**
 * Starts the plan that follows a finished one: same goal and days, with the level updated from
 * what was actually run (race result, recent weekly volume).
 */
internal fun AppStore.profileBuildNextPlan() {
    val today = PlanCalendar.startOfDay(Instant.now())
    val state = current
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
            val seconds = (record.durationSec.toDouble() / record.distanceKm * km).roundToInt()
            updated = updated.copy(recentResult = RecentResult(distanceKm = km, seconds = seconds))
        }
        // Weekly volume really run over the last weeks of the plan. Never lowered here.
        val lastWeeks = plan.weeks.takeLast(3)
        if (lastWeeks.isNotEmpty()) {
            val average = lastWeeks.sumOf { doneKm(state, it) } / lastWeeks.size
            if (average >= 25) {
                updated = updated.copy(weeklyVolume = WeeklyVolume.Over25)
            } else if (average >= 10 && (updated.weeklyVolume ?: WeeklyVolume.Under10) in listOf(WeeklyVolume.NotYet, WeeklyVolume.Under10)) {
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
    updateAnswers { updated }
    profileRebuildPlan()
}

/** Kilometres run during the days of a plan week (MainShared.swift `mainDoneKm`). */
private fun doneKm(state: AppState, week: PlanWeek): Double {
    val start = PlanCalendar.startOfDay(week.startDate)
    val end = PlanCalendar.addDays(start, 7)
    return state.runs.filter { it.date >= start && it.date < end }.sumOf { it.distanceKm }
}
