package com.atala.app.core.coach

import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.l10n.midSentence
import com.atala.app.core.model.ChatMessage
import com.atala.app.core.model.ChatRole
import com.atala.app.core.model.CoachTone
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.store.AppState
import java.time.Instant

/**
 * Answers built on the phone from the plan (CoachLocalReplies.swift), used when the coach service
 * can't be reached (guest account, no network, server error). Same rules: never a diagnosis,
 * changes only as proposals.
 */
object CoachLocalReplies {
    private val separators = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    fun reply(text: String, state: AppState): ChatMessage {
        // Curly apostrophes typed by some keyboards: "can’t" must read like "can't".
        val canonical = CoachLocalProposals.canonical(text)
        CoachWords.readsEnglish = canonical != text
        try {
            val lowered = canonical.lowercase().replace('’', '\'')
            val words = lowered.split(separators).filter { it.isNotEmpty() }
            return reply(words, lowered, state)
        } finally {
            CoachWords.readsEnglish = false
        }
    }

    private fun reply(words: List<String>, lowered: String, state: AppState): ChatMessage {
        val tone = state.answers.tone
        fun coach(text: String) = ChatMessage(role = ChatRole.Coach, text = text)

        if (state.plan == null) {
            return coach(L("Your plan isn't ready yet. Finish setting it up and I'll have every session at hand to help you."))
        }
        CoachLocalProposals.reply(words, lowered, state, tone)?.let { return it }
        if (words.size <= 4 && matches(words, CoachWords.pick(listOf("thank", "thanks", "cheers", "great", "perfect"), "thanks_stems"))) {
            return coach(pick(tone, L("Any time. I'm here whenever you need me."), L("Good. Back to it."),
                L("That's what I'm here for. Now go and be brilliant.")))
        }
        if (matches(words, CoachWords.pick(listOf("pace", "paces", "fast", "faster", "slow", "slower", "speed"), "pace_stems"))) {
            return coach(paceText(state, tone))
        }
        if (matches(words, CoachWords.pick(listOf("race", "goal", "target", "ready", "predict", "finish"), "goal_stems"))) {
            return coach(goalText(state, tone))
        }
        if (matches(words, CoachWords.pick(listOf("next", "today", "tomorrow", "tonight", "week", "session", "workout", "plan", "schedule", "when"), "next_stems"))) {
            return coach(nextText(state, tone))
        }
        if (matches(words, CoachWords.pick(listOf("motivat", "lazy", "bored", "quit", "struggl", "discourag", "hard", "doubt"), "motivation_stems"))) {
            val lead = pick(tone, L("Some days are heavier than others, and that's normal."),
                L("Motivation comes and goes. The habit stays."), L("Even the best legs have grumpy days."))
            val start = L("Just start the first ten minutes easy and see how you feel.")
            return coach(listOf(lead, start, nextText(state, tone)).joinToString(" "))
        }
        if (words.size <= 3 && matches(words, CoachWords.pick(listOf("hi", "hello", "hey", "morning", "evening"), "greeting_stems"))) {
            return coach(L("Hi %@.", state.firstName) + " " + nextText(state, tone))
        }
        val lead = pick(tone, L("I'm with you."), L("Here's where you stand."), L("Let's keep it simple."))
        val offer = L("If something gets in the way of a session, tell me and I'll suggest how to move it.")
        return coach(listOf(lead, nextText(state, tone), offer).joinToString(" "))
    }

    // Keywords

    fun matches(words: List<String>, stems: List<String>): Boolean {
        // Japanese is written without spaces: a "word" is a whole clause, searched anywhere.
        val anywhere = AppLanguage.current == "ja" && !CoachWords.readsEnglish
        return words.any { word -> stems.any { if (anywhere) word.contains(it) else word.startsWith(it) } }
    }

    fun pick(tone: CoachTone, kind: String, direct: String, playful: String): String = when (tone) {
        CoachTone.Kind -> kind
        CoachTone.Direct -> direct
        CoachTone.Playful -> playful
    }

    /** "A and B": the items of a sentence, joined in the app's language. */
    fun joined(items: List<String>): String {
        var text = items.firstOrNull() ?: return ""
        for (item in items.drop(1)) text = L("%@ and %@", text, item)
        return text
    }

    // Plain answers

    fun isTomorrow(date: Instant): Boolean =
        PlanCalendar.isSameDay(date, PlanCalendar.addDays(PlanCalendar.startOfDay(Instant.now()), 1))

    fun dayLabel(date: Instant): String = when {
        PlanCalendar.isToday(date) -> L("today")
        isTomorrow(date) -> L("tomorrow")
        else -> L("on %@", Fmt.day(date, "EEEE d MMMM"))
    }

    fun describe(workout: Workout): String {
        var text = workout.title
        if (workout.kind.isRun && workout.distanceKm > 0) text += ", " + Fmt.km(workout.distanceKm)
        if (workout.durationMin > 0) text += ", " + L("about %@", Fmt.minutes(workout.durationMin))
        return text
    }

    private fun nextText(state: AppState, tone: CoachTone): String {
        val next = state.nextRun ?: return L("There's no run left on your plan for now. Enjoy the rest, you've earned it.")
        var text = L("Your next run is %@: %@.", dayLabel(next.date), describe(next))
        val pace = next.targetPace
        if (pace != null && pace > 0) text += " " + L("Aim for around %@ per km.", Fmt.pace(pace))
        if (next.kind == WorkoutKind.Easy || next.kind == WorkoutKind.Long) {
            text += " " + pick(tone, L("Keep it comfortable, you should be able to chat the whole way."),
                L("Keep it easy. If you can't talk, slow down."),
                L("Chatting pace only. If you can't gossip, you're going too fast."))
        }
        return text
    }

    private fun paceText(state: AppState, tone: CoachTone): String {
        val paces = state.plan?.paces ?: return nextText(state, tone)
        val lead = pick(tone, L("Here are your paces for now."), L("Your paces:"), L("Your numbers, fresh from the oven:"))
        val numbers = L("Easy runs around %@ per km, long runs %@, tempo %@, intervals %@ and race pace %@.",
            Fmt.pace(paces.easy), Fmt.pace(paces.long), Fmt.pace(paces.tempo), Fmt.pace(paces.interval), Fmt.pace(paces.race))
        val advice = L("Most of your running should feel easy: that's what builds the engine.")
        return listOf(lead, numbers, advice).joinToString(" ")
    }

    private fun goalText(state: AppState, tone: CoachTone): String {
        val plan = state.plan ?: return nextText(state, tone)
        val parts = mutableListOf<String>()
        val raceDate = plan.raceDate
        if (raceDate != null) {
            val today = PlanCalendar.startOfDay(Instant.now())
            val days = PlanCalendar.daysBetween(today, PlanCalendar.startOfDay(raceDate))
            if (days > 1) {
                parts.add(L("Your %@ is in %@ days, on %@.", plan.goal.title, days, Fmt.day(raceDate, "d MMMM")))
            } else if (days >= 0) {
                parts.add(L("Your %@ is almost here.", plan.goal.title))
            }
        } else {
            parts.add(L("Your goal: %@.", plan.goal.title.midSentence))
        }
        val target = plan.targetSeconds
        val predicted = plan.predictedSeconds
        if (target != null) {
            parts.add(L("You're aiming for %@, which means about %@ per km.", Fmt.goalTime(target), Fmt.pace(plan.paces.race)))
        } else if (predicted != null) {
            parts.add(L("The plan is built to bring you to around %@.", Fmt.goalTime(predicted)))
        }
        parts.add(pick(tone, L("Session after session, you're getting there. Stay regular and it will come."),
            L("Do the sessions, keep the easy runs easy, and you'll be ready."),
            L("Keep ticking the sessions off and the finish line won't know what hit it.")))
        return parts.joinToString(" ")
    }
}
