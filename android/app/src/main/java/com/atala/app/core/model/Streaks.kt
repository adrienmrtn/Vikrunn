package com.atala.app.core.model

import com.atala.app.core.l10n.L
import java.time.Instant

/**
 * Weeks in a row with at least one session done. Runners rest on purpose, so the streak counts
 * weeks, not days: a rest day never breaks it.
 */
data class Streak(
    /**
     * Weeks in a row up to now. The current week counts once something is done in it; until then
     * the streak of the previous weeks stays alive.
     */
    val current: Int,
    val best: Int,
    /** True when this week already has a session done. */
    val activeThisWeek: Boolean,
) {
    companion object {
        val None = Streak(current = 0, best = 0, activeThisWeek = false)
    }
}

object StreakEngine {
    fun weekly(runs: List<RunRecord>, workouts: List<Workout>, now: Instant = Instant.now()): Streak {
        val activeWeeks = buildSet {
            runs.filter { it.date <= now }.forEach { add(PlanCalendar.mondayOfWeek(it.date)) }
            workouts.filter { it.status == WorkoutStatus.Done && it.date <= now }
                .forEach { add(PlanCalendar.mondayOfWeek(it.date)) }
        }
        if (activeWeeks.isEmpty()) return Streak.None

        fun weekBefore(monday: Instant): Instant = PlanCalendar.mondayOfWeek(PlanCalendar.addDays(monday, -7))

        val thisWeek = PlanCalendar.mondayOfWeek(now)
        val activeThisWeek = thisWeek in activeWeeks

        // Current streak: counted back from this week, or from last week while this one is still empty.
        var current = 0
        var cursor = if (activeThisWeek) thisWeek else weekBefore(thisWeek)
        while (cursor in activeWeeks) {
            current += 1
            cursor = weekBefore(cursor)
        }

        // Best streak: longest chain of consecutive weeks.
        var best = 0
        var chain = 0
        var previous: Instant? = null
        for (week in activeWeeks.sorted()) {
            chain = if (previous != null && weekBefore(week) == previous) chain + 1 else 1
            best = maxOf(best, chain)
            previous = week
        }
        return Streak(current = current, best = maxOf(best, current), activeThisWeek = activeThisWeek)
    }

    fun weeks(count: Int): String = if (count == 1) L("1 week") else L("%@ weeks", count)

    /** One line that says what to do next. */
    fun nudge(streak: Streak): String = when {
        streak.current == 0 -> L("Finish one session this week to start your streak.")
        streak.activeThisWeek -> L("This week is in the bank. Show up next week to make it %@.", streak.current + 1)
        else -> L("One session this week keeps your streak alive.")
    }
}
