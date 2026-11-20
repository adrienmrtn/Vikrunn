package com.atala.app.core.run

import android.location.Location
import android.os.SystemClock
import com.google.android.gms.location.LocationResult
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A synthetic but realistic GPS track: a loop of a park (radius 450 m) with a gentle hill, fixes
 * once a second with correlated noise (as the fused provider gives: the error drifts, it does not
 * jump from one fix to the next), an accuracy of 4 to 8 m and an altitude. The runner's position is
 * the distance run along the loop; [fix] turns it into a [Location] stamped with the shadow clock.
 */
class GpsTrack(seed: Long = 42) {
    private val random = Random(seed)
    private val lat0 = 48.8466
    private val lon0 = 2.3376
    private val radius = 450.0
    private val metresPerDegree = 111_320.0
    private val startEpochMs = 1_790_000_000_000L
    private val startNanos = SystemClock.elapsedRealtimeNanos()

    // Slowly drifting error, metres east, north and up (AR(1), about 0.8 m standard deviation).
    private var east = 0.0
    private var north = 0.0
    private var up = 0.0

    /** Latitude and longitude of the point [along] metres into the loop, without error. */
    fun position(along: Double, eastOffset: Double = 0.0, northOffset: Double = 0.0): Pair<Double, Double> {
        val angle = along / radius
        val x = radius * sin(angle) + eastOffset
        val y = radius * (1 - cos(angle)) + northOffset
        return (lat0 + y / metresPerDegree) to (lon0 + x / (metresPerDegree * cos(lat0 * PI / 180)))
    }

    /** Altitude of the hill: ±9 m around 40 m, one wave every 1.5 km. */
    fun altitude(along: Double): Double = 40 + 9 * sin(2 * PI * along / 1500)

    /** Metres between two points of the loop, as Android measures them. */
    fun trueDistance(from: Double, to: Double): Double {
        val (lat1, lon1) = position(from)
        val (lat2, lon2) = position(to)
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0].toDouble()
    }

    /**
     * The fix the phone receives now for a runner [along] metres into the loop. [accuracy] and the
     * offsets make bad fixes; [ageSeconds] makes a cached one.
     */
    fun fix(
        along: Double,
        accuracy: Double? = null,
        eastOffset: Double = 0.0,
        northOffset: Double = 0.0,
        ageSeconds: Double = 0.0,
    ): Location {
        east = 0.9 * east + random.nextGaussian() * 0.35
        north = 0.9 * north + random.nextGaussian() * 0.35
        up = 0.9 * up + random.nextGaussian() * 0.35
        val (lat, lon) = position(along, east + eastOffset, north + northOffset)
        val nanos = SystemClock.elapsedRealtimeNanos() - (ageSeconds * 1e9).toLong()
        return Location("fused").apply {
            latitude = lat
            longitude = lon
            this.accuracy = (accuracy ?: (4 + random.nextDouble() * 4)).toFloat()
            altitude = altitude(along) + up
            verticalAccuracyMeters = 3f
            elapsedRealtimeNanos = nanos
            time = startEpochMs + (nanos - startNanos) / 1_000_000
        }
    }
}

/** Hands fixes to the tracker exactly as the fused location provider does (its callback). */
fun RunTracker.deliver(vararg locations: Location) {
    locationCallback.onLocationResult(LocationResult.create(locations.toList()))
}
