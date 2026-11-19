package com.atala.app.core.plan

import com.atala.app.core.exercise.GuidedPrescription
import com.atala.app.core.l10n.L
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.Paces
import com.atala.app.core.model.PlanPhase
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutBlock
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStep
import java.time.Instant
import kotlin.math.roundToInt

/** The sessions `PlanEngine` puts in a week (the "Sessions" part of PlanEngine.swift). */
internal object PlanSessions {
    private fun workout(
        date: Instant, week: Int, kind: WorkoutKind, title: String, subtitle: String, effort: Int,
        blocks: List<WorkoutBlock>, why: String, pace: Int?, fallbackPace: Int,
    ): Workout {
        var meters = 0.0
        var seconds = 0
        for (block in blocks) {
            val repeats = maxOf(1, block.repeats)
            for (step in block.steps) {
                meters += step.estimatedMeters(fallbackPace) * repeats
                seconds += step.estimatedSeconds(fallbackPace) * repeats
            }
        }
        return Workout(
            date = date, weekIndex = week, kind = kind, title = title, subtitle = subtitle,
            distanceKm = if (kind.isRun) (meters / 100).roundToInt() / 10.0 else 0.0,
            durationMin = maxOf(1, (seconds / 60.0).roundToInt()),
            targetPace = pace, effort = effort, blocks = blocks, why = why,
        )
    }

    fun easy(date: Instant, week: Int, km: Double, paces: Paces, strides: Boolean): Workout {
        val meters = (km * 10).roundToInt() * 100
        val step = WorkoutStep(kind = StepKind.Steady, label = L("Easy pace"), distanceM = meters,
            paceFast = paces.easy - 15, paceSlow = paces.easy + 20)
        return workout(date, week, WorkoutKind.Easy,
            title = if (strides) L("Easy run + strides") else L("Easy run"),
            subtitle = L("You should be able to talk"),
            effort = 3, blocks = listOf(WorkoutBlock(steps = listOf(step))),
            why = L("Easy kilometres build your endurance without tiring you. They are what makes the hard sessions possible."),
            pace = paces.easy, fallbackPace = paces.easy)
    }

    fun long(date: Instant, week: Int, km: Double, paces: Paces, phase: PlanPhase): Workout {
        val meters = (km * 10).roundToInt() * 100
        val step = WorkoutStep(kind = StepKind.Steady, label = L("Long run pace"), distanceM = meters,
            paceFast = paces.long - 10, paceSlow = paces.long + 25)
        return workout(date, week, WorkoutKind.Long, title = L("Long run"),
            subtitle = L("Slow and steady"),
            effort = if (phase == PlanPhase.Specific) 6 else 5, blocks = listOf(WorkoutBlock(steps = listOf(step))),
            why = L("The long run teaches your body to last. Keep it slow: the distance does the work, not the speed."),
            pace = paces.long, fallbackPace = paces.long)
    }

    fun quality(date: Instant, week: Int, phase: PlanPhase, goal: GoalType, paces: Paces,
                budgetKm: Double, variant: Int): Workout {
        val warmup = WorkoutBlock(steps = listOf(WorkoutStep(kind = StepKind.Warmup, label = L("Easy pace"),
            durationSec = 15 * 60, paceFast = paces.easy - 10, paceSlow = paces.easy + 30)))
        val cooldown = WorkoutBlock(steps = listOf(WorkoutStep(kind = StepKind.Cooldown, label = L("Free pace"),
            durationSec = 10 * 60)))
        // Kilometres left for the main set once warm-up and cool-down are counted.
        val mainKm = maxOf(1.5, budgetKm - 25.0 * 60 / paces.easy)

        fun reps(distanceM: Int, recoveryM: Int, nominal: Int, pace: Int, spread: Int): Pair<WorkoutBlock, Int> {
            val perRep = (distanceM + recoveryM) / 1000.0
            val count = minOf(nominal, maxOf(3, (mainKm / perRep).roundToInt()))
            val block = WorkoutBlock(repeats = count, steps = listOf(
                WorkoutStep(kind = StepKind.Work, label = "$distanceM m", distanceM = distanceM,
                    paceFast = pace - spread, paceSlow = pace + spread),
                WorkoutStep(kind = StepKind.Recovery, label = L("Very slow jog"), distanceM = recoveryM),
            ))
            return block to count
        }

        fun format(meters: Int) = if (meters >= 1000) "${meters / 1000} km" else "$meters m"

        when (phase) {
            PlanPhase.Base -> {
                val distance = if (variant % 2 == 0) 400 else 300
                val (block, count) = reps(distance, 200, nominal = 10, pace = paces.interval, spread = 5)
                return workout(date, week, WorkoutKind.Intervals, title = "$count × ${format(distance)}",
                    subtitle = L("Short and fast"), effort = 7, blocks = listOf(warmup, block, cooldown),
                    why = L("Short repetitions raise your top speed. Everything slower will feel easier afterwards."),
                    pace = paces.interval, fallbackPace = paces.easy)
            }
            PlanPhase.Build -> {
                if (variant % 2 == 0) {
                    val (block, count) = reps(800, 400, nominal = 6, pace = paces.interval + 8, spread = 5)
                    return workout(date, week, WorkoutKind.Intervals, title = "$count × 800 m",
                        subtitle = L("Controlled speed"), effort = 7, blocks = listOf(warmup, block, cooldown),
                        why = L("The 800s develop your VMA. This is what will make your goal pace feel comfortable."),
                        pace = paces.interval + 8, fallbackPace = paces.easy)
                }
                val count = (mainKm / 2.2).roundToInt().coerceIn(2, 3)
                val block = WorkoutBlock(repeats = count, steps = listOf(
                    WorkoutStep(kind = StepKind.Work, label = L("Tempo"), durationSec = 8 * 60,
                        paceFast = paces.tempo - 5, paceSlow = paces.tempo + 8),
                    WorkoutStep(kind = StepKind.Recovery, label = L("Very slow jog"), durationSec = 2 * 60),
                ))
                return workout(date, week, WorkoutKind.Tempo, title = L("%@ × 8 min tempo", count),
                    subtitle = L("Comfortably hard"), effort = 6, blocks = listOf(warmup, block, cooldown),
                    why = L("Tempo running teaches you to hold a strong pace for a long time without blowing up."),
                    pace = paces.tempo, fallbackPace = paces.easy)
            }
            PlanPhase.Specific -> {
                val (distance, nominal) = when (goal) {
                    GoalType.FiveK -> 1000 to 5
                    GoalType.TenK -> 2000 to 4
                    GoalType.Half -> 3000 to 3
                    GoalType.Regular, GoalType.Shape -> 1000 to 4
                }
                val (block, count) = reps(distance, 400, nominal = nominal, pace = paces.race, spread = 5)
                return workout(date, week, WorkoutKind.RacePace, title = L("%@ × %@ at race pace", count, format(distance)),
                    subtitle = L("Learn the pace of the day"), effort = 7, blocks = listOf(warmup, block, cooldown),
                    why = L("Running at your goal pace now means no surprises on race day: your legs will know it by heart."),
                    pace = paces.race, fallbackPace = paces.easy)
            }
            PlanPhase.Taper -> {
                val block = WorkoutBlock(repeats = 4, steps = listOf(
                    WorkoutStep(kind = StepKind.Work, label = "400 m", distanceM = 400,
                        paceFast = paces.race - 5, paceSlow = paces.race + 5),
                    WorkoutStep(kind = StepKind.Recovery, label = L("Very slow jog"), distanceM = 200),
                ))
                return workout(date, week, WorkoutKind.RacePace, title = L("4 × 400 m at race pace"),
                    subtitle = L("A reminder, nothing more"), effort = 5, blocks = listOf(warmup, block, cooldown),
                    why = L("A short reminder of race pace to stay sharp while your body recovers."),
                    pace = paces.race, fallbackPace = paces.easy)
            }
        }
    }

    /**
     * Strength and stretching are built from `ExerciseCatalog`, with a light progression by phase.
     * One step per exercise: its label carries the sets and reps, its duration includes the rests.
     */
    fun strength(date: Instant, week: Int, phase: PlanPhase): Workout {
        val exercises = GuidedPrescription.strengthSession(phase)
        val minutes = GuidedPrescription.totalMinutes(exercises)
        return workout(date, week, WorkoutKind.Strength, title = L("Strength"), subtitle = L("Bodyweight, %@ min", minutes),
            effort = 4, blocks = listOf(WorkoutBlock(steps = exercises.map { it.workoutStep })),
            why = L("Stronger legs and core help you hold your form and keep your stride efficient when you tire."),
            pace = null, fallbackPace = 360)
    }

    fun stretching(date: Instant, week: Int, phase: PlanPhase): Workout {
        val exercises = GuidedPrescription.stretchingSession(phase)
        val minutes = GuidedPrescription.totalMinutes(exercises)
        return workout(date, week, WorkoutKind.Stretching, title = L("Stretching"), subtitle = L("Gentle, %@ min", minutes),
            effort = 1, blocks = listOf(WorkoutBlock(steps = exercises.map { it.workoutStep })),
            why = L("A few gentle minutes help you unwind after your runs and keep your range of motion."),
            pace = null, fallbackPace = 360)
    }

    fun race(date: Instant, week: Int, goal: GoalType, paces: Paces): Workout {
        val meters = ((goal.distanceKm ?: 5.0) * 1000).roundToInt()
        val step = WorkoutStep(kind = StepKind.Work, label = goal.title, distanceM = meters,
            paceFast = paces.race - 5, paceSlow = paces.race + 5)
        return workout(date, week, WorkoutKind.Race, title = goal.title,
            subtitle = L("This is your day"), effort = 9, blocks = listOf(WorkoutBlock(steps = listOf(step))),
            why = L("Everything you did leads here. Start calm, hold your pace, and finish strong."),
            pace = paces.race, fallbackPace = paces.race)
    }
}
