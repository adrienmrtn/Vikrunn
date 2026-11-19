@file:UseSerializers(InstantSerializer::class, UuidSerializer::class)

package com.atala.app.core.model

import com.atala.app.core.l10n.L
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

@Serializable
enum class WorkoutKind {
    @SerialName("easy") Easy,
    @SerialName("intervals") Intervals,
    @SerialName("tempo") Tempo,
    @SerialName("long") Long,
    @SerialName("racePace") RacePace,
    @SerialName("strength") Strength,
    @SerialName("stretching") Stretching,
    @SerialName("race") Race,
    @SerialName("free") Free;

    val title: String
        get() = when (this) {
            Easy -> L("Easy run")
            Intervals -> L("Intervals")
            Tempo -> L("Tempo")
            Long -> L("Long run")
            RacePace -> L("Race pace")
            Strength -> L("Strength")
            Stretching -> L("Stretching")
            Race -> L("Race day")
            Free -> L("Free run")
        }

    /** True for sessions that are run (and can be tracked with GPS). */
    val isRun: Boolean get() = this != Strength && this != Stretching

    /** True for the hard sessions shown in orange. */
    val isQuality: Boolean get() = this == Intervals || this == Tempo || this == RacePace || this == Race
}

@Serializable
enum class StepKind {
    @SerialName("warmup") Warmup,
    @SerialName("work") Work,
    @SerialName("recovery") Recovery,
    @SerialName("cooldown") Cooldown,
    @SerialName("steady") Steady,
    @SerialName("exercise") Exercise;

    val title: String
        get() = when (this) {
            Warmup -> L("Warm-up")
            Work -> L("Fast")
            Recovery -> L("Recovery")
            Cooldown -> L("Cool-down")
            Steady -> L("Steady")
            Exercise -> L("Exercise")
        }
}

/** One step of a session. Either time-based or distance-based. */
@Serializable
data class WorkoutStep(
    val id: UUID = UUID.randomUUID(),
    val kind: StepKind,
    val label: String,
    val durationSec: Int? = null,
    val distanceM: Int? = null,
    /** Target pace range in seconds per kilometre (fast bound first). Null = free pace. */
    val paceFast: Int? = null,
    val paceSlow: Int? = null,
    /** Identifier of the guided exercise (see `ExerciseCatalog`). Null for running steps and older plans. */
    @SerialName("exerciseID") val exerciseId: String? = null,
) {
    /** Estimated distance in metres. */
    fun estimatedMeters(fallbackPace: Int): Double {
        if (distanceM != null) return distanceM.toDouble()
        if (durationSec == null) return 0.0
        val pace = paceSlow ?: paceFast ?: fallbackPace
        return if (pace > 0) durationSec.toDouble() / pace * 1000 else 0.0
    }

    /** Estimated duration in seconds. */
    fun estimatedSeconds(fallbackPace: Int): Int {
        if (durationSec != null) return durationSec
        if (distanceM == null) return 0
        val pace = paceSlow ?: paceFast ?: fallbackPace
        return (distanceM / 1000.0 * pace).toInt()
    }
}

/** A group of steps repeated `repeats` times (1 for warm-up, cool-down, steady runs). */
@Serializable
data class WorkoutBlock(
    val id: UUID = UUID.randomUUID(),
    val repeats: Int = 1,
    val steps: List<WorkoutStep>,
)

@Serializable
enum class WorkoutStatus {
    @SerialName("planned") Planned,
    @SerialName("done") Done,
    @SerialName("missed") Missed,
}

@Serializable
data class Workout(
    val id: UUID = UUID.randomUUID(),
    val date: Instant,
    val weekIndex: Int,
    val kind: WorkoutKind,
    val title: String,
    val subtitle: String,
    val distanceKm: Double,
    val durationMin: Int,
    val targetPace: Int? = null,
    /** Perceived effort, 1 to 10. */
    val effort: Int,
    val blocks: List<WorkoutBlock>,
    val why: String,
    val status: WorkoutStatus = WorkoutStatus.Planned,
    @SerialName("runID") val runId: UUID? = null,
    /** How a guided strength or stretching session felt. Runs keep their feeling on the run record. */
    val feeling: Feeling? = null,
) {
    /** Blocks expanded into the flat list of steps followed during the run. */
    val flatSteps: List<WorkoutStep>
        get() = blocks.flatMap { block ->
            List(maxOf(1, block.repeats)) { block.steps.map { it.copy(id = UUID.randomUUID()) } }.flatten()
        }
}

@Serializable
enum class PlanPhase {
    @SerialName("base") Base,
    @SerialName("build") Build,
    @SerialName("specific") Specific,
    @SerialName("taper") Taper;

    val title: String
        get() = when (this) {
            Base -> L("Base")
            Build -> L("Build")
            Specific -> L("Specific")
            Taper -> L("Taper")
        }

    val summary: String
        get() = when (this) {
            Base -> L("Settle the habit, run easy")
            Build -> L("More volume, first hard efforts")
            Specific -> L("Train at your goal pace")
            Taper -> L("Ease off, fresh legs")
        }
}

@Serializable
data class PlanWeek(
    val id: UUID = UUID.randomUUID(),
    val index: Int,
    val phase: PlanPhase,
    val startDate: Instant,
    val workouts: List<Workout>,
) {
    val plannedKm: Double get() = workouts.filter { it.kind.isRun }.sumOf { it.distanceKm }
    val runCount: Int get() = workouts.count { it.kind.isRun }
}

/** Training paces in seconds per kilometre. */
@Serializable
data class Paces(
    val easy: Int,
    val long: Int,
    val tempo: Int,
    val interval: Int,
    val race: Int,
)

@Serializable
data class TrainingPlan(
    val id: UUID = UUID.randomUUID(),
    val goal: GoalType,
    val createdAt: Instant,
    val startDate: Instant,
    val raceDate: Instant? = null,
    val targetSeconds: Int? = null,
    /** What the user is worth today on the goal distance. */
    val currentSeconds: Int? = null,
    /** What the plan aims for on race day. */
    val predictedSeconds: Int? = null,
    val vmaKmh: Double,
    val paces: Paces,
    val weeks: List<PlanWeek>,
) {
    val allWorkouts: List<Workout> get() = weeks.flatMap { it.workouts }.sortedBy { it.date }
    val peakKm: Double get() = weeks.maxOfOrNull { it.plannedKm } ?: 0.0
    val totalRuns: Int get() = weeks.sumOf { it.runCount }
}
