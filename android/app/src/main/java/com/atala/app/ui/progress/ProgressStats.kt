package com.atala.app.ui.progress

import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSource
import com.atala.app.core.model.TrainingPlan
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal class ProgressBest(val id: String, val title: String, val value: String, val detail: String)

/** The period the summary at the top of the tab covers. */
internal enum class ProgressPeriod {
    Week, Month;

    val title: String get() = if (this == Week) L("This week") else L("This month")
}

/** Distance, runs and time of a period, with the distance of the period before it. */
internal class ProgressSummary(val km: Double, val runs: Int, val seconds: Int, val previousKm: Double) {
    /** Average pace of the period, in seconds per kilometre (0 when nothing was run). */
    val pace: Int get() = if (km > 0) (seconds / km).roundToInt() else 0

    /** Change of the distance against the period before, in percent. Null when there is nothing to compare with. */
    val changePercent: Int? get() = if (previousKm > 0) ((km - previousKm) / previousKm * 100).roundToInt() else null
}

/** One week of the distance chart. */
internal class ProgressWeek(val monday: Instant, val km: Double, val plannedKm: Double, val isCurrent: Boolean)

/** What the goal card shows: the level today, the goal and the paces behind them. */
internal class ProgressGoal(
    val title: String,
    val currentSeconds: Int?,
    val goalSeconds: Int?,
    val footnote: String,
    val vmaKmh: Double,
    val easyPace: Int,
)

/** Sessions done out of the ones whose day has come. */
internal class ProgressConsistency(val done: Int, val settled: Int) {
    /** Whole percent, null while no session has come up yet. */
    val percent: Int? get() = if (settled > 0) (done.toDouble() / settled * 100).roundToInt() else null
}

/** Figures of the Progress tab, derived from the saved runs and the plan (ProgressTabStats.swift). */
internal object ProgressStats {
    fun summary(runs: List<RunRecord>, period: ProgressPeriod, now: Instant = Instant.now()): ProgressSummary {
        val start: Instant
        val previousStart: Instant
        if (period == ProgressPeriod.Week) {
            start = PlanCalendar.mondayOfWeek(now)
            previousStart = PlanCalendar.addDays(start, -7)
        } else {
            val zone = ZoneId.systemDefault()
            val first = now.atZone(zone).toLocalDate().withDayOfMonth(1)
            start = first.atStartOfDay(zone).toInstant()
            previousStart = first.minusMonths(1).atStartOfDay(zone).toInstant()
        }
        val current = runs.filter { it.date >= start && it.date <= now }
        val previous = runs.filter { it.date >= previousStart && it.date < start }
        return ProgressSummary(
            km = current.sumOf { it.distanceKm },
            runs = current.size,
            seconds = current.sumOf { it.durationSec },
            previousKm = previous.sumOf { it.distanceKm },
        )
    }

    /** The last weeks, oldest first, ending with the current one. */
    fun weeks(runs: List<RunRecord>, plan: TrainingPlan?, count: Int = 12, now: Instant = Instant.now()): List<ProgressWeek> {
        val thisMonday = PlanCalendar.mondayOfWeek(now)
        val totals = mutableMapOf<Instant, Double>()
        runs.filter { it.date <= now }.forEach { run ->
            val monday = PlanCalendar.mondayOfWeek(run.date)
            totals[monday] = (totals[monday] ?: 0.0) + run.distanceKm
        }
        val planned = plan?.weeks?.associate { PlanCalendar.mondayOfWeek(it.startDate) to it.plannedKm }.orEmpty()
        return (count - 1 downTo 0).map { back ->
            val monday = PlanCalendar.mondayOfWeek(PlanCalendar.addDays(thisMonday, -7 * back))
            ProgressWeek(monday, totals[monday] ?: 0.0, planned[monday] ?: 0.0, isCurrent = back == 0)
        }
    }

    /** "12" or "12.4": a distance without its unit. */
    fun number(km: Double): String =
        if (abs(km - Math.round(km)) < 0.05) "${Math.round(km)}" else oneDecimal(km)

    fun oneDecimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    fun goal(plan: TrainingPlan) = ProgressGoal(
        title = plan.goal.title,
        currentSeconds = plan.currentSeconds,
        goalSeconds = plan.targetSeconds ?: plan.predictedSeconds,
        footnote = footnote(plan.targetSeconds, plan.predictedSeconds),
        vmaKmh = plan.vmaKmh,
        easyPace = plan.paces.easy,
    )

    fun footnote(target: Int?, predicted: Int?): String {
        if (target != null) {
            if (predicted != null && predicted != target) {
                return L("Goal %@ · the plan aims for %@ on race day", Fmt.goalTime(target), Fmt.goalTime(predicted))
            }
            return L("Goal %@", Fmt.goalTime(target))
        }
        if (predicted != null) return L("The plan aims for %@ at the end", Fmt.goalTime(predicted))
        return L("Your prediction sharpens as you log runs.")
    }

    fun bests(runs: List<RunRecord>): List<ProgressBest> {
        val result = mutableListOf<ProgressBest>()
        val valid = runs.filter { it.distanceM > 0 && it.durationSec > 0 }
        valid.maxByOrNull { it.distanceM }?.let {
            result.add(ProgressBest("longest", L("Longest run"), Fmt.km(it.distanceKm), dateText(it)))
        }
        valid.filter { it.distanceM >= 1000 }.minByOrNull { it.avgPace }?.let {
            result.add(ProgressBest("pace", L("Fastest average pace"), "${Fmt.pace(it.avgPace)}/km", "${Fmt.km(it.distanceKm)} · ${dateText(it)}"))
        }
        listOf(Triple("5k", L("Best 5 km"), 5.0), Triple("10k", L("Best 10 km"), 10.0), Triple("half", L("Best half marathon"), 21.0975))
            .forEach { (id, title, km) ->
                // A run slightly short of the distance (GPS drift) still counts.
                val best = valid.filter { it.distanceKm >= km * 0.99 }.minByOrNull { it.avgPace } ?: return@forEach
                val seconds = (best.durationSec / best.distanceKm * km).roundToInt()
                result.add(ProgressBest(id, title, Fmt.duration(seconds),
                    L("At the pace of your %@ run · %@", Fmt.km(best.distanceKm), dateText(best))))
            }
        valid.maxByOrNull { it.durationSec }?.let {
            result.add(ProgressBest("time", L("Longest time on your feet"), Fmt.duration(it.durationSec), dateText(it)))
        }
        return result
    }

    fun dateText(run: RunRecord): String = Fmt.day(run.date, "d MMM yyyy")

    fun sourceTitle(source: RunSource): String = when (source) {
        RunSource.Gps -> "GPS"
        RunSource.Manual -> L("Added by hand")
        // Only runs synced from an iPhone come from a health store: Android has none.
        RunSource.Health -> "Apple Health"
    }
}

/**
 * Believable figures shown behind the blur of the locked tab, while the runner has little or
 * nothing of their own to show yet. Never shown with Atala Pro.
 */
internal object ProgressSample {
    /** Twelve weeks of three runs, building up, ending this week. Built once per launch. */
    val runs: List<RunRecord> by lazy { makeRuns(Instant.now()) }

    val goal: ProgressGoal
        get() = ProgressGoal(L("10K"), 3015, 2880, ProgressStats.footnote(2880, 2850), 14.6, 352)

    const val SESSIONS_DONE = 29
    const val SESSIONS_SETTLED = 32

    private fun makeRuns(now: Instant): List<RunRecord> {
        val thisMonday = PlanCalendar.mondayOfWeek(now)
        // Kilometres per week, oldest first.
        val weeklyKm = listOf(14.0, 16.0, 18.0, 15.0, 20.0, 22.0, 24.0, 19.0, 25.0, 27.0, 29.0, 31.0)
        // Day of the week (0 = Monday), share of the week, title, pace in seconds per kilometre.
        data class Slot(val day: Int, val share: Double, val title: String, val pace: Int)
        val pattern = listOf(Slot(1, 0.28, "Easy run", 352), Slot(3, 0.27, "Tempo run", 305), Slot(6, 0.45, "Long run", 365))
        val result = mutableListOf<RunRecord>()
        weeklyKm.forEachIndexed { index, km ->
            val back = weeklyKm.size - 1 - index
            val monday = PlanCalendar.addDays(thisMonday, -7 * back)
            var added = 0
            pattern.forEachIndexed { slot, entry ->
                val date = PlanCalendar.addDays(monday, entry.day).plusSeconds(18 * 3600)
                if (date > now) return@forEachIndexed
                val distanceM = Math.round(km * entry.share * 1000).toDouble()
                // A little faster as the weeks go by.
                val pace = entry.pace - index * 2
                val duration = (distanceM / 1000 * pace).roundToInt()
                // Android has no health store: the variety comes from runs added by hand.
                val source = if ((index + slot) % 3 == 0) RunSource.Manual else RunSource.Gps
                result.add(RunRecord(title = entry.title, date = date, source = source, distanceM = distanceM, durationSec = duration))
                added += 1
            }
            if (added == 0) {
                // Early in the week: one run at its very start, so the week is never empty.
                result.add(RunRecord(title = "Easy run", date = monday, source = RunSource.Gps, distanceM = 6200.0, durationSec = 2130))
            }
        }
        return result
    }
}
