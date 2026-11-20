package com.atala.app.core.coach

import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.l10n.midSentence
import com.atala.app.core.model.ChatMessage
import com.atala.app.core.model.ChatRole
import com.atala.app.core.model.CoachTone
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanChange
import com.atala.app.core.model.PlanProposal
import com.atala.app.core.model.Weekday
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.store.AppState
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Plan changes proposed from the phone when the coach service can't be reached
 * (CoachLocalProposals.swift). Every change points at a real session of the current plan and is
 * applied only after the user accepts.
 */
object CoachLocalProposals {
    private val pendingNote: String
        get() = L("The rest of your plan stays as it is. Nothing changes until you tap Apply.")

    /** A reply with a proposal when the message calls for a change of plan, null otherwise. */
    fun reply(words: List<String>, lowered: String, state: AppState, tone: CoachTone): ChatMessage? {
        val intent = CoachLocalIntent.detect(words, lowered) ?: return null
        val days = CoachLocalIntent.mentionedDays(words, lowered)
        return when (intent) {
            CoachLocalIntent.Pain -> painReply(state, tone)
            CoachLocalIntent.Skip -> skipReply(days, state, tone)
            else -> moveReply(intent, days, state, tone)
        }
    }

    /** Suggestions shown in the chat. The first ones always lead to a proposal on the current plan. */
    fun suggestedPrompts(state: AppState): List<String> {
        val runs = movableRuns(state)
        val first = runs.firstOrNull()
            ?: return listOf(L("What's my next session?"), L("What pace should my easy runs be?"), L("Am I on track for my goal?"))
        val today = PlanCalendar.startOfDay(Instant.now())
        val todayIndex = PlanCalendar.mondayIndex(today)
        val prompts = mutableListOf(L("I'm tired today, can we move my session?"))
        val weekend = listOf(maxOf(0, 6 - todayIndex), 7 - todayIndex).map { PlanCalendar.addDays(today, it) }
        val runsThisWeekend = runs.any { run -> weekend.any { PlanCalendar.isSameDay(it, run.date) } }
        if (runsThisWeekend) {
            prompts.add(L("I'm travelling this weekend"))
        } else {
            // A run of the coming days, named by its weekday so the coach finds it.
            val limit = PlanCalendar.addDays(today, 7)
            val soon = runs.firstOrNull { it.date > today && it.date < limit } ?: first
            prompts.add(L("I can't run on %@", Weekday.long(PlanCalendar.mondayIndex(soon.date))))
        }
        prompts.add(L("My knee hurts a little"))
        prompts.add(L("What's my next session?"))
        return prompts
    }

    /**
     * A suggested prompt tapped in another language, back in English: the answers built on the phone
     * read the message from its English words. Any other message comes back unchanged.
     */
    fun canonical(text: String): String {
        val trimmed = text.trim()
        val fixed = listOf(
            "What's my next session?", "What pace should my easy runs be?", "Am I on track for my goal?",
            "I'm tired today, can we move my session?", "I'm travelling this weekend", "My knee hurts a little",
        )
        fixed.firstOrNull { L(it) == trimmed }?.let { return it }
        for (index in 1..7) {
            if (L("I can't run on %@", Weekday.long(index)) == trimmed) return "I can't run on " + Weekday.englishLong(index)
        }
        return text
    }

    // Sessions and days

    private fun isHard(workout: Workout) = workout.kind.isQuality || workout.kind == WorkoutKind.Long

    /** Planned runs from today on, race excluded. */
    private fun movableRuns(state: AppState): List<Workout> {
        val today = PlanCalendar.startOfDay(Instant.now())
        return state.allWorkouts.filter {
            it.kind.isRun && it.kind != WorkoutKind.Race && it.status == WorkoutStatus.Planned && it.date >= today
        }
    }

    /** The runs planned on the days the user named, or the next run when there is none on those days. */
    private fun targets(days: List<Instant>, state: AppState): Pair<List<Workout>, Boolean> {
        val runs = movableRuns(state)
        val onDays = runs.filter { run -> days.any { PlanCalendar.isSameDay(it, run.date) } }
        if (onDays.isNotEmpty()) return onDays.take(3) to true
        return runs.take(1) to false
    }

    private fun hasHardNeighbour(day: Instant, id: UUID, state: AppState): Boolean =
        listOf(-1, 1).any { delta ->
            state.workouts(PlanCalendar.addDays(day, delta)).any { it.id != id && isHard(it) }
        }

    /**
     * The best day to run the session instead: no other run that day, not a blocked day, before the
     * race, as close as possible to the original day, in the same week when it can, away from other
     * hard sessions.
     */
    private fun destination(workout: Workout, earliest: Instant, blocked: Set<Instant>, state: AppState): Instant? {
        val today = PlanCalendar.startOfDay(Instant.now())
        val original = PlanCalendar.startOfDay(workout.date)
        val start = maxOf(today, PlanCalendar.startOfDay(earliest))
        val last = PlanCalendar.addDays(original, 7)
        val raceDay = state.plan?.raceDate?.let { PlanCalendar.startOfDay(it) }
        val originalWeek = PlanCalendar.mondayOfWeek(original)
        var best: Instant? = null
        var bestScore = Double.MAX_VALUE

        for (offset in 0..21) {
            val day = PlanCalendar.startOfDay(PlanCalendar.addDays(start, offset))
            if (day > last) break
            if (raceDay != null && day >= raceDay) break
            if (day == original || day in blocked) continue
            val others = state.workouts(day).filter { it.id != workout.id }
            if (others.any { it.kind.isRun }) continue

            var score = abs(PlanCalendar.daysBetween(original, day)).toDouble()
            if (day < original) score += 0.5
            if (day == today) score += 1
            if (others.isNotEmpty()) score += 0.75
            if (PlanCalendar.mondayOfWeek(day) != originalWeek) score += 1.5
            if (isHard(workout) && hasHardNeighbour(day, workout.id, state)) score += 3
            if (score < bestScore) {
                bestScore = score
                best = day
            }
        }
        return best
    }

    /** About 60 % of the planned distance, at least 3 km, never more than planned. */
    private fun lighter(km: Double): Double {
        val reduced = maxOf(3.0, Math.round(km * 0.6).toDouble())
        return if (km > 0) minOf(km, reduced) else reduced
    }

    // Wording

    private fun dayName(date: Instant): String = when {
        PlanCalendar.isToday(date) -> L("today")
        CoachLocalReplies.isTomorrow(date) -> L("tomorrow")
        else -> Fmt.day(date, "EEEE d MMMM")
    }

    private fun daysText(days: List<Instant>): String {
        val labels = days.map(CoachLocalReplies::dayLabel)
        return when (labels.size) {
            1 -> labels[0]
            2 -> L("%@ or %@", labels[0], labels[1])
            else -> L("on those days")
        }
    }

    private fun lead(intent: CoachLocalIntent, tone: CoachTone): String = when (intent) {
        CoachLocalIntent.Sick -> CoachLocalReplies.pick(tone,
            L("Rest comes first: no running while you feel unwell. If you have a fever, or it doesn't pass in a few days, please see a doctor."),
            L("No running while you're unwell. If you have a fever, or it doesn't pass in a few days, see a doctor."),
            L("Sofa first, shoes later. If you have a fever, or it doesn't pass in a few days, please see a doctor."))
        CoachLocalIntent.Tired -> CoachLocalReplies.pick(tone,
            L("Thanks for telling me. Rest counts as training too."),
            L("Then rest. We move it."),
            L("Tired legs get a veto."))
        else -> CoachLocalReplies.pick(tone,
            L("No problem, life comes first."),
            L("Fine. We adapt."),
            L("Plans are made to be wiggled a little."))
    }

    // Replies

    private fun moveReply(intent: CoachLocalIntent, days: List<Instant>, state: AppState, tone: CoachTone): ChatMessage {
        val (runs, matched) = targets(days, state)
        if (runs.isEmpty()) {
            return ChatMessage(role = ChatRole.Coach,
                text = L("There's no session left to move right now. Rest, and write to me when you're ready to go again."))
        }
        val today = PlanCalendar.startOfDay(Instant.now())
        val blocked = mutableSetOf<Instant>()
        days.forEach { blocked.add(PlanCalendar.startOfDay(it)) }
        runs.forEach { blocked.add(PlanCalendar.startOfDay(it.date)) }
        if (intent == CoachLocalIntent.Tired || intent == CoachLocalIntent.Sick) blocked.add(today)
        if (intent == CoachLocalIntent.Sick) blocked.add(PlanCalendar.startOfDay(PlanCalendar.addDays(today, 1)))

        val changes = mutableListOf<PlanChange>()
        val moved = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        for (run in runs) {
            val runDay = PlanCalendar.startOfDay(run.date)
            // A session only moves earlier when the user named the days they can't run.
            var earliest = PlanCalendar.addDays(runDay, 1)
            if (matched && intent == CoachLocalIntent.Busy) earliest = today
            val day = destination(run, earliest, blocked, state)
            if (day != null) {
                changes.add(PlanChange(workoutId = run.id, newDate = day))
                blocked.add(day)
                moved.add(L("your %@ from %@ to %@", run.title.midSentence, dayName(run.date), dayName(day)))
            } else {
                changes.add(PlanChange(workoutId = run.id, remove = true))
                dropped.add(L("your %@ %@", run.title.midSentence, CoachLocalReplies.dayLabel(run.date)))
            }
        }

        var text = lead(intent, tone) + " "
        if (!matched && days.isNotEmpty()) {
            text += L("You have no run planned %@, so nothing is in the way there. Still, if it helps,", daysText(days)) + " "
        }
        val sentences = mutableListOf<String>()
        if (moved.isNotEmpty()) sentences.add(L("I can move %@.", CoachLocalReplies.joined(moved)))
        if (dropped.isNotEmpty()) {
            val names = CoachLocalReplies.joined(dropped)
            sentences.add(
                if (dropped.size == 1) L("I'd rather let %@ go than squeeze it in: the days around are already full.", names)
                else L("I'd rather let %@ go than squeeze them in: the days around are already full.", names),
            )
        }
        text += sentences.joinToString(" ") + " " + L("Does that work for you?")
        return ChatMessage(role = ChatRole.Coach, text = text, proposal = PlanProposal(summary = pendingNote, changes = changes))
    }

    private fun skipReply(days: List<Instant>, state: AppState, tone: CoachTone): ChatMessage {
        val (runs, matched) = targets(days, state)
        if (runs.isEmpty()) {
            return ChatMessage(role = ChatRole.Coach,
                text = L("There's no session left to take off right now. Rest, and write to me when you're ready to go again."))
        }
        val changes = runs.map { PlanChange(workoutId = it.id, remove = true) }
        val names = runs.map { L("your %@ %@", it.title.midSentence, CoachLocalReplies.dayLabel(it.date)) }
        var text = CoachLocalReplies.pick(tone, L("Understood, no guilt needed."), L("Understood."), L("Permission granted.")) + " "
        if (!matched && days.isNotEmpty()) {
            text += L("You have no run planned %@. Your next one is the session below.", daysText(days)) + " "
        }
        text += L("I can take %@ off the plan. One session less won't undo your training. Shall I?", CoachLocalReplies.joined(names))
        return ChatMessage(role = ChatRole.Coach, text = text, proposal = PlanProposal(summary = pendingNote, changes = changes))
    }

    private fun painReply(state: AppState, tone: CoachTone): ChatMessage {
        val advice = CoachLocalReplies.pick(tone,
            L("Thank you for telling me. I can't tell you what it is, and I don't want you to guess either: please have it looked at by a doctor or a physiotherapist, especially if it lasts or gets worse."),
            L("Don't run through pain. I can't tell you what it is: see a doctor or a physiotherapist, especially if it lasts or gets worse."),
            L("Let's not play the hero with that. I can't tell you what it is, so please show it to a doctor or a physiotherapist, especially if it lasts or gets worse."))
        val note = L("Only if it feels fine on the day. If it hurts while running, stop and walk home.")
        val runs = movableRuns(state)
        val easyPace = state.plan?.paces?.easy ?: 390

        val hard = runs.firstOrNull(::isHard)
        if (hard != null) {
            val km = lighter(hard.distanceKm)
            val change = PlanChange(
                workoutId = hard.id, newKind = WorkoutKind.Easy, newTitle = L("Easy run"),
                newDistanceKm = km, newDurationMin = (km * easyPace / 60).roundToInt(),
            )
            val text = advice + " " + L("In the meantime I'd swap your %@ %@ for a short easy run.",
                hard.title.midSentence, CoachLocalReplies.dayLabel(hard.date))
            return ChatMessage(role = ChatRole.Coach, text = text, proposal = PlanProposal(summary = note, changes = listOf(change)))
        }
        val next = runs.firstOrNull()
        if (next != null && next.distanceKm > 3) {
            val km = lighter(next.distanceKm)
            val change = PlanChange(workoutId = next.id, newDistanceKm = km, newDurationMin = (km * easyPace / 60).roundToInt())
            val text = advice + " " + L("In the meantime I'd shorten your run %@.", CoachLocalReplies.dayLabel(next.date))
            return ChatMessage(role = ChatRole.Coach, text = text, proposal = PlanProposal(summary = note, changes = listOf(change)))
        }
        return ChatMessage(role = ChatRole.Coach,
            text = advice + " " + L("Skip your next run if you're in doubt: a few days off cost far less than an injury."))
    }
}
