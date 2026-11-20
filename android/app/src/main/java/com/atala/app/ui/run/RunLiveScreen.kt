package com.atala.app.ui.run

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.WorkoutStep
import com.atala.app.core.run.RunFormat
import com.atala.app.core.run.RunGpsQuality
import com.atala.app.core.run.RunTracker
import com.atala.app.core.run.RunTrackerSnapshot
import com.atala.app.core.voice.VoiceCoach
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import java.util.Locale

/**
 * The screen the runner glances at while moving (RunLiveScreen.swift): distance, time and pace in
 * very large figures, the step being run in a black card, the coach's last words, and one big pause
 * button. Finishing goes through the pause screen and is confirmed there.
 */
@Composable
fun RunLiveScreen(tracker: RunTracker, snapshot: RunTrackerSnapshot, voice: VoiceCoach, coach: Coach, onPause: () -> Unit) {
    val haptics = rememberHaptics()
    var locked by rememberSaveable { mutableStateOf(false) }
    val lastCue by voice.lastCue.collectAsStateWithLifecycle()
    val step = tracker.currentStep
    val kind = step?.kind
    val fast = RunFormat.isFast(kind)
    val background by animateColorAsState(RunColors.tint(kind), tween(350), label = "tint")
    val secondary by animateColorAsState(RunColors.tintedText(kind), tween(350), label = "secondary")
    val showsStepCard = tracker.hasSteps && !snapshot.stepsComplete && step != null

    Box(Modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Theme.screenPadding)
                    .padding(top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Top line
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pill = when {
                        !tracker.hasSteps -> L("Free run")
                        snapshot.stepsComplete -> L("Session complete")
                        else -> RunFormat.pill(snapshot.stepIndex, tracker.steps)
                    }
                    FitText(
                        pill,
                        Theme.font(16, FontWeight.Bold),
                        if (fast) Color.White else Theme.green,
                        Modifier
                            .background(if (fast) Theme.orange else Color.White, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        minScale = 0.7f,
                    )
                    Spacer(Modifier.weight(1f).width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(gpsColor(snapshot.gps, snapshot.isSimulating), CircleShape))
                        FitText(
                            if (snapshot.isSimulating) L("Simulated GPS") else liveGpsText(snapshot.gps),
                            Theme.font(14, FontWeight.SemiBold), secondary, minScale = 0.8f,
                        )
                    }
                }
                if (tracker.hasSteps) RunSegmentBar(tracker.steps, snapshot.stepIndex, snapshot.stepsComplete)
                if (showsStepCard && step != null) StepCard(tracker, snapshot, step)
                Figures(tracker, snapshot, compact = showsStepCard, secondary = secondary)
                val paceFast = step?.paceFast
                val paceSlow = step?.paceSlow
                if (step != null && paceFast != null && paceSlow != null && !snapshot.stepsComplete) {
                    PaceCard(step, snapshot.currentPace, paceFast, paceSlow)
                }
                val bubble = lastCue.ifEmpty {
                    if (tracker.hasSteps) L("Here we go. I'll guide you step by step.") else L("Off you go. Run the way you feel.")
                }
                RunCoachBubble(coach, bubble)
            }
            // Controls
            Row(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RunRoundButton(Icons.Filled.Lock, L("Lock the screen")) {
                    haptics.tap()
                    locked = true
                }
                Spacer(Modifier.weight(1f))
                val pauseLabel = L("Pause")
                Box(
                    Modifier
                        .size(92.dp)
                        .background(Theme.ink, CircleShape)
                        .clickable(role = Role.Button) {
                            haptics.play(HapticKind.Confirm)
                            onPause()
                        }
                        .semantics { contentDescription = pauseLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.weight(1f))
                RunRoundButton(
                    Icons.Filled.SkipNext,
                    L("Skip to the next step"),
                    Modifier.alpha(if (tracker.canSkipStep) 1f else 0f),
                    enabled = tracker.canSkipStep,
                ) { tracker.skipStep() }
            }
        }
        AnimatedVisibility(locked, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            RunLockOverlay(onUnlock = {
                haptics.play(HapticKind.Confirm)
                locked = false
            })
        }
    }
}

private fun liveGpsText(gps: RunGpsQuality): String = when (gps) {
    RunGpsQuality.Good -> L("GPS good")
    RunGpsQuality.Fair -> L("GPS fair")
    RunGpsQuality.Poor -> L("GPS weak")
    RunGpsQuality.Denied -> L("Location off")
    RunGpsQuality.Unknown -> L("Looking for GPS")
}

@Composable
private fun StepCard(tracker: RunTracker, snapshot: RunTrackerSnapshot, step: WorkoutStep) {
    val seconds = tracker.stepRemainingSeconds
    val metres = tracker.stepRemainingMeters
    data class Remaining(val caption: String, val value: String, val unit: String?, val total: String)
    val remaining = when {
        seconds != null -> Remaining(L("Time remaining"), Fmt.duration(seconds), null, L("of %@", RunFormat.spokenAmount(step)))
        metres != null -> {
            val total = L("of %@", RunFormat.amount(step))
            if (metres >= 1000) Remaining(L("Distance remaining"), String.format(Locale.ROOT, "%.2f", metres / 1000), "km", total)
            else Remaining(L("Distance remaining"), "${Math.round(metres)}", "m", total)
        }
        else -> Remaining(L("Distance"), RunFormat.kmValue(snapshot.distance), "km", "")
    }
    val progress = when {
        (step.durationSec ?: 0) > 0 -> snapshot.stepElapsed.toDouble() / step.durationSec!!
        (step.distanceM ?: 0) > 0 -> snapshot.stepDistance / step.distanceM!!
        else -> 0.0
    }
    RunStepCard(
        caption = remaining.caption,
        value = remaining.value,
        unit = remaining.unit,
        total = remaining.total,
        progress = progress,
        next = RunFormat.next(snapshot.stepIndex, tracker.steps),
        position = L("Step %@ of %@", snapshot.stepIndex + 1, tracker.steps.size),
        fast = step.kind == StepKind.Work,
    )
}

@Composable
private fun Figures(tracker: RunTracker, snapshot: RunTrackerSnapshot, compact: Boolean, secondary: Color) {
    val footnote = when {
        !tracker.hasSteps -> L("Run the way you feel")
        snapshot.stepsComplete -> L("Everything planned is done. Finish when you like.")
        else -> null
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
    ) {
        RunLiveFigure(L("Distance"), RunFormat.kmValue(snapshot.distance), unit = "km", size = if (compact) 76 else 108,
            centered = true, labelColor = secondary)
        if (footnote != null) {
            Text(footnote, style = Theme.font(15, FontWeight.SemiBold), color = secondary, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RunLiveFigure(L("Time"), Fmt.duration(snapshot.elapsed), Modifier.weight(1f), size = if (compact) 44 else 52,
                centered = true, labelColor = secondary)
            RunLiveFigure(L("Pace"), Fmt.pace(snapshot.currentPace), Modifier.weight(1f), unit = "/km", size = if (compact) 44 else 52,
                centered = true, labelColor = secondary)
        }
        Text(L("Average pace %@/km", Fmt.pace(snapshot.averagePace)), style = Theme.font(15, FontWeight.SemiBold).digits(),
            color = secondary, maxLines = 1)
    }
}

/** Pace against the target. */
@Composable
private fun PaceCard(step: WorkoutStep, pace: Int, fast: Int, slow: Int) {
    val work = step.kind == StepKind.Work
    val (text, color) = when {
        pace <= 0 -> L("Finding your pace…") to Theme.muted
        pace < fast - 5 -> L("Too fast") to Theme.orangeDeep
        pace > slow + 5 -> if (work) L("Too slow") to Theme.orangeDeep else L("Nice and easy") to Theme.green
        else -> (if (work) L("On target") else L("Easy, as planned")) to Theme.green
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FitText(text, Theme.font(18, FontWeight.Bold), color, Modifier.weight(1f), minScale = 0.7f)
            Spacer(Modifier.width(8.dp))
            FitText(L("Target %@ to %@", Fmt.pace(slow), Fmt.pace(fast)), Theme.font(14, FontWeight.SemiBold).digits(), Theme.body, minScale = 0.7f)
        }
        if (work) RunPaceGauge(pace, fast, slow, showsLabels = false)
    }
}
