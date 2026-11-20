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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val context: Context,
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        // A copy on disk (active_run.json) at the start, on every pause and resume, and every 15 s
        // while running, so a run cut short by the end of the process can still be saved.
        scope.launch {
            tracker.snapshot.map { it.state }.distinctUntilChanged().collect { state ->
                if (state == RunTrackerState.Running || state == RunTrackerState.Paused) persist()
            }
        }
        scope.launch {
            while (isActive) {
                delay(15_000)
                if (tracker.snapshot.value.state == RunTrackerState.Running) persist()
            }
        }
    }

    /** True while the run is recorded (running or paused). */
    val isRecording: Boolean
        get() = tracker.snapshot.value.state.let { it == RunTrackerState.Running || it == RunTrackerState.Paused }

    private val title: String get() = workout?.title ?: "Free run"

    /** Writes the run so far to `active_run.json` (nothing when it is not being recorded). */
    fun persist() {
        tracker.backup(title, workout?.id)?.let { RunBackupStore.write(context, it) }
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
        if (!save) {
            RunBackupStore.delete(context)
            return null
        }
        val run = tracker.makeRecord(title = title, workoutId = workout?.id)
        store.addRun(run)
        // On disk before the copy of the run in progress goes.
        store.save()
        RunBackupStore.delete(context)
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
                if (existing.workoutId == workout?.id || existing.isRecording) return existing
                existing.release()
            }
            return ActiveRun(context.applicationContext, store, workout).also { current = it }
        }

        /** Ends the flow: stops listening and talking, forgets the run. */
        fun close(run: ActiveRun) {
            run.release()
            if (current === run) current = null
        }

        /** The app goes to the background: the run in progress is copied to disk now. */
        fun persistCurrent() {
            current?.persist()
        }

        /**
         * The run flow to show again when the app opens (after it was swiped away during a run):
         * a run being recorded, or one just finished waiting for its feeling. Null otherwise.
         */
        val resumable: ActiveRun?
            get() = current?.takeIf { run ->
                run.phase.value == RunFlowPhase.Active && run.isRecording ||
                    run.phase.value == RunFlowPhase.Feeling && run.savedRun.value != null
            }

        private val _showRequests = MutableStateFlow(0)

        /** Bumped when the "Run in progress" notification is tapped: the app shows the run flow. */
        val showRequests: StateFlow<Int> = _showRequests.asStateFlow()

        fun requestShow() {
            _showRequests.value += 1
        }
    }

    private fun release() {
        scope.cancel()
        tracker.release()
        voice.release()
    }
}
