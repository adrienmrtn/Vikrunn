package com.atala.app.ui.guided

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import com.atala.app.core.exercise.ExerciseInfo
import com.atala.app.core.exercise.GuidedPrescription
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.ProAccess
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.voice.VoiceClip
import com.atala.app.core.voice.VoiceCoach
import com.atala.app.ui.components.ChoiceDialog
import com.atala.app.ui.components.DialogChoice
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.Haptics
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import java.util.UUID

private enum class GuidedFlowPhase { Ready, Active, Finish }

/**
 * Guided strength or stretching session (Atala/Features/Guided/GuidedSessionView.swift), from
 * "Start" to the summary. It marks the session as done and closes itself with `nav.onBack`.
 */
@Composable
fun GuidedSessionScreen(workoutId: UUID, nav: Navigation) {
    val store = LocalAppStore.current
    val state by collectAppState()
    // The session as it was when the flow opened, like the iPhone's `let workout`.
    val workout = remember(workoutId) { store.current.workout(workoutId) }
    if (workout == null) {
        LaunchedEffect(Unit) { nav.onBack() }
        return
    }

    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val view = LocalView.current
    val engine = remember { GuidedSessionEngine(scope).apply { configure(GuidedPrescription.session(workout)) } }
    val voice = remember { VoiceCoach() }
    var phase by remember { mutableStateOf(GuidedFlowPhase.Ready) }
    var confirmEnd by remember { mutableStateOf(false) }
    var howTo by remember { mutableStateOf<ExerciseInfo?>(null) }
    /** True while the clock is stopped only because the instructions are open. */
    var pausedForHowTo by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    val onBack by rememberUpdatedState(nav.onBack)

    fun teardown() {
        engine.shutdown()
        voice.stop()
        view.keepScreenOn = false
    }

    fun close() {
        if (closed) return
        closed = true
        teardown()
        onBack()
    }

    /** "Done" on the summary: the session is marked as done, with the feeling when one was given. */
    fun complete(feeling: Feeling?) {
        if (feeling != null) store.updateWorkout(workout.id) { it.copy(feeling = feeling) }
        store.setStatus(WorkoutStatus.Done, workout.id)
        close()
    }

    /** Ending early, after the confirmation. */
    fun leave(markDone: Boolean) {
        if (markDone) store.setStatus(WorkoutStatus.Done, workout.id)
        close()
    }

    fun begin() {
        if (phase != GuidedFlowPhase.Ready || engine.exercises.isEmpty()) return
        haptics.play(HapticKind.Strong)
        view.keepScreenOn = true
        phase = GuidedFlowPhase.Active
        engine.start()
    }

    fun openHowTo(info: ExerciseInfo) {
        // The clock waits while the instructions are open, and starts again when the sheet closes.
        if (engine.state != GuidedSessionState.Running) return
        pausedForHowTo = true
        engine.pause()
        howTo = info
    }

    fun closeHowTo() {
        howTo = null
        if (!pausedForHowTo) return
        engine.resume()
        pausedForHowTo = false
    }

    DisposableEffect(engine) {
        engine.onEvent = { event ->
            handle(event, engine, voice, haptics, ProAccess.voice(store.current.settings.voice))
            if (event is GuidedSessionEvent.Finished) {
                view.keepScreenOn = false
                howTo = null
                pausedForHowTo = false
                phase = GuidedFlowPhase.Finish
            }
        }
        onDispose {
            engine.onEvent = null
            teardown()
            // The phone's voice engine (TextToSpeech) belongs to this screen: free it.
            voice.release()
        }
    }

    // System back: close before starting, pause while running, ask before ending, finish on the summary.
    BackHandler {
        when (phase) {
            GuidedFlowPhase.Ready -> close()
            GuidedFlowPhase.Active ->
                if (engine.state == GuidedSessionState.Running) {
                    haptics.confirm()
                    engine.pause()
                } else {
                    confirmEnd = true
                }
            GuidedFlowPhase.Finish -> complete(null)
        }
    }

    val pausedScreen = phase == GuidedFlowPhase.Active && engine.state == GuidedSessionState.Paused && !pausedForHowTo
    val screen = when {
        phase == GuidedFlowPhase.Ready -> 0
        phase == GuidedFlowPhase.Finish -> 3
        pausedScreen -> 2
        else -> 1
    }
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
        label = "guided",
        modifier = Modifier.fillMaxSize().background(Color.White),
    ) { target ->
        when (target) {
            0 -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                GuidedReadyScreen(
                    workout, engine.exercises,
                    onStart = { begin() },
                    onClose = { close() },
                    onOpenPaywall = nav.onOpenPaywall,
                )
            }
            1 -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                GuidedExerciseScreen(engine, onPause = { engine.pause() }, onHowTo = { openHowTo(it) })
            }
            2 -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                GuidedPauseScreen(engine, state.coach, onResume = { engine.resume() }, onEnd = { confirmEnd = true })
            }
            else -> GuidedFinishScreen(
                workout = workout,
                elapsedSec = engine.totalElapsed,
                doneCount = engine.completedExercises.size,
                totalCount = engine.exercises.size,
                coach = state.coach,
                onDone = { complete(it) },
            )
        }
    }

    howTo?.let { info -> GuidedHowToSheet(info, onDismiss = { closeHowTo() }) }

    if (confirmEnd) {
        ChoiceDialog(
            title = L("End this session?"),
            message = L("You completed %@ of %@ exercises.", engine.completedExercises.size, engine.exercises.size),
            choices = listOf(
                DialogChoice(L("Mark as done anyway")) {
                    haptics.play(HapticKind.Success)
                    leave(markDone = true)
                },
                DialogChoice(L("Leave without saving"), destructive = true) {
                    haptics.play(HapticKind.Warning)
                    leave(markDone = false)
                },
            ),
            onDismiss = { confirmEnd = false },
            cancelTitle = L("Keep going"),
        )
    }
}

/** Voice and haptics for what the engine reports. */
private fun handle(
    event: GuidedSessionEvent,
    engine: GuidedSessionEngine,
    voice: VoiceCoach,
    haptics: Haptics,
    settings: com.atala.app.core.model.VoiceSettings,
) {
    when (event) {
        is GuidedSessionEvent.SegmentStarted -> {
            val segment = event.segment
            // The first segment starts with the session, which has its own (strong) haptic.
            if (segment.id > 0) haptics.play(if (segment.kind == GuidedSegmentKind.Work) HapticKind.Rigid else HapticKind.Tap)
            val exercise = engine.exercises.getOrNull(segment.exerciseIndex) ?: return
            val line = GuidedWording.announcement(segment, exercise)
            voice.say(GuidedWording.clips(segment, exercise), speech = line, display = line, settings = settings, interrupt = true)
        }
        GuidedSessionEvent.Halfway -> {
            haptics.tap()
            voice.say(listOf(VoiceClip.Halfway), speech = L("Halfway."), display = L("Halfway."), settings = settings)
        }
        is GuidedSessionEvent.Countdown -> {
            // Light, light, medium, as before a run.
            haptics.play(if (event.seconds == 1) HapticKind.Confirm else HapticKind.Tap)
            voice.say(listOf(VoiceClip.Count(event.seconds)), speech = "${event.seconds}", display = "${event.seconds}",
                settings = settings, interrupt = true)
        }
        GuidedSessionEvent.Finished -> {
            // The success haptic comes with the check mark of the "Session complete" screen.
            val line = L("Session complete. Well done.")
            voice.say(listOf(VoiceClip.Done), speech = line, display = line, settings = settings, interrupt = true)
        }
    }
}
