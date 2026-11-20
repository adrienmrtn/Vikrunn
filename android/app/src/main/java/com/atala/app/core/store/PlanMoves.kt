package com.atala.app.core.store

import com.atala.app.core.l10n.L
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

// Moving a session by hand (PlanMoveSheet.swift) and filing sessions under the week of their date
// (CoachChatPlanApply.swift).

/** What happens when a session is dropped on a given day. */
sealed interface PlanMoveSlot {
    /** The session is already there. */
    data object Current : PlanMoveSlot
    /** Nothing of the same kind that day: the session simply moves. */
    data object Free : PlanMoveSlot
    /** Another planned session of the same kind is there: the two trade places. */
    data class Swap(val other: Workout) : PlanMoveSlot
    /** The day cannot take the session; the text says why. */
    data class Blocked(val reason: String) : PlanMoveSlot
}

/** How far ahead a session can be moved by hand. */
const val PLAN_MOVE_HORIZON_DAYS = 14

/** A session can be moved by hand while it is not done and is not the race itself. */
fun AppState.planCanMove(workout: Workout): Boolean =
    workout.status != WorkoutStatus.Done && workout.kind != WorkoutKind.Race && planMoveDays().isNotEmpty()

/**
 * Days a session can be moved to: from today, two weeks ahead at most, never past the end of
 * the plan and never on or after race day. The same for every session.
 */
fun AppState.planMoveDays(): List<Instant> {
    val plan = plan ?: return emptyList()
    val lastWeek = plan.weeks.lastOrNull() ?: return emptyList()
    val today = PlanCalendar.startOfDay(Instant.now())
    var last = PlanCalendar.addDays(PlanCalendar.startOfDay(lastWeek.startDate), 6)
    plan.raceDate?.let { last = minOf(last, PlanCalendar.addDays(PlanCalendar.startOfDay(it), -1)) }
    last = minOf(last, PlanCalendar.addDays(today, PLAN_MOVE_HORIZON_DAYS - 1))
    if (last < today) return emptyList()
    return generateSequence(today) { PlanCalendar.addDays(it, 1) }
        .takeWhile { it <= last }
        .take(PLAN_MOVE_HORIZON_DAYS)
        .toList()
}

/** The other sessions of that day, the moved one left out. */
fun AppState.planOthers(day: Instant, besides: Workout): List<Workout> =
    workouts(day).filter { it.id != besides.id }

fun AppState.planMoveSlot(workout: Workout, day: Instant): PlanMoveSlot {
    if (PlanCalendar.isSameDay(workout.date, day)) return PlanMoveSlot.Current
    // Runs compete with runs, strength and stretching with themselves.
    val rivals = planOthers(day, workout).filter { other ->
        if (workout.kind.isRun) other.kind.isRun else other.kind == workout.kind
    }
    val rival = rivals.firstOrNull() ?: return PlanMoveSlot.Free
    if (rivals.size > 1) return PlanMoveSlot.Blocked(L("Day already full"))
    if (rival.kind == WorkoutKind.Race) return PlanMoveSlot.Blocked(L("Race day"))
    if (rival.status == WorkoutStatus.Done) return PlanMoveSlot.Blocked(L("Already done"))
    // Trading places would send the other session into the past.
    val today = PlanCalendar.startOfDay(Instant.now())
    if (PlanCalendar.startOfDay(workout.date) < today) return PlanMoveSlot.Blocked(L("Another session that day"))
    return PlanMoveSlot.Swap(rival)
}

/** True when a hard run (intervals, tempo, race pace, long run) would sit right next to another one. */
fun AppState.planMoveIsTight(workout: Workout, day: Instant): Boolean {
    fun isHard(kind: WorkoutKind) = kind.isQuality || kind == WorkoutKind.Long
    if (!isHard(workout.kind)) return false
    // On a swap the other session lands on this one's day: two hard days side by side count too.
    val slot = planMoveSlot(workout, day)
    if (slot is PlanMoveSlot.Swap && isHard(slot.other.kind)) {
        val gap = PlanCalendar.daysBetween(PlanCalendar.startOfDay(workout.date), PlanCalendar.startOfDay(day))
        if (abs(gap) == 1) return true
    }
    return listOf(-1, 1).any { offset ->
        planOthers(PlanCalendar.addDays(day, offset), workout).any { isHard(it.kind) && it.status != WorkoutStatus.Missed }
    }
}

/**
 * Moves a session to another day, trading places with the session already there when needed.
 * Returns false, changing nothing, when the day cannot take it.
 */
fun AppStore.planMove(id: UUID, day: Instant): Boolean {
    val state = current
    val workout = state.workout(id)?.takeIf { state.planCanMove(it) } ?: return false
    val target = PlanCalendar.startOfDay(day)
    if (state.planMoveDays().none { PlanCalendar.isSameDay(it, target) }) return false
    val origin = PlanCalendar.startOfDay(workout.date)
    when (val slot = state.planMoveSlot(workout, target)) {
        PlanMoveSlot.Current, is PlanMoveSlot.Blocked -> return false
        PlanMoveSlot.Free -> Unit
        is PlanMoveSlot.Swap -> updateWorkout(slot.other.id) { it.copy(date = origin) }
    }
    updateWorkout(id) {
        it.copy(date = target, status = if (it.status == WorkoutStatus.Missed) WorkoutStatus.Planned else it.status)
    }
    // File both sessions under the week of their new date.
    update { it.copy(plan = it.plan?.rehomingWorkouts()) }
    return true
}

/**
 * Files every session under the week of its date. A session moved to another week would otherwise
 * stay listed in its old week on the Plan tab. Sessions before the first week or after the last
 * one stay where they are.
 */
fun TrainingPlan.rehomingWorkouts(): TrainingPlan {
    if (weeks.isEmpty()) return this
    val lastIndex = weeks.lastIndex
    fun end(start: Instant) = PlanCalendar.addDays(start, 7)

    val misplaced = mutableListOf<Workout>()
    val kept = weeks.mapIndexed { w, week ->
        val start = PlanCalendar.startOfDay(week.startDate)
        val (outside, inside) = week.workouts.partition { session ->
            (session.date < start && w > 0) || (session.date >= end(start) && w < lastIndex)
        }
        misplaced += outside
        week.copy(workouts = inside)
    }.toMutableList()
    if (misplaced.isEmpty()) return this

    for (session in misplaced) {
        val target = kept.indexOfFirst { session.date < end(PlanCalendar.startOfDay(it.startDate)) }
            .takeIf { it >= 0 } ?: lastIndex
        val week = kept[target]
        kept[target] = week.copy(workouts = (week.workouts + session.copy(weekIndex = week.index)).sortedBy { it.date })
    }
    return copy(weeks = kept)
}
