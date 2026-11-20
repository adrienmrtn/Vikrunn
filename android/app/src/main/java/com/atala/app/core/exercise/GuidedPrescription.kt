package com.atala.app.core.exercise

import com.atala.app.core.l10n.L
import com.atala.app.core.model.PlanPhase
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStep
import kotlin.math.roundToInt

/** Fixed pauses of a guided session, in seconds. */
object GuidedTiming {
    const val GET_READY = 5
    const val SWITCH_SIDE = 5
}

/** What a step label says about sets, reps and holds ("3 × 12", "3 × 40 s", "45 s each side"). */
data class GuidedLabelAmount(val sets: Int? = null, val reps: Int? = null, val holdSec: Int? = null) {
    companion object {
        fun parse(label: String): GuidedLabelAmount {
            val comma = label.indexOf(',')
            if (comma < 0) return GuidedLabelAmount()
            val tokens = label.substring(comma + 1).lowercase().split(" ").filter { it.isNotEmpty() }
            var sets: Int? = null
            val amount: Int?
            val unitIndex: Int
            val cross = tokens.indexOfFirst { it == "×" || it == "x" }
            val first = tokens.getOrNull(cross - 1)?.toIntOrNull()
            val second = tokens.getOrNull(cross + 1)?.toIntOrNull()
            if (cross > 0 && first != null && second != null) {
                sets = first
                amount = second
                unitIndex = cross + 2
            } else {
                amount = tokens.firstOrNull()?.toIntOrNull()
                unitIndex = 1
            }
            if (amount == null) return GuidedLabelAmount()
            return when (tokens.getOrNull(unitIndex).orEmpty()) {
                "s", "sec", "seconds" -> GuidedLabelAmount(sets = sets, holdSec = amount)
                "min", "minute", "minutes" -> GuidedLabelAmount(sets = sets, holdSec = amount * 60)
                else -> GuidedLabelAmount(sets = sets, reps = amount)
            }
        }
    }
}

/** One exercise as it is prescribed in a session: the catalog entry plus sets, reps or hold, and rest. */
class GuidedPrescription(
    val info: ExerciseInfo,
    sets: Int? = null,
    reps: Int? = null,
    holdSec: Int? = null,
    restSec: Int? = null,
) {
    val sets: Int = (sets ?: info.sets).coerceIn(1, 6)

    /** Repetitions per set (per side). Null for a timed hold. */
    val reps: Int? = when {
        reps != null -> reps.coerceIn(1, 50)
        holdSec == null -> info.reps
        else -> null
    }

    /** Hold per set (per side), used when `reps` is null. */
    val holdSec: Int = (holdSec ?: info.holdSec).coerceIn(5, 300)
    val restSec: Int = maxOf(0, restSec ?: info.restSec)

    val perSide: Boolean get() = info.perSide
    val isTimed: Boolean get() = reps == null
    private val sides: Int get() = if (perSide) 2 else 1

    /** Seconds of work for one side of one set. */
    val workSec: Int get() = reps?.let { it * maxOf(1, info.repSec) } ?: holdSec

    /** Whole exercise, with the get-ready countdown, side switches and rests. Matches the guided session. */
    val totalSec: Int
        get() {
            val oneSet = sides * workSec + (sides - 1) * GuidedTiming.SWITCH_SIDE
            return GuidedTiming.GET_READY + sets * oneSet + (sets - 1) * restSec
        }

    /** "3 × 12", "3 × 40 s", "45 s each side", in the app's language. */
    val amountText: String
        get() {
            val reps = reps
            if (reps != null) {
                if (sets > 1) return if (perSide) L("%@ × %@ each side", sets, reps) else "$sets × $reps"
                return if (perSide) L("%@ reps each side", reps) else L("%@ reps", reps)
            }
            if (sets > 1) return if (perSide) L("%@ × %@ s each side", sets, holdSec) else L("%@ × %@ s", sets, holdSec)
            return if (perSide) L("%@ s each side", holdSec) else L("%@ s", holdSec)
        }

    /** The same in English, for the saved step label (`GuidedLabelAmount.parse` reads it back). */
    private val baseAmountText: String
        get() {
            val reps = reps
            val text = if (reps != null) {
                if (sets > 1) "$sets × $reps" else "$reps reps"
            } else {
                if (sets > 1) "$sets × $holdSec s" else "$holdSec s"
            }
            return if (perSide) "$text each side" else text
        }

    val label: String get() = "${info.baseName}, $baseAmountText"

    /** The step saved in the plan for this exercise. */
    val workoutStep: WorkoutStep
        get() = WorkoutStep(kind = StepKind.Exercise, label = label, durationSec = totalSec, exerciseId = info.id)

    companion object {
        fun totalMinutes(exercises: List<GuidedPrescription>): Int =
            maxOf(1, (exercises.sumOf { it.totalSec } / 60.0).roundToInt())

        /** The five stretches, each side. Holds get a little longer as the plan advances. */
        fun stretchingSession(phase: PlanPhase): List<GuidedPrescription> {
            val hold = when (phase) {
                PlanPhase.Base -> 40
                PlanPhase.Build -> 50
                PlanPhase.Specific -> 60
                PlanPhase.Taper -> 45
            }
            return ExerciseCatalog.stretchIds.mapNotNull { id ->
                ExerciseCatalog.info(id)?.let { GuidedPrescription(it, sets = 1, holdSec = hold, restSec = 0) }
            }
        }

        /** Squats, lunges, glute bridge and plank. One more set after the base weeks, then a few more reps. */
        fun strengthSession(phase: PlanPhase): List<GuidedPrescription> {
            val (sets, amounts) = when (phase) {
                PlanPhase.Base, PlanPhase.Taper ->
                    2 to mapOf("squat" to 12, "lunge" to 8, "glute_bridge" to 12, "plank" to 30)
                PlanPhase.Build ->
                    3 to mapOf("squat" to 12, "lunge" to 10, "glute_bridge" to 15, "plank" to 40)
                PlanPhase.Specific ->
                    3 to mapOf("squat" to 15, "lunge" to 12, "glute_bridge" to 18, "plank" to 45)
            }
            return ExerciseCatalog.strengthIds.mapNotNull { id ->
                val info = ExerciseCatalog.info(id) ?: return@mapNotNull null
                val amount = amounts[id]
                if (info.reps == null) {
                    GuidedPrescription(info, sets = sets, holdSec = amount, restSec = 30)
                } else {
                    GuidedPrescription(info, sets = sets, reps = amount, restSec = 30)
                }
            }
        }

        /**
         * The exercises of a strength or stretching session, read from its steps.
         * Steps without an exercise id (older plans) are matched by their label.
         */
        fun session(workout: Workout): List<GuidedPrescription> {
            val kind = if (workout.kind == WorkoutKind.Strength) GuidedExerciseKind.Strength else GuidedExerciseKind.Stretch
            val result = workout.blocks.flatMap { block ->
                List(maxOf(1, block.repeats)) {
                    block.steps.filter { it.kind == StepKind.Exercise }.map { prescription(it, kind) }
                }.flatten()
            }
            if (result.isNotEmpty()) return result
            // A session with no exercise steps (for example a run turned into stretching by the coach).
            return if (kind == GuidedExerciseKind.Strength) strengthSession(PlanPhase.Base) else stretchingSession(PlanPhase.Base)
        }

        fun prescription(step: WorkoutStep, kind: GuidedExerciseKind): GuidedPrescription {
            val parsed = GuidedLabelAmount.parse(step.label)
            val info = ExerciseCatalog.info(step.exerciseId) ?: ExerciseCatalog.match(step.label)
                ?: ExerciseCatalog.generic(step.label, kind, step.durationSec)
            return GuidedPrescription(info, sets = parsed.sets, reps = parsed.reps, holdSec = parsed.holdSec)
        }
    }
}
