@file:UseSerializers(InstantSerializer::class, UuidSerializer::class)

package com.atala.app.core.model

import com.atala.app.core.l10n.L
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.util.UUID

@Serializable
enum class RunSource {
    @SerialName("gps") Gps,
    @SerialName("manual") Manual,
    // Runs imported from Apple Health on the iPhone (Health Connect on Android).
    @SerialName("health") Health,
}

@Serializable
enum class Feeling {
    @SerialName("easy") Easy,
    @SerialName("good") Good,
    @SerialName("hard") Hard,
    @SerialName("pain") Pain;

    val title: String
        get() = when (this) {
            Easy -> L("Easy")
            Good -> L("Hard but good")
            Hard -> L("Very hard")
            Pain -> L("Something hurts")
        }

    val subtitle: String
        get() = when (this) {
            Easy -> L("I had more in the tank")
            Good -> L("The planned effort, no more, no less")
            Hard -> L("I finished at my limit")
            Pain -> L("I'd rather tell my coach")
        }
}

@Serializable
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val time: Instant,
    /** Altitude in metres. Null for routes recorded before altitude was kept, or when the GPS had none. */
    val alt: Double? = null,
)

/** One step as it was actually run (used for the bars in the summary). */
@Serializable
data class RunSplit(
    val id: UUID = UUID.randomUUID(),
    val label: String,
    val kind: StepKind,
    val distanceM: Double,
    val durationSec: Int,
    val paceFast: Int? = null,
    val paceSlow: Int? = null,
) {
    val pace: Int get() = if (distanceM > 0) (durationSec / (distanceM / 1000)).toInt() else 0

    val onTarget: Boolean
        get() {
            if (paceFast == null || paceSlow == null) return true
            return pace >= paceFast - 5 && pace <= paceSlow + 5
        }
}

@Serializable
data class RunRecord(
    val id: UUID = UUID.randomUUID(),
    @SerialName("workoutID") val workoutId: UUID? = null,
    val title: String,
    val date: Instant,
    val source: RunSource,
    val distanceM: Double,
    val durationSec: Int,
    val avgHeartRate: Int? = null,
    val route: List<RoutePoint> = emptyList(),
    val splits: List<RunSplit> = emptyList(),
    val feeling: Feeling? = null,
    /** Highest heart rate of the run, when the health store had one. */
    val maxHeartRate: Int? = null,
) {
    val distanceKm: Double get() = distanceM / 1000
    val avgPace: Int get() = if (distanceM > 0) (durationSec / (distanceM / 1000)).toInt() else 0
}
