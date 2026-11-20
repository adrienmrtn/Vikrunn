package com.atala.app.core.run

import android.location.Location
import com.atala.app.core.model.RoutePoint
import com.atala.app.core.model.RunRecord
import java.time.Duration
import kotlin.math.roundToInt

/** One kilometre of a run (the last one can be shorter). [id] is 1 for the first kilometre. */
data class RunKmSplit(val id: Int, val distanceM: Double, val seconds: Double) {
    val pace: Int get() = if (distanceM > 0) (seconds / (distanceM / 1000)).roundToInt() else 0
    val isPartial: Boolean get() = distanceM < 995
}

/** Altitude at a point of the run, for the elevation profile. */
data class RunElevationPoint(val id: Int, val distanceM: Double, val altitudeM: Double)

/** Pace at a point of the run, for the pace chart. [pace] in seconds per kilometre. */
data class RunPacePoint(val id: Int, val distanceM: Double, val pace: Double)

/**
 * Figures worked out from the recorded route (Atala/Features/Run/RunAnalysis.swift): kilometre
 * splits, pace along the way and elevation. Everything is empty for runs without a route.
 */
class RunAnalysis(run: RunRecord) {
    val kmSplits: List<RunKmSplit>
    /** Pace along the run, smoothed. Empty for runs too short to draw. */
    val paceProfile: List<RunPacePoint>
    val elevation: List<RunElevationPoint>
    /** Total climb in metres, null when the route carries no altitude. */
    val elevationGainM: Int?

    init {
        val legs = legs(run)
        kmSplits = kmSplits(legs)
        paceProfile = paceProfile(legs)
        val profile = profile(run, legs)
        elevation = thinned(profile, 80)
        elevationGainM = if (profile.size >= 5) gain(profile.map { it.altitudeM }) else null
    }

    val fastestSplit: RunKmSplit? get() = kmSplits.filter { !it.isPartial && it.pace > 0 }.minByOrNull { it.pace }

    /** Distance and moving time between two consecutive route points, scaled to the run's totals. */
    private class Leg(val metres: Double, val seconds: Double)

    private fun legs(run: RunRecord): List<Leg> {
        val route = run.route
        if (route.size < 2 || run.distanceM <= 0 || run.durationSec <= 0) return emptyList()
        val legs = ArrayList<Leg>(route.size)
        for (index in 1 until route.size) {
            val metres = distance(route[index - 1], route[index])
            val elapsed = maxOf(0.0, Duration.between(route[index - 1].time, route[index].time).toMillis() / 1000.0)
            // A long gap with little distance is a pause: it must not count as running time.
            val longest = maxOf(10.0, metres / 0.8)
            legs += Leg(metres, minOf(elapsed, longest))
        }
        val totalMetres = legs.sumOf { it.metres }
        val totalSeconds = legs.sumOf { it.seconds }
        if (totalMetres <= 0 || totalSeconds <= 0) return emptyList()
        val distanceScale = run.distanceM / totalMetres
        val timeScale = run.durationSec / totalSeconds
        return legs.map { Leg(it.metres * distanceScale, it.seconds * timeScale) }
    }

    private fun kmSplits(legs: List<Leg>): List<RunKmSplit> {
        val splits = mutableListOf<RunKmSplit>()
        var metresDone = 0.0
        var secondsDone = 0.0
        for (leg in legs) {
            var metres = leg.metres
            var seconds = leg.seconds
            while (metres > 0.001) {
                val room = 1000 - metresDone
                if (metres >= room) {
                    val used = seconds * (room / metres)
                    splits += RunKmSplit(splits.size + 1, 1000.0, secondsDone + used)
                    metres -= room
                    seconds -= used
                    metresDone = 0.0
                    secondsDone = 0.0
                } else {
                    metresDone += metres
                    secondsDone += seconds
                    metres = 0.0
                }
            }
        }
        if (metresDone >= 100) splits += RunKmSplit(splits.size + 1, metresDone, secondsDone)
        return splits
    }

    /**
     * The run cut into about fifty equal stretches (100 m at least), each with its pace, then
     * smoothed over three stretches so the line shows the effort and not the GPS noise.
     */
    private fun paceProfile(legs: List<Leg>): List<RunPacePoint> {
        val total = legs.sumOf { it.metres }
        if (total < 500) return emptyList()
        val stretch = maxOf(100.0, total / 50)
        val distances = mutableListOf<Double>()
        val paces = mutableListOf<Double>()
        var metres = 0.0
        var seconds = 0.0
        var travelled = 0.0
        for (leg in legs) {
            metres += leg.metres
            seconds += leg.seconds
            travelled += leg.metres
            if (metres >= stretch) {
                if (seconds > 0) {
                    distances += travelled - metres / 2
                    // Slower than 20:00 per km is a stop, not a pace: keep the line readable.
                    paces += minOf(1200.0, seconds / metres * 1000)
                }
                metres = 0.0
                seconds = 0.0
            }
        }
        if (paces.size < 4) return emptyList()
        return paces.indices.map { index ->
            val window = paces.subList(maxOf(0, index - 1), minOf(paces.size - 1, index + 1) + 1)
            RunPacePoint(index, distances[index], window.average())
        }
    }

    /** Altitude along the run, smoothed: raw GPS altitude jumps by a few metres from one fix to the next. */
    private fun profile(run: RunRecord, legs: List<Leg>): List<RunElevationPoint> {
        val route = run.route
        if (legs.size != route.size - 1) return emptyList()
        val distances = mutableListOf<Double>()
        val altitudes = mutableListOf<Double>()
        var travelled = 0.0
        for (index in route.indices) {
            if (index > 0) travelled += legs[index - 1].metres
            route[index].alt?.let {
                distances += travelled
                altitudes += it
            }
        }
        if (altitudes.size < 5) return emptyList()
        return altitudes.indices.map { index ->
            val window = altitudes.subList(maxOf(0, index - 2), minOf(altitudes.size - 1, index + 2) + 1)
            RunElevationPoint(index, distances[index], window.average())
        }
    }

    /** Sum of the climbs, ignoring wobbles under two metres. */
    private fun gain(altitudes: List<Double>): Int {
        var reference = altitudes.firstOrNull() ?: return 0
        var total = 0.0
        for (altitude in altitudes) {
            if (altitude > reference + 2) {
                total += altitude - reference
                reference = altitude
            } else if (altitude < reference - 2) {
                reference = altitude
            }
        }
        return total.roundToInt()
    }

    private fun thinned(points: List<RunElevationPoint>, limit: Int): List<RunElevationPoint> {
        if (points.size <= limit || limit <= 1) return points
        val step = (points.size - 1).toDouble() / (limit - 1)
        return (0 until limit).map { points[(it * step).roundToInt()] }
    }

    companion object {
        /** Metres between two points of a route. */
        fun distance(from: RoutePoint, to: RoutePoint): Double {
            val result = FloatArray(1)
            Location.distanceBetween(from.lat, from.lon, to.lat, to.lon, result)
            return result[0].toDouble()
        }
    }
}
