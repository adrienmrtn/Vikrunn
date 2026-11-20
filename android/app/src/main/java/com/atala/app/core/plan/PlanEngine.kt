package com.atala.app.core.plan

import com.atala.app.core.model.Experience
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.Paces
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanPhase
import com.atala.app.core.model.PlanWeek
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.WeeklyVolume
import java.time.Instant
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Rule-based plan generator. Deterministic: the same answers always give the same plan, on the
 * iPhone (PlanEngine.swift) and here. Method: VMA (maximal aerobic speed) estimate, then target
 * paces as fractions of VMA.
 */
object PlanEngine {
    const val MAX_WEEKS = 8

    // Level

    /** VMA in km/h, from a recent result when there is one, otherwise a cautious guess. */
    fun estimateVma(answers: OnboardingAnswers): Double {
        val result = answers.recentResult
        if (result != null && result.seconds > 0 && result.distanceKm > 0) {
            val speed = result.distanceKm / (result.seconds / 3600.0)
            return (speed / sustainableFraction(result.distanceKm)).coerceIn(7.0, 22.0)
        }
        var vma = when (answers.experience ?: Experience.Starting) {
            Experience.Starting -> 9.0
            Experience.UnderSixMonths -> 10.5
            Experience.SixMonthsToTwoYears -> 12.0
            Experience.OverTwoYears -> 13.0
        }
        if (answers.weeklyVolume == WeeklyVolume.Over25) vma += 0.5
        return vma
    }

    /** Share of VMA a runner holds over a given race distance. */
    fun sustainableFraction(distanceKm: Double): Double = when {
        distanceKm < 3.5 -> 0.98
        distanceKm < 7.5 -> 0.93
        distanceKm < 15 -> 0.88
        distanceKm < 30 -> 0.82
        else -> 0.76
    }

    fun paceFor(vma: Double, fraction: Double): Int = (3600 / (vma * fraction)).roundToInt()

    fun predictedSeconds(vma: Double, goal: GoalType): Int? {
        val distance = goal.distanceKm ?: return null
        return (distance / (vma * sustainableFraction(distance)) * 3600).roundToInt()
    }

    fun paces(vma: Double, goal: GoalType, targetSeconds: Int?): Paces {
        val tempo = paceFor(vma, 0.85)
        var race = tempo
        val distance = goal.distanceKm
        if (distance != null) {
            val predicted = paceFor(vma, sustainableFraction(distance))
            race = if (targetSeconds != null) {
                val wanted = (targetSeconds / distance).toInt()
                // Stay cautious: never train more than 5 % faster than today's level.
                maxOf(wanted, (predicted * 0.95).toInt())
            } else {
                predicted
            }
        }
        return Paces(
            easy = paceFor(vma, 0.65),
            long = paceFor(vma, 0.68),
            tempo = tempo,
            interval = paceFor(vma, 0.97),
            race = race,
        )
    }

    // Plan

    fun generate(answers: OnboardingAnswers, start: Instant = Instant.now()): TrainingPlan {
        val today = PlanCalendar.startOfDay(start)
        val weekStart = PlanCalendar.mondayOfWeek(today)
        val goal = answers.goal ?: GoalType.Regular
        val vma = estimateVma(answers)
        val paces = paces(vma, goal, answers.targetSeconds)
        val beginner = (answers.experience ?: Experience.Starting) == Experience.Starting
        // Pain, a heart condition or a long break reported in the questionnaire: start gently.
        val cautious = answers.healthFlags.any { it in listOf("pain", "heart", "break") }

        // Number of weeks: up to the race, capped at two months.
        val raceDay = raceDayWithinReach(answers, goal, weekStart)
        val weekCount = raceDay?.let { maxOf(1, PlanCalendar.daysBetween(weekStart, it) / 7 + 1) } ?: MAX_WEEKS

        val runDays = answers.runDays.filter { it in 1..7 }.distinct().sorted().ifEmpty { listOf(2, 4, 7) }
        val longDay = answers.longRunDay?.takeIf { it in runDays } ?: runDays.last()
        val otherDays = runDays.filter { it != longDay }
        val restDays = (1..7).filter { it !in runDays }

        var baseKm = when (answers.weeklyVolume ?: WeeklyVolume.Under10) {
            WeeklyVolume.NotYet -> if (beginner) 5.0 else 8.0
            WeeklyVolume.Under10 -> if (beginner) 6.0 else 10.0
            WeeklyVolume.From10To25 -> 18.0
            WeeklyVolume.Over25 -> 32.0
        }
        if (cautious) baseKm *= 0.8
        baseKm = maxOf(baseKm, runDays.size * 2.5)

        val (weeklyCap, longCap) = when (goal) {
            GoalType.FiveK -> 35.0 to 10.0
            GoalType.TenK -> 45.0 to 14.0
            GoalType.Half -> 60.0 to 19.0
            GoalType.Regular, GoalType.Shape -> 40.0 to 12.0
        }

        val weeks = (0 until weekCount).map { w ->
            val phase = phase(w, weekCount, hasRace = raceDay != null)
            var km = baseKm * 1.08.pow(w)
            if (w % 4 == 3) km *= 0.8
            if (phase == PlanPhase.Taper) km = baseKm * 1.08.pow(maxOf(0, w - 1)) * 0.6
            km = minOf(km, weeklyCap)

            val monday = PlanCalendar.addDays(weekStart, w * 7)
            fun date(day: Int) = PlanCalendar.addDays(monday, day - 1)

            val qualityCount = when {
                (beginner && phase == PlanPhase.Base) || (cautious && w < 2) -> 0
                otherDays.size >= 4 -> 2
                otherDays.size >= 2 -> 1
                else -> 0
            }

            val qualityDays = buildList {
                if (qualityCount >= 1) otherDays.firstOrNull()?.let { add(it) }
                if (qualityCount >= 2) add(otherDays[otherDays.size / 2])
            }
            val easyDays = otherDays.filter { it !in qualityDays }

            val longKm = if (runDays.size == 1) minOf(km, longCap) else minOf(km * 0.36, longCap)
            val qualityKm = km * 0.24
            val easyTotal = maxOf(0.0, km - longKm - qualityKm * qualityDays.size)
            val easyKm = if (easyDays.isEmpty()) 0.0 else maxOf(2.5, easyTotal / easyDays.size)

            var workouts = buildList {
                qualityDays.forEachIndexed { i, day ->
                    add(PlanSessions.quality(date(day), w, phase, goal, paces, budgetKm = qualityKm, variant = w + i))
                }
                for (day in easyDays) {
                    add(PlanSessions.easy(date(day), w, easyKm, paces, strides = !beginner && w % 2 == 0))
                }
                add(PlanSessions.long(date(longDay), w, longKm, paces, phase))

                // Strength and stretching, on rest days when there are some.
                val strengthDay = restDays.firstOrNull() ?: easyDays.firstOrNull() ?: longDay
                val stretchDay = restDays.getOrNull(1) ?: longDay
                if (phase != PlanPhase.Taper) add(PlanSessions.strength(date(strengthDay), w, phase))
                add(PlanSessions.stretching(date(stretchDay), w, phase))
            }

            // Drop what is before today, and replace race-day sessions by the race itself.
            workouts = workouts.filter { it.date >= today }
            if (raceDay != null) {
                workouts = workouts.filter { it.date < raceDay }
                if (raceDay >= monday && raceDay < PlanCalendar.addDays(monday, 7)) {
                    workouts = workouts + PlanSessions.race(raceDay, w, goal, paces)
                }
            }
            PlanWeek(index = w, phase = phase, startDate = monday, workouts = workouts.sortedBy { it.date })
        }

        val current = predictedSeconds(vma, goal)
        val predicted = goal.distanceKm?.let { (paces.race * it).toInt() } ?: current

        return TrainingPlan(
            goal = goal,
            createdAt = start,
            startDate = weekStart,
            raceDate = raceDay ?: if (answers.hasRace) answers.raceDate else null,
            targetSeconds = answers.targetSeconds,
            currentSeconds = current,
            predictedSeconds = predicted,
            vmaKmh = vma,
            paces = paces,
            weeks = weeks,
        )
    }

    /** The race day when there is one and it falls within [MAX_WEEKS] weeks from `weekStart`. */
    private fun raceDayWithinReach(answers: OnboardingAnswers, goal: GoalType, weekStart: Instant): Instant? {
        val raceDate = answers.raceDate
        if (!answers.hasRace || goal.isOpenEnded || raceDate == null) return null
        val race = PlanCalendar.startOfDay(raceDate)
        val days = PlanCalendar.daysBetween(weekStart, race)
        return race.takeIf { days >= 0 && days / 7 + 1 <= MAX_WEEKS }
    }

    fun phase(week: Int, count: Int, hasRace: Boolean): PlanPhase {
        if (hasRace && count >= 3 && week == count - 1) return PlanPhase.Taper
        val usable = if (hasRace && count >= 3) count - 1 else count
        val position = week.toDouble() / maxOf(1, usable)
        return when {
            position < 0.38 -> PlanPhase.Base
            position < 0.70 -> PlanPhase.Build
            else -> PlanPhase.Specific
        }
    }
}
