package com.atala.app.core.run

import android.content.Context
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.ProAccess
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.Workout
import com.atala.app.core.store.AppStore
import com.atala.app.core.voice.VoiceClip
import com.atala.app.core.voice.VoiceCoach
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Where the run flow is (RunFlowPhase in RunFlowView.swift). */
enum class RunFlowPhase { Ready, Countdown, Active, Feeling }

/**
 * The run being prepared or recorded, kept for the whole process so that it survives the screen
 * turning off, the activity being rebuilt and the run screens leaving composition. The run flow
 * screen picks it up again ([obtain]); the voice coach hears the tracker even when no screen is shown.
 */
class ActiveRun private constructor(
    context: Context,
    private val store: AppStore,
    val workout: Workout?,
) {
    val workoutId: UUID? get() = workout?.id
    val tracker = RunTracker(context)
    val voice = VoiceCoach(context)

    private val _phase = MutableStateFlow(RunFlowPhase.Ready)
    val phase: StateFlow<RunFlowPhase> = _phase.asStateFlow()

    private val _savedRun = MutableStateFlow<RunRecord?>(null)
    /** The run once saved (exactly once, when the runner finishes and keeps it). */
    val savedRun: StateFlow<RunRecord?> = _savedRun.asStateFlow()

    private val _events = MutableSharedFlow<RunTrackerEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Tracker events, for the haptics of the screen that is showing. */
    val events: SharedFlow<RunTrackerEvent> = _events.asSharedFlow()

    init {
        tracker.configure(workout)
        voice.coachId = store.current.coach.id
        tracker.onEvent = { event ->
            _events.tryEmit(event)
            voice.handle(event, tracker, ProAccess.voice(store.current.settings.voice))
        }
    }

    fun setPhase(phase: RunFlowPhase) {
        _phase.value = phase
    }

    /** End of the countdown: the run starts. */
    fun begin() {
        if (_phase.value != RunFlowPhase.Countdown) return
        if (!tracker.hasSteps) {
            val line = L("Off you go. Run the way you feel.")
            voice.say(listOf(VoiceClip.Start), line, line, ProAccess.voice(store.current.settings.voice))
        }
        tracker.start()
        _phase.value = RunFlowPhase.Active
    }

    /** Stops the recording. The run is saved right away so nothing is lost if the app is closed. */
    fun finish(save: Boolean): RunRecord? {
        if (_savedRun.value != null) return _savedRun.value
        tracker.stop()
        voice.stop()
        if (!save) return null
        val run = tracker.makeRecord(title = workout?.title ?: "Free run", workoutId = workout?.id)
        store.addRun(run)
        _savedRun.value = run
        _phase.value = RunFlowPhase.Feeling
        return run
    }

    /** The answer of the feeling screen, saved on the run. */
    fun saveFeeling(feeling: Feeling?): RunRecord? {
        val run = _savedRun.value ?: return null
        store.updateRun(run.id) { it.copy(feeling = feeling) }
        val updated = run.copy(feeling = feeling)
        _savedRun.value = updated
        return updated
    }

    companion object {
        /** The run of the process, null when no run flow is open. */
        var current: ActiveRun? = null
            private set

        /**
         * The run of this session: the one already open when it matches (coming back to the flow),
         * a new one otherwise. A run that is being recorded is never replaced.
         */
        fun obtain(context: Context, store: AppStore, workout: Workout?): ActiveRun {
            current?.let { existing ->
                val recording = existing.tracker.snapshot.value.state.let {
                    it == RunTrackerState.Running || it == RunTrackerState.Paused
                }
                if (existing.workoutId == workout?.id || recording) return existing
                existing.release()
            }
            return ActiveRun(context.applicationContext, store, workout).also { current = it }
        }

        /** Ends the flow: stops listening and talking, forgets the run. */
        fun close(run: ActiveRun) {
            run.release()
            if (current === run) current = null
        }
    }

    private fun release() {
        tracker.release()
        voice.release()
    }
}
