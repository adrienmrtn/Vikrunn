package com.atala.app.ui.guided

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atala.app.core.exercise.GuidedPrescription
import com.atala.app.core.exercise.GuidedTiming
import com.atala.app.core.l10n.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

internal enum class GuidedSide {
    Left, Right;

    val title: String get() = if (this == Left) L("Left leg") else L("Right leg")
    val spoken: String get() = if (this == Left) L("left leg") else L("right leg")
}

internal enum class GuidedSegmentKind { GetReady, Work, SwitchSide, Rest }

/** One timed (or rep-based) slice of the session. */
internal data class GuidedSegment(
    val id: Int,
    val kind: GuidedSegmentKind,
    val exerciseIndex: Int,
    /** 1-based set number. */
    val setNumber: Int,
    /** Side being worked, or the side coming next for get-ready and switch segments. */
    val side: GuidedSide?,
    /** Null for a rep-based set, which ends when the user taps "Done". */
    val durationSec: Int?,
    val reps: Int?,
) {
    companion object {
        fun build(exercises: List<GuidedPrescription>): List<GuidedSegment> {
            val result = mutableListOf<GuidedSegment>()
            fun add(kind: GuidedSegmentKind, exercise: Int, number: Int, side: GuidedSide?, duration: Int?, reps: Int?) {
                result += GuidedSegment(result.size, kind, exercise, number, side, duration, reps)
            }
            exercises.forEachIndexed { index, exercise ->
                val sides: List<GuidedSide?> = if (exercise.perSide) listOf(GuidedSide.Left, GuidedSide.Right) else listOf(null)
                add(GuidedSegmentKind.GetReady, index, 1, sides[0], GuidedTiming.GET_READY, null)
                for (number in 1..maxOf(1, exercise.sets)) {
                    sides.forEachIndexed { position, side ->
                        if (position > 0) add(GuidedSegmentKind.SwitchSide, index, number, side, GuidedTiming.SWITCH_SIDE, null)
                        add(GuidedSegmentKind.Work, index, number, side, if (exercise.isTimed) exercise.holdSec else null, exercise.reps)
                    }
                    if (number < exercise.sets && exercise.restSec > 0) {
                        add(GuidedSegmentKind.Rest, index, number, sides[0], exercise.restSec, null)
                    }
                }
            }
            return result
        }
    }
}

internal enum class GuidedSessionState { Idle, Running, Paused, Finished }

internal sealed interface GuidedSessionEvent {
    data class SegmentStarted(val segment: GuidedSegment) : GuidedSessionEvent
    data object Halfway : GuidedSessionEvent
    data class Countdown(val seconds: Int) : GuidedSessionEvent
    data object Finished : GuidedSessionEvent
}

/**
 * Drives a guided session (`GuidedSessionEngine`). Every duration is measured against the clock
 * (elapsed real time, which keeps counting in deep sleep), never by counting ticks, so the timers
 * are still right when the app comes back from the background.
 */
internal class GuidedSessionEngine(private val scope: CoroutineScope) {
    var state by mutableStateOf(GuidedSessionState.Idle)
        private set
    var index by mutableIntStateOf(0)
        private set

    /** Refreshed several times per second while running; everything shown derives from it (ms). */
    var now by mutableLongStateOf(clock())
        private set
    var completedExercises by mutableStateOf(emptySet<Int>())
        private set
    var exercises by mutableStateOf(emptyList<GuidedPrescription>())
        private set
    var segments: List<GuidedSegment> = emptyList()
        private set
    var onEvent: ((GuidedSessionEvent) -> Unit)? = null

    private var segmentBanked = 0L
    private var segmentResumedAt: Long? = null
    private var totalBanked = 0L
    private var totalResumedAt: Long? = null
    private var timer: Job? = null
    private var halfwaySaid = false
    private var lastCountdown = 0

    private fun clock(): Long = SystemClock.elapsedRealtime()

    fun configure(exercises: List<GuidedPrescription>) {
        if (state != GuidedSessionState.Idle) return
        this.exercises = exercises
        segments = GuidedSegment.build(exercises)
    }

    // Reading

    val current: GuidedSegment? get() = segments.getOrNull(index)

    val currentExercise: GuidedPrescription? get() = current?.let { exercises.getOrNull(it.exerciseIndex) }

    val plannedSeconds: Int get() = exercises.sumOf { it.totalSec }

    /** Seconds spent in the current segment, pauses excluded. */
    val segmentElapsed: Double
        get() {
            var value = segmentBanked
            segmentResumedAt?.let { value += now - it }
            return maxOf(0L, value) / 1000.0
        }

    /** Seconds of the whole session, pauses excluded. */
    val totalElapsed: Int
        get() {
            var value = totalBanked
            totalResumedAt?.let { value += now - it }
            return (maxOf(0L, value) / 1000).toInt()
        }

    /** Whole seconds left in a timed segment (rounded up, so it reads 5, 4, 3, 2, 1). */
    val remainingSeconds: Int?
        get() {
            val duration = current?.durationSec ?: return null
            return maxOf(0, ceil(duration - segmentElapsed).toInt())
        }

    /** 0...1 for a timed segment. */
    val segmentProgress: Double
        get() {
            val duration = current?.durationSec ?: return 0.0
            if (duration <= 0) return 0.0
            return (segmentElapsed / duration).coerceIn(0.0, 1.0)
        }

    /** Rep the calm tempo is at, for a rep-based set (1...reps). */
    val pacedRep: Int?
        get() {
            val current = current ?: return null
            if (current.durationSec != null) return null
            val reps = current.reps ?: return null
            val perRep = maxOf(1, currentExercise?.info?.repSec ?: 3).toDouble()
            return minOf(reps, (segmentElapsed / perRep).toInt() + 1)
        }

    private fun firstSegmentIndex(exercise: Int): Int? =
        segments.indexOfFirst { it.exerciseIndex == exercise }.takeIf { it >= 0 }

    // Commands

    fun start() {
        if (state != GuidedSessionState.Idle || segments.isEmpty()) return
        state = GuidedSessionState.Running
        totalBanked = 0
        totalResumedAt = clock()
        startTimer()
        go(0)
    }

    fun pause() {
        if (state != GuidedSessionState.Running) return
        val date = clock()
        segmentResumedAt?.let { segmentBanked += date - it }
        totalResumedAt?.let { totalBanked += date - it }
        segmentResumedAt = null
        totalResumedAt = null
        now = date
        state = GuidedSessionState.Paused
        stopTimer()
    }

    fun resume() {
        if (state != GuidedSessionState.Paused) return
        val date = clock()
        segmentResumedAt = date
        totalResumedAt = date
        now = date
        state = GuidedSessionState.Running
        startTimer()
    }

    /** Ends the current segment as done: "Done" on a rep set, "Skip rest", or a timer reaching zero. */
    fun completeSegment() {
        if (state != GuidedSessionState.Running) return
        val current = current ?: return
        val next = index + 1
        val leavesExercise = next !in segments.indices || segments[next].exerciseIndex != current.exerciseIndex
        if (leavesExercise) completedExercises = completedExercises + current.exerciseIndex
        go(next)
    }

    /** Skips what is left of the current exercise. */
    fun nextExercise() {
        if (state != GuidedSessionState.Running) return
        val current = current ?: return
        val target = firstSegmentIndex(current.exerciseIndex + 1)
        if (target != null) go(target) else finish()
    }

    /** Restarts the current exercise, or goes back to the previous one when it has barely begun. */
    fun previousExercise() {
        if (state != GuidedSessionState.Running) return
        val current = current ?: return
        val first = firstSegmentIndex(current.exerciseIndex) ?: 0
        val previous = if (current.exerciseIndex > 0) firstSegmentIndex(current.exerciseIndex - 1) else null
        if (index == first && previous != null) go(previous) else go(first)
    }

    /** Stops the clock for good (flow closed). */
    fun shutdown() {
        stopTimer()
        if (state == GuidedSessionState.Running) pause()
    }

    // Internals

    private fun go(newIndex: Int) {
        if (newIndex !in segments.indices) {
            finish()
            return
        }
        val date = clock()
        index = newIndex
        segmentBanked = 0
        segmentResumedAt = if (state == GuidedSessionState.Running) date else null
        halfwaySaid = false
        lastCountdown = 0
        now = date
        onEvent?.invoke(GuidedSessionEvent.SegmentStarted(segments[newIndex]))
    }

    private fun finish() {
        if (state != GuidedSessionState.Running && state != GuidedSessionState.Paused) return
        val date = clock()
        totalResumedAt?.let { totalBanked += date - it }
        totalResumedAt = null
        segmentResumedAt = null
        now = date
        state = GuidedSessionState.Finished
        stopTimer()
        onEvent?.invoke(GuidedSessionEvent.Finished)
    }

    private fun startTimer() {
        stopTimer()
        timer = scope.launch {
            while (isActive) {
                delay(200)
                tick()
            }
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun tick() {
        if (state != GuidedSessionState.Running) return
        now = clock()
        val current = current ?: return
        val duration = current.durationSec ?: return
        val remaining = duration - segmentElapsed
        if (remaining <= 0) {
            completeSegment()
            return
        }
        if (current.kind != GuidedSegmentKind.Work) return
        if (!halfwaySaid && duration >= 20 && remaining > 5 && segmentElapsed >= duration / 2.0) {
            halfwaySaid = true
            onEvent?.invoke(GuidedSessionEvent.Halfway)
        }
        val secondsLeft = ceil(remaining).toInt()
        if (secondsLeft in 1..3 && secondsLeft != lastCountdown) {
            lastCountdown = secondsLeft
            onEvent?.invoke(GuidedSessionEvent.Countdown(secondsLeft))
        }
    }
}
