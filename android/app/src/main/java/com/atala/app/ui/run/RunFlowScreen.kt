package com.atala.app.ui.run

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.StepKind
import com.atala.app.core.run.ActiveRun
import com.atala.app.core.run.LocationPermission
import com.atala.app.core.run.RunFlowPhase
import com.atala.app.core.run.RunFormat
import com.atala.app.core.run.RunGpsQuality
import com.atala.app.core.run.RunTracker
import com.atala.app.core.run.RunTrackerEvent
import com.atala.app.core.run.RunTrackerState
import com.atala.app.core.voice.VoicePack
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.util.UUID

/**
 * A run with GPS, from "Ready to go?" to the feeling (Atala/Features/Run/RunFlowView.swift).
 * [workoutId] is the plan session being run, null for a free run. The run itself lives in
 * [ActiveRun] (with a foreground service while it records), so it survives the screen turning off
 * and this screen leaving composition. Once saved, the summary replaces this flow.
 */
@Composable
fun RunFlowScreen(workoutId: UUID?, nav: Navigation) {
    val store = LocalAppStore.current
    val context = LocalContext.current
    val state by collectAppState()
    val haptics = rememberHaptics()
    val run = remember(workoutId) { ActiveRun.obtain(context, store, workoutId?.let { store.current.workout(it) }) }
    val tracker = run.tracker
    val phase by run.phase.collectAsStateWithLifecycle()
    val snapshot by tracker.snapshot.collectAsStateWithLifecycle()
    var confirmShortRun by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var askedPermission by rememberSaveable { mutableStateOf(false) }

    fun close() {
        ActiveRun.close(run)
        nav.onBack()
    }

    fun finishRun(save: Boolean) {
        confirmShortRun = false
        confirmFinish = false
        val saved = run.finish(save)
        if (saved == null) {
            haptics.play(HapticKind.Warning)
            close()
        }
        // Saved: the "Session complete" screen plays the success haptic when its check mark lands.
    }

    fun requestFinish() {
        haptics.tap()
        val now = tracker.snapshot.value
        if (now.elapsed < 60 || now.distance < 100) confirmShortRun = true else confirmFinish = true
    }

    // Location (and, on Android 13+, the notification that shows the run in progress).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        askedPermission = true
        val granted = LocationPermission.permissions.any { result[it] == true } || RunTracker.hasPermission(context)
        LocationPermission.answered(context)
        tracker.authorizationChanged(granted)
    }

    LaunchedEffect(run) {
        if (tracker.snapshot.value.state == RunTrackerState.Idle) tracker.prepare()
        VoicePack.prepare(context, store.current.coach.id)
        if (!RunTracker.hasPermission(context) && !askedPermission) {
            val wanted = LocationPermission.permissions.toMutableList()
            if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(wanted.toTypedArray())
        }
    }

    // Back from the system settings: the permission may have changed.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, run) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = RunTracker.hasPermission(context)
                if (granted || askedPermission) tracker.authorizationChanged(granted)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // A sharp marker when the step changes or a kilometre is passed. The first step starts with the
    // run itself, which has its own haptic.
    LaunchedEffect(run) {
        run.events.collect { event ->
            when (event) {
                is RunTrackerEvent.StepStarted -> if (event.index > 0) haptics.play(HapticKind.Rigid)
                is RunTrackerEvent.Kilometre, RunTrackerEvent.SessionComplete -> haptics.play(HapticKind.Rigid)
                RunTrackerEvent.PaceTick -> Unit
            }
        }
    }

    // The screen stays on while running (it can still be turned off: the run goes on).
    val view = LocalView.current
    DisposableEffect(phase) {
        view.keepScreenOn = phase == RunFlowPhase.Active
        onDispose { view.keepScreenOn = false }
    }

    BackHandler {
        when (phase) {
            RunFlowPhase.Ready -> close()
            RunFlowPhase.Countdown -> run.setPhase(RunFlowPhase.Ready)
            // The recording goes on in the background, with its notification.
            RunFlowPhase.Active -> context.findActivity()?.moveTaskToBack(true)
            RunFlowPhase.Feeling -> run.savedRun.value?.let { saved ->
                ActiveRun.close(run)
                nav.onOpenRunSummary(saved.id)
            }
        }
    }

    val firstStep = tracker.steps.firstOrNull()
    Crossfade(phase, animationSpec = tween(250), label = "phase") { shown ->
        when (shown) {
            RunFlowPhase.Ready -> RunReadyScreen(
                workout = run.workout,
                tracker = tracker,
                snapshot = snapshot,
                onStart = { run.setPhase(RunFlowPhase.Countdown) },
                onClose = { close() },
                onOpenPaywall = nav.onOpenPaywall,
            )
            RunFlowPhase.Countdown -> RunCountdownScreen(
                coach = state.coach,
                heading = firstStep?.let { L("%@ in", it.kind.title) } ?: L("Starting in"),
                line = when {
                    firstStep == null -> L("No plan today. Run the way you feel.")
                    firstStep.kind == StepKind.Work -> L("Stay relaxed. Find your rhythm first.")
                    else -> L("Start gently. You should be able to talk.")
                },
                onDone = {
                    haptics.play(HapticKind.Strong)
                    run.begin()
                },
            )
            RunFlowPhase.Active -> if (snapshot.state == RunTrackerState.Paused) {
                RunPauseScreen(
                    tracker = tracker,
                    snapshot = snapshot,
                    coach = state.coach,
                    onResume = { tracker.resume() },
                    onFinish = { requestFinish() },
                )
            } else {
                RunLiveScreen(tracker = tracker, snapshot = snapshot, voice = run.voice, coach = state.coach, onPause = { tracker.pause() })
            }
            RunFlowPhase.Feeling -> RunFeelingScreen(
                distanceM = snapshot.distance,
                durationSec = snapshot.elapsed,
                coach = state.coach,
                onDone = { feeling ->
                    val saved = run.saveFeeling(feeling) ?: run.savedRun.value
                    ActiveRun.close(run)
                    if (saved != null) nav.onOpenRunSummary(saved.id) else nav.onBack()
                },
            )
        }
    }

    if (confirmFinish) {
        RunConfirmDialog(
            title = L("Finish this session?"),
            message = L("%@ in %@. It is saved to your history.", RunFormat.distance(snapshot.distance), Fmt.duration(snapshot.elapsed)),
            actions = listOf(
                RunDialogAction(L("Finish and save")) { finishRun(save = true) },
                RunDialogAction(L("Not yet"), cancel = true) { confirmFinish = false },
            ),
            onDismiss = { confirmFinish = false },
        )
    }

    if (confirmShortRun) {
        // A real effort in time, but no distance: location refused, or no signal (indoors).
        val noDistance = snapshot.elapsed >= 60 && snapshot.distance < 100
        RunConfirmDialog(
            title = if (noDistance) L("No distance was recorded") else L("This session is very short"),
            message = when {
                !noDistance -> L("Less than a minute or 100 metres were recorded.")
                snapshot.gps == RunGpsQuality.Denied ->
                    L("Location is off for Atala, so only your time was measured. You can save it as it is, or discard it and add the run by hand with its distance.")
                else ->
                    L("The GPS signal never came through, so only your time was measured. You can save it as it is, or discard it and add the run by hand with its distance.")
            },
            actions = listOf(
                RunDialogAction(if (noDistance) L("Save the time only") else L("Save it anyway")) { finishRun(save = true) },
                RunDialogAction(L("Discard it"), destructive = true) { finishRun(save = false) },
                RunDialogAction(L("Keep going"), cancel = true) { confirmShortRun = false },
            ),
            onDismiss = { confirmShortRun = false },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** One button of a [RunConfirmDialog]. */
data class RunDialogAction(
    val title: String,
    val destructive: Boolean = false,
    val cancel: Boolean = false,
    val action: () -> Unit,
)

/**
 * The iPhone's confirmation dialog: a title, a message and stacked choices, the cancel one apart
 * at the bottom.
 */
@Composable
fun RunConfirmDialog(title: String, message: String?, actions: List<RunDialogAction>, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(top = 20.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = Theme.font(17, FontWeight.Bold),
                color = Theme.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (message != null) {
                Text(
                    message,
                    style = Theme.font(14),
                    color = Theme.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                )
            }
            Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.Top) {
                actions.forEach { item ->
                    HorizontalDivider(color = Theme.hairline)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Button) {
                                if (!item.destructive) haptics.tap()
                                item.action()
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            item.title,
                            style = Theme.font(17, if (item.cancel) FontWeight.SemiBold else FontWeight.Normal),
                            color = when {
                                item.destructive -> Color(0xFFD7332B)
                                item.cancel -> Theme.ink
                                else -> Theme.green
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
