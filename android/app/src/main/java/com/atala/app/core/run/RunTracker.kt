package com.atala.app.core.run

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.RoutePoint
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSource
import com.atala.app.core.model.RunSplit
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutStep
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class RunTrackerState { Idle, Running, Paused, Finished }

enum class RunGpsQuality { Unknown, Denied, Poor, Fair, Good }

/** What the tracker tells the voice coach. */
sealed interface RunTrackerEvent {
    data class StepStarted(val index: Int) : RunTrackerEvent
    data class Kilometre(val count: Int) : RunTrackerEvent
    data object PaceTick : RunTrackerEvent
    data object SessionComplete : RunTrackerEvent
}

/** Everything the run screens show, as one value. */
data class RunTrackerSnapshot(
    val state: RunTrackerState = RunTrackerState.Idle,
    /** Seconds of running, pauses excluded. */
    val elapsed: Int = 0,
    /** Metres. */
    val distance: Double = 0.0,
    /** Seconds per kilometre, smoothed over the last seconds. 0 = unknown. */
    val currentPace: Int = 0,
    val gps: RunGpsQuality = RunGpsQuality.Unknown,
    /** Horizontal accuracy of the last fix, in metres. */
    val accuracy: Double? = null,
    val isSimulating: Boolean = false,
    val stepIndex: Int = 0,
    val stepElapsed: Int = 0,
    val stepDistance: Double = 0.0,
    /** True once the last step of the session has been completed (the run goes on freely). */
    val stepsComplete: Boolean = false,
    /** Number of points of the route so far. */
    val routeCount: Int = 0,
) {
    /** Seconds per kilometre since the start. 0 = unknown. */
    val averagePace: Int get() = if (distance >= 20 && elapsed > 0) (elapsed / (distance / 1000)).toInt() else 0
}

/**
 * Records a run (Atala/Services/RunTracker.swift and RunTracker+Location.swift): time, distance,
 * pace, route and the steps of a structured session. No auto-pause: only the runner pauses, resumes
 * and stops. Fused location, high accuracy, about one fix a second. Main thread only.
 * While a run is recorded, [RunTrackingService] keeps the process in the foreground, so the run goes
 * on with the screen off.
 */
class RunTracker(context: Context) {
    private val context = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(this.context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _snapshot = MutableStateFlow(RunTrackerSnapshot())
    val snapshot: StateFlow<RunTrackerSnapshot> = _snapshot.asStateFlow()
    private val now: RunTrackerSnapshot get() = _snapshot.value

    var steps: List<WorkoutStep> = emptyList()
        private set
    private val splits = mutableListOf<RunSplit>()
    private val routePoints = mutableListOf<RoutePoint>()
    val route: List<RoutePoint> get() = routePoints.toList()
    var startDate: Instant? = null
        private set

    var onEvent: ((RunTrackerEvent) -> Unit)? = null

    // Location bookkeeping.
    private var lastPoint: Location? = null
    private var lastPointNanos: Long? = null
    private var lastFixAt: Long? = null
    private var lastRoutePoint: Location? = null
    private val paceSamples = ArrayDeque<Pair<Double, Double>>()
    private var lastSimulationAt: Long? = null
    private var listening = false

    // Clock (SystemClock.elapsedRealtime, so it stays right with the screen off).
    private var resumedAt: Long? = null
    private var accumulated = 0.0
    private var timer: Job? = null
    private var stepStartTime = 0.0
    private var stepStartDistance = 0.0
    private var lastKilometre = 0

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            ingest(result.locations)
        }
    }

    // Derived values

    /** Exact running time in seconds, computed from the clock so it stays right in the background. */
    val activeTime: Double
        get() = resumedAt?.let { accumulated + (SystemClock.elapsedRealtime() - it) / 1000.0 } ?: accumulated

    val hasSteps: Boolean get() = steps.isNotEmpty()

    val currentStep: WorkoutStep?
        get() = if (!now.stepsComplete) steps.getOrNull(now.stepIndex) else null

    val stepRemainingSeconds: Int? get() = currentStep?.durationSec?.let { maxOf(0, it - now.stepElapsed) }

    val stepRemainingMeters: Double? get() = currentStep?.distanceM?.let { maxOf(0.0, it - now.stepDistance) }

    val canSkipStep: Boolean get() = hasSteps && !now.stepsComplete

    private val cooldownIndex: Int?
        get() {
            if (now.stepsComplete) return null
            return steps.indices.firstOrNull { it > now.stepIndex && steps[it].kind == StepKind.Cooldown }
        }

    val canSkipToCooldown: Boolean get() = cooldownIndex != null

    // Lifecycle

    /** Sets the session to follow. Null = free run, no steps. */
    fun configure(workout: Workout?) {
        if (now.state != RunTrackerState.Idle) return
        steps = workout?.flatSteps.orEmpty().filter { it.kind != StepKind.Exercise }
    }

    /**
     * Starts listening when the permission is there, so the ready screen can show the signal. Without
     * it the signal stays "looking" until the runner answers the request ([authorizationChanged]).
     */
    fun prepare() {
        if (hasPermission(context)) authorizationChanged(true)
        scheduleSimulatedSignal()
    }

    /** The runner answered the permission request (or came back from the settings). */
    fun authorizationChanged(granted: Boolean) {
        if (!granted) {
            if (!now.isSimulating) _snapshot.update { it.copy(gps = RunGpsQuality.Denied) }
            return
        }
        if (now.gps == RunGpsQuality.Denied) _snapshot.update { it.copy(gps = RunGpsQuality.Unknown) }
        if (now.state != RunTrackerState.Finished) startUpdates()
        // Allowed during the run (back from the settings): the run can now go on with the screen off.
        if (isRecording) RunTrackingService.start(context)
    }

    fun start() {
        if (now.state != RunTrackerState.Idle) return
        val clock = SystemClock.elapsedRealtime()
        startDate = Instant.now()
        resumedAt = clock
        accumulated = 0.0
        lastPoint = null
        lastPointNanos = null
        lastSimulationAt = null
        _snapshot.update { it.copy(state = RunTrackerState.Running) }
        // A location service needs the location permission; without it only the clock runs, and the
        // time stays right with the screen off because it is read from the system clock.
        if (hasPermission(context)) {
            RunTrackingService.start(context)
            startUpdates()
        }
        startTimer()
        if (hasSteps) onEvent?.invoke(RunTrackerEvent.StepStarted(0))
    }

    fun pause() {
        if (now.state != RunTrackerState.Running) return
        accumulated = activeTime
        resumedAt = null
        _snapshot.update { it.copy(state = RunTrackerState.Paused) }
        stopTimer()
        resetPace()
        refreshClock()
    }

    fun resume() {
        if (now.state != RunTrackerState.Paused) return
        resumedAt = SystemClock.elapsedRealtime()
        lastPoint = null
        lastPointNanos = null
        lastSimulationAt = null
        _snapshot.update { it.copy(state = RunTrackerState.Running) }
        startTimer()
    }

    /** Ends the run. Call [makeRecord] afterwards. */
    fun stop() {
        if (now.state != RunTrackerState.Running && now.state != RunTrackerState.Paused) return
        accumulated = activeTime
        resumedAt = null
        refreshClock()
        if (hasSteps && !now.stepsComplete) closeCurrentStep()
        _snapshot.update { it.copy(state = RunTrackerState.Finished) }
        shutdown()
    }

    /** Stops the timer, the location updates and the foreground service (also when the ready screen is closed). */
    fun shutdown() {
        stopTimer()
        stopUpdates()
        RunTrackingService.stop(context)
    }

    /** Releases everything for good (the flow is over). */
    fun release() {
        shutdown()
        onEvent = null
        scope.cancel()
    }

    // Steps

    fun skipStep() {
        if (!canSkipStep || !isRecording) return
        advance(now.stepIndex + 1)
    }

    fun skipToCooldown() {
        val index = cooldownIndex ?: return
        if (!isRecording) return
        advance(index)
    }

    private val isRecording: Boolean
        get() = now.state == RunTrackerState.Running || now.state == RunTrackerState.Paused

    private fun advance(next: Int) {
        closeCurrentStep()
        stepStartTime = activeTime
        stepStartDistance = now.distance
        if (next >= steps.size) {
            _snapshot.update { it.copy(stepElapsed = 0, stepDistance = 0.0, stepsComplete = true) }
            onEvent?.invoke(RunTrackerEvent.SessionComplete)
        } else {
            _snapshot.update { it.copy(stepElapsed = 0, stepDistance = 0.0, stepIndex = next) }
            onEvent?.invoke(RunTrackerEvent.StepStarted(next))
        }
    }

    /** Records the current step as it was actually run. */
    private fun closeCurrentStep() {
        currentStepSplit()?.let { splits += it }
    }

    /** The current step as run so far, null when there is none or it has barely started. */
    private fun currentStepSplit(): RunSplit? {
        val step = steps.getOrNull(now.stepIndex) ?: return null
        val seconds = (activeTime - stepStartTime).roundToInt()
        if (seconds < 1) return null
        return RunSplit(
            label = step.label, kind = step.kind,
            distanceM = maxOf(0.0, now.distance - stepStartDistance),
            durationSec = seconds, paceFast = step.paceFast, paceSlow = step.paceSlow,
        )
    }

    private fun checkStepCompletion() {
        val step = currentStep ?: return
        var reached = false
        step.durationSec?.let { if (activeTime - stepStartTime >= it) reached = true }
        step.distanceM?.let { if (now.distance - stepStartDistance >= it) reached = true }
        if (reached) advance(now.stepIndex + 1)
    }

    // Clock

    private fun startTimer() {
        stopTimer()
        timer = scope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun tick() {
        if (now.state != RunTrackerState.Running) return
        simulateIfNeeded()
        refreshSignalAge()
        refreshProgress()
        onEvent?.invoke(RunTrackerEvent.PaceTick)
    }

    private fun refreshClock() {
        val time = activeTime
        val seconds = time.toInt()
        val inStep = maxOf(0, (time - stepStartTime).toInt())
        val metres = maxOf(0.0, now.distance - stepStartDistance)
        _snapshot.update { it.copy(elapsed = seconds, stepElapsed = inStep, stepDistance = metres) }
    }

    /** Updates the clock, the step and the kilometre counter. Called every second and after each fix. */
    private fun refreshProgress() {
        if (now.state != RunTrackerState.Running) return
        refreshClock()
        checkStepCompletion()
        val kilometre = (now.distance / 1000).toInt()
        if (kilometre > lastKilometre) {
            lastKilometre = kilometre
            onEvent?.invoke(RunTrackerEvent.Kilometre(kilometre))
        }
        val last = paceSamples.lastOrNull()
        if (last != null && activeTime - last.first > 8 && now.currentPace != 0) {
            _snapshot.update { it.copy(currentPace = 0) }
        }
    }

    private fun resetPace() {
        paceSamples.clear()
        if (now.currentPace != 0) _snapshot.update { it.copy(currentPace = 0) }
    }

    // Location

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        if (listening || !hasPermission(context)) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            listening = true
        } catch (_: SecurityException) {
            listening = false
        }
    }

    private fun stopUpdates() {
        if (!listening) return
        client.removeLocationUpdates(callback)
        listening = false
    }

    private fun ingest(locations: List<Location>) {
        if (now.state == RunTrackerState.Finished) return
        for (location in locations) ingest(location)
        refreshProgress()
    }

    private fun ingest(location: Location) {
        // Ignore invalid and cached fixes.
        if (!location.hasAccuracy()) return
        val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1e9
        if (abs(age) >= 15) return
        val accuracy = location.accuracy.toDouble()
        lastFixAt = SystemClock.elapsedRealtime()
        _snapshot.update { it.copy(isSimulating = false, accuracy = accuracy, gps = quality(accuracy)) }

        if (now.state != RunTrackerState.Running || accuracy > 30) return
        val previous = lastPoint
        val previousNanos = lastPointNanos
        if (previous == null || previousNanos == null) {
            // First point after a start or a resume: nothing to measure yet.
            lastPoint = location
            lastPointNanos = location.elapsedRealtimeNanos
            appendRoute(location, force = true)
            return
        }
        val seconds = (location.elapsedRealtimeNanos - previousNanos) / 1e9
        if (seconds <= 0) return
        val metres = location.distanceTo(previous).toDouble()
        // Standing still: GPS jitter must not add distance.
        if (metres < 1.5) return
        // Faster than 9 m/s (about 1:50 per km) is a GPS jump, not a runner.
        if (metres / seconds > 9) return
        lastPoint = location
        lastPointNanos = location.elapsedRealtimeNanos
        addDistance(metres, location)
    }

    /** Marks the signal as lost when nothing arrived for a while. */
    private fun refreshSignalAge() {
        val gps = now.gps
        if (now.isSimulating || gps == RunGpsQuality.Denied || gps == RunGpsQuality.Unknown) return
        val last = lastFixAt ?: return
        if (SystemClock.elapsedRealtime() - last > 12_000) {
            _snapshot.update { it.copy(gps = RunGpsQuality.Unknown, accuracy = null) }
        }
    }

    private fun addDistance(metres: Double, location: Location) {
        val distance = now.distance + metres
        _snapshot.update { it.copy(distance = distance) }
        paceSamples.addLast(activeTime to distance)
        updatePace()
        appendRoute(location, force = false)
    }

    /** Pace over the last ~14 seconds of running. */
    private fun updatePace() {
        val time = activeTime
        while (paceSamples.isNotEmpty() && time - paceSamples.first().first > 14) paceSamples.removeFirst()
        val first = paceSamples.firstOrNull() ?: return
        val last = paceSamples.last()
        val seconds = last.first - first.first
        val metres = last.second - first.second
        if (seconds < 3 || metres < 4) return
        val pace = seconds / metres * 1000
        // Slower than 20:00 per km is walking around, not a pace worth showing.
        val rounded = if (pace < 1200) pace.roundToInt() else 0
        if (rounded != now.currentPace) _snapshot.update { it.copy(currentPace = rounded) }
    }

    /** Keeps one route point every ~8 metres so the saved run stays light. */
    private fun appendRoute(location: Location, force: Boolean) {
        val lastRoute = lastRoutePoint
        if (!force && lastRoute != null && location.distanceTo(lastRoute) < 8) return
        lastRoutePoint = location
        routePoints += RoutePoint(
            lat = location.latitude,
            lon = location.longitude,
            time = Instant.ofEpochMilli(location.time),
            alt = altitude(location),
        )
        _snapshot.update { it.copy(routeCount = routePoints.size) }
    }

    /** Metres above sea level when the fix has a usable altitude. */
    private fun altitude(location: Location): Double? {
        if (!location.hasAltitude()) return null
        if (location.hasVerticalAccuracy() && location.verticalAccuracyMeters <= 0f) return null
        if (Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude()) return location.mslAltitudeMeters
        return location.altitude
    }

    // Emulator mode: synthetic progress when no location arrives (the iPhone simulator's mode).

    private fun scheduleSimulatedSignal() {
        if (!simulationAllowed) return
        scope.launch {
            delay(1500)
            if (lastFixAt == null && now.state != RunTrackerState.Finished) {
                _snapshot.update { it.copy(isSimulating = true, gps = RunGpsQuality.Good, accuracy = 5.0) }
            }
        }
    }

    private fun simulateIfNeeded() {
        if (!simulationAllowed || now.state != RunTrackerState.Running) return
        val clock = SystemClock.elapsedRealtime()
        val lastFix = lastFixAt
        if (lastFix != null && clock - lastFix < 3000) {
            lastSimulationAt = null
            return
        }
        val previous = lastSimulationAt
        lastSimulationAt = clock
        if (previous == null) {
            _snapshot.update { it.copy(isSimulating = true, gps = RunGpsQuality.Good, accuracy = 5.0) }
            return
        }
        val seconds = minOf(2.0, (clock - previous) / 1000.0)
        if (seconds <= 0) return
        val pace = simulatedPace() * (0.97 + Math.random() * 0.06)
        val metres = seconds * 1000 / maxOf(120.0, pace)
        // Runs around a 250 m circle so the summary has a route to draw.
        val radius = 250.0
        val angle = (now.distance + metres) / radius
        val baseLat = 48.8606
        val baseLon = 2.3376
        val location = Location("simulated").apply {
            latitude = baseLat + radius * cos(angle) / 111_320
            longitude = baseLon + radius * sin(angle) / (111_320 * cos(baseLat * Math.PI / 180))
            // A gentle hill on each lap, so the summary has an elevation profile to draw.
            altitude = 38 + 7 * sin(angle)
            accuracy = 5f
            verticalAccuracyMeters = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        addDistance(metres, location)
    }

    private fun simulatedPace(): Int {
        val step = currentStep ?: return 345
        val fast = step.paceFast
        val slow = step.paceSlow
        if (fast != null && slow != null) return (fast + slow) / 2
        return when (step.kind) {
            StepKind.Work -> 270
            StepKind.Recovery -> 420
            StepKind.Warmup, StepKind.Cooldown -> 370
            else -> 345
        }
    }

    // Result

    /**
     * A copy of the run so far, for `active_run.json` (the step in progress counts as run up to
     * now). Null when nothing is being recorded.
     */
    fun backup(title: String, workoutId: UUID?): RunBackup? {
        if (!isRecording) return null
        val start = startDate ?: return null
        val open = if (hasSteps && !now.stepsComplete) currentStepSplit() else null
        return RunBackup(
            startDate = start,
            activeSeconds = activeTime,
            distance = now.distance,
            route = routePoints.toList(),
            splits = splits.toList() + listOfNotNull(open),
            stepIndex = now.stepIndex,
            workoutId = workoutId,
            title = title,
            isPaused = now.state == RunTrackerState.Paused,
            savedAt = Instant.now(),
        )
    }

    /** Builds the record of the finished run. */
    fun makeRecord(title: String, workoutId: UUID?, feeling: Feeling? = null): RunRecord = RunRecord(
        workoutId = workoutId,
        title = title,
        date = startDate ?: Instant.now(),
        source = RunSource.Gps,
        distanceM = now.distance,
        durationSec = accumulated.roundToInt(),
        avgHeartRate = null,
        route = routePoints.toList(),
        splits = splits.toList(),
        feeling = feeling,
    )

    companion object {
        fun quality(accuracy: Double): RunGpsQuality = when {
            accuracy <= 12 -> RunGpsQuality.Good
            accuracy <= 30 -> RunGpsQuality.Fair
            else -> RunGpsQuality.Poor
        }

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /**
         * Precise location granted. Approximate location alone (Android 12+) gives fixes kilometres
         * wide, all filtered out: the run would record no distance.
         */
        fun hasPreciseLocation(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Emulator only: synthetic progress when no location arrives. */
        val simulationAllowed: Boolean by lazy {
            Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
                Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android SDK built for") ||
                Build.PRODUCT.contains("sdk_gphone") || Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")
        }
    }
}
