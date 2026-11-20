package com.atala.app.core.coach

import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanChange
import com.atala.app.core.model.ProposalStatus
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutBlock
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStep
import com.atala.app.core.store.AppStore
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/** Short texts shared by the proposal card and the applied-change snapshot (CoachChatPlanApply.swift). */
object CoachPlanText {
    /** "Easy run · 8 km" for runs, "Strength · 20 min" otherwise. */
    fun detail(title: String, kind: WorkoutKind, km: Double, minutes: Int): String = when {
        kind.isRun && km > 0 -> "$title · ${Fmt.km(km)}"
        minutes > 0 -> "$title · ${Fmt.minutes(minutes)}"
        else -> title
    }

    fun detail(workout: Workout): String = detail(workout.title, workout.kind, workout.distanceKm, workout.durationMin)

    /** "Today", "Tomorrow" or "Thu 19 Nov". */
    fun day(date: Instant): String = when {
        PlanCalendar.isToday(date) -> L("Today")
        CoachLocalReplies.isTomorrow(date) -> L("Tomorrow")
        else -> Fmt.day(date, "EEE d MMM")
    }
}

/**
 * Applies the proposal of a coach message once the user tapped "Apply". Keeps a snapshot of each
 * session as it was (so the chat can still show what changed), then makes the plan consistent:
 * steps of a lightened session, and sessions filed under the right week.
 */
fun AppStore.coachApply(messageId: UUID) {
    val message = current.messages.firstOrNull { it.id == messageId } ?: return
    val proposal = message.proposal?.takeIf { it.status == ProposalStatus.Pending } ?: return
    val snapshot = proposal.copy(changes = proposal.changes.map { change ->
        val workout = current.workout(change.workoutId) ?: return@map change
        change.copy(previousDate = workout.date, previousDetail = CoachPlanText.detail(workout))
    })
    update { state ->
        state.copy(messages = state.messages.map { if (it.id == messageId) it.copy(proposal = snapshot) else it })
    }
    applyProposal(messageId)
    snapshot.changes.filter { !it.remove }.forEach { coachSyncSteps(it) }
    coachRehomeWorkouts()
}

/**
 * Makes the steps of a session match a change of kind or distance, so its detail screen and the
 * guided run follow what was agreed with the coach.
 */
private fun AppStore.coachSyncSteps(change: PlanChange) {
    val paces = current.plan?.paces
    if (change.newKind == WorkoutKind.Easy && paces != null) {
        updateWorkout(change.workoutId) { session ->
            val meters = (session.distanceKm * 10).roundToInt() * 100
            val step = WorkoutStep(kind = StepKind.Steady, label = L("Easy pace"), distanceM = meters,
                paceFast = paces.easy - 15, paceSlow = paces.easy + 20)
            session.copy(
                blocks = listOf(WorkoutBlock(steps = listOf(step))),
                subtitle = L("You should be able to talk"),
                targetPace = paces.easy,
                effort = 3,
                why = L("A short easy run in place of the planned session, as agreed with your coach. It keeps you moving without adding strain."),
            )
        }
    } else if (change.newDistanceKm != null) {
        updateWorkout(change.workoutId) { session ->
            // Only a single steady step can simply be shortened or lengthened.
            val block = session.blocks.singleOrNull() ?: return@updateWorkout session
            val step = block.steps.singleOrNull() ?: return@updateWorkout session
            if (block.repeats > 1 || step.distanceM == null) return@updateWorkout session
            val meters = (session.distanceKm * 10).roundToInt() * 100
            session.copy(blocks = listOf(block.copy(steps = listOf(step.copy(distanceM = meters)))))
        }
    }
}

/**
 * Files every session under the week of its date. A session moved to another week by the coach
 * would otherwise stay listed in its old week on the Plan tab.
 */
fun AppStore.coachRehomeWorkouts() = update { state ->
    val plan = state.plan ?: return@update state
    if (plan.weeks.isEmpty()) return@update state
    val lastIndex = plan.weeks.size - 1
    val misplaced = mutableListOf<Workout>()
    val weeks = plan.weeks.mapIndexed { w, week ->
        val start = PlanCalendar.startOfDay(week.startDate)
        val end = PlanCalendar.addDays(start, 7)
        // Sessions before the first week or after the last one stay where they are.
        val outside = week.workouts.filter { (it.date < start && w > 0) || (it.date >= end && w < lastIndex) }
        if (outside.isEmpty()) week else {
            misplaced.addAll(outside)
            week.copy(workouts = week.workouts.filter { session -> outside.none { it.id == session.id } })
        }
    }.toMutableList()
    if (misplaced.isEmpty()) return@update state
    for (session in misplaced) {
        var target = lastIndex
        for (w in weeks.indices) {
            val end = PlanCalendar.addDays(PlanCalendar.startOfDay(weeks[w].startDate), 7)
            if (session.date < end) {
                target = w
                break
            }
        }
        val week = weeks[target]
        weeks[target] = week.copy(workouts = (week.workouts + session.copy(weekIndex = week.index)).sortedBy { it.date })
    }
    state.copy(plan = plan.copy(weeks = weeks))
}
