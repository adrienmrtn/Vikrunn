package com.atala.app.core.coach

import com.atala.app.core.model.PlanCalendar
import java.time.Instant

/**
 * What the user is asking for when a message calls for a change of plan, read from its words
 * (CoachLocalIntent.swift). Used by the answers built on the phone (guest, offline, server down).
 */
enum class CoachLocalIntent {
    /** Something hurts: lighten the next session, and send the user to a professional. */
    Pain,
    /** Unwell: rest first, push the next session back. */
    Sick,
    /** The user asks to drop a session. */
    Skip,
    /** Tired, slept badly: move the session to a later day. */
    Tired,
    /** Busy, travelling, can't run on a day, or simply asking to move a session. */
    Busy;

    companion object {
        /** [words] are the lowercased words of the message, [lowered] the whole lowercased message with straight apostrophes. */
        fun detect(words: List<String>, lowered: String): CoachLocalIntent? {
            if (hasStem(words, CoachWords.pick(painStems, "pain_stems")) ||
                hasPhrase(lowered, CoachWords.pick(painPhrases, "pain_phrases"))
            ) return Pain
            if (hasStem(words, CoachWords.pick(sickStems, "sick_stems")) || hasWord(words, CoachWords.english(sickWords)) ||
                hasPhrase(lowered, CoachWords.pick(sickPhrases, "sick_phrases"))
            ) return Sick
            if (hasStem(words, CoachWords.pick(skipStems, "skip_stems")) ||
                hasPhrase(lowered, CoachWords.pick(skipPhrases, "skip_phrases"))
            ) return Skip
            if (hasStem(words, CoachWords.pick(tiredStems, "tired_stems")) ||
                hasPhrase(lowered, CoachWords.pick(tiredPhrases, "tired_phrases"))
            ) return Tired
            if (hasStem(words, CoachWords.pick(busyStems, "busy_stems")) || hasWord(words, CoachWords.english(busyWords)) ||
                hasPhrase(lowered, CoachWords.pick(busyPhrases, "busy_phrases"))
            ) return Busy
            return null
        }

        /** The days named in the message (today, tomorrow, the weekend, a weekday), from today on, sorted. */
        fun mentionedDays(words: List<String>, lowered: String): List<Instant> {
            val today = PlanCalendar.startOfDay(Instant.now())
            val todayIndex = PlanCalendar.mondayIndex(today)
            val offsets = mutableListOf<Int>()

            if (hasWord(words, CoachWords.english(listOf("today", "tonight"))) ||
                hasPhrase(lowered, CoachWords.pick(listOf("this morning", "this afternoon", "this evening"), "today_phrases"))
            ) offsets.add(0)
            if (hasWord(words, CoachWords.english(listOf("tomorrow"))) || hasPhrase(lowered, CoachWords.list("tomorrow_phrases"))) {
                offsets.add(1)
            }
            if (hasWord(words, CoachWords.english(listOf("weekend", "weekends"))) || hasPhrase(lowered, CoachWords.list("weekend_phrases"))) {
                // Saturday and Sunday of the current week (only Sunday is left when today is Sunday).
                offsets.add(maxOf(0, 6 - todayIndex))
                offsets.add(7 - todayIndex)
            }
            weekdayNames.forEachIndexed { index, names ->
                // Next occurrence of that weekday, today included.
                val named = hasWord(words, CoachWords.english(names)) ||
                    hasPhrase(lowered, CoachWords.list("weekday_${index + 1}_phrases"))
                if (named) offsets.add((index + 1 - todayIndex + 7) % 7)
            }

            val days = mutableListOf<Instant>()
            for (offset in offsets.sorted()) {
                val day = PlanCalendar.startOfDay(PlanCalendar.addDays(today, offset))
                if (day !in days) days.add(day)
            }
            return days
        }

        // Keywords. Prefixes: "injur" matches "injury" and "injured".
        private val painStems = listOf(
            "pain", "hurt", "injur", "sore", "ache", "aching", "knee", "ankle", "shin", "calf",
            "calves", "achilles", "hamstring", "sprain", "strain", "twinge", "cramp",
            "swollen", "limp", "tendon", "blister", "stiff", "niggl", "tweak", "dizzy",
        )
        private val painPhrases = listOf("pulled a muscle", "pulled muscle", "pulled my", "chest pain")

        private val sickStems = listOf(
            "sick", "fever", "unwell", "nause", "migraine", "headache", "vomit", "covid", "poorly", "coughing",
        )
        private val sickWords = listOf("ill", "flu")
        private val sickPhrases = listOf(
            "a cold", "under the weather", "not feeling well", "don't feel well",
            "dont feel well", "feeling rough", "sore throat",
        )

        private val skipStems = listOf("skip", "cancel")
        private val skipPhrases = listOf(
            "drop my", "drop the", "drop today", "drop tomorrow", "take it off",
            "off the plan", "off my plan", "remove my", "remove the", "delete my", "delete the",
        )

        private val tiredStems = listOf(
            "tired", "exhaust", "fatigue", "knackered", "shattered", "drained", "sleepy",
            "hungover", "jetlag", "insomnia", "wiped",
        )
        private val tiredPhrases = listOf(
            "slept badly", "slept poorly", "bad night", "rough night", "short night",
            "didn't sleep", "didnt sleep", "did not sleep", "not slept", "no sleep",
            "little sleep", "poor sleep", "worn out", "burnt out", "burned out",
            "heavy legs", "dead legs", "no energy", "low energy", "jet lag",
            "need a rest", "need rest", "need a break", "day off", "rest today",
        )

        private val busyStems = listOf(
            "busy", "travel", "holiday", "vacation", "wedding", "abroad", "overtime",
            "move", "moving", "postpone", "reschedul", "swap", "shift", "delay",
            "conference", "flight", "raining", "snowing", "storm",
        )
        private val busyWords = listOf("trip", "trips", "miss", "missing", "rain", "snow", "icy")
        private val busyPhrases = listOf(
            "can't run", "cant run", "cannot run", "can not run", "won't run", "wont run",
            "can't make", "cant make", "cannot make", "can't train", "cant train",
            "cannot train", "can't go", "cant go", "can't do", "cant do", "cannot do",
            "won't be able", "wont be able", "not able to", "unable to", "no time",
            "not enough time", "don't have time", "dont have time", "work late",
            "working late", "push back", "put off", "another day", "other day",
            "different day", "do it later", "out of town", "on the road",
            "i'm away", "im away", "i am away", "be away", "going away",
            "too hot", "too cold",
        )

        private val weekdayNames = listOf(
            listOf("monday", "mondays", "mon"),
            listOf("tuesday", "tuesdays", "tue", "tues"),
            listOf("wednesday", "wednesdays"),
            listOf("thursday", "thursdays", "thu", "thur", "thurs"),
            listOf("friday", "fridays", "fri"),
            listOf("saturday", "saturdays"),
            listOf("sunday", "sundays"),
        )

        private fun hasStem(words: List<String>, stems: List<String>) =
            words.any { word -> stems.any { word.startsWith(it) } }

        private fun hasWord(words: List<String>, list: List<String>) = words.any { it in list }

        private fun hasPhrase(text: String, phrases: List<String>): Boolean {
            // Padded, so an entry written with spaces around it also matches at either end of the message.
            val padded = " $text "
            return phrases.any { padded.contains(it) }
        }
    }
}
