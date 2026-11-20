package com.atala.app.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.core.run.RunFormat
import com.atala.app.core.run.RunTracker
import com.atala.app.core.run.RunTrackerSnapshot
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme

/**
 * Paused (RunPauseScreen.swift): the clock stands still until the runner resumes or finishes.
 * Resuming is the big button; finishing is the quieter one and is confirmed by the run flow.
 */
@Composable
fun RunPauseScreen(tracker: RunTracker, snapshot: RunTrackerSnapshot, coach: Coach, onResume: () -> Unit, onFinish: () -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Theme.screenPadding)
                .padding(top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .background(Theme.ink, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Text(L("Paused"), style = Theme.font(16, FontWeight.Bold), color = Color.White)
            }
            if (tracker.hasSteps) RunSegmentBar(tracker.steps, snapshot.stepIndex, snapshot.stepsComplete)
            Column(
                Modifier
                    .fillMaxWidth()
                    .card(radius = 24.dp)
                    .padding(horizontal = 14.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RunLiveFigure(L("Distance"), RunFormat.kmValue(snapshot.distance), unit = "km", size = 88, centered = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RunLiveFigure(L("Time"), Fmt.duration(snapshot.elapsed), Modifier.weight(1f), size = 46, centered = true)
                    RunLiveFigure(L("Average pace"), Fmt.pace(snapshot.averagePace), Modifier.weight(1f), unit = "/km", size = 46, centered = true)
                }
                Text(positionLine(tracker, snapshot), style = Theme.font(15, FontWeight.SemiBold), color = Theme.muted, textAlign = TextAlign.Center)
            }
            RunCoachBubble(coach, L("Take your time. We start again whenever you like."), showsSpeaker = false)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = Theme.screenPadding)
                .padding(top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RunBigButton(L("Resume"), Icons.Filled.PlayArrow, onClick = {
                haptics.play(HapticKind.Confirm)
                onResume()
            }, haptic = null)
            // No haptic here: success comes with the "Session complete" screen, warning when discarded.
            RunSecondaryButton(L("Finish session"), Icons.Filled.SportsScore, onFinish, haptic = null)
            if (tracker.canSkipToCooldown) {
                RunTextButton(L("Skip straight to the cool-down"), color = Theme.muted, modifier = Modifier.fillMaxWidth()) {
                    tracker.skipToCooldown()
                    onResume()
                }
            }
        }
    }
}

/** Where the runner stopped: "Rep 3 of 6", "Warm-up · step 1 of 4", "Free run". */
private fun positionLine(tracker: RunTracker, snapshot: RunTrackerSnapshot): String {
    if (!tracker.hasSteps) return L("Free run")
    if (snapshot.stepsComplete) return L("Session complete")
    RunFormat.repetition(snapshot.stepIndex, tracker.steps)?.let { (number, total) -> return L("Rep %@ of %@", number, total) }
    val title = tracker.currentStep?.kind?.title ?: L("Run")
    return L("%@ · step %@ of %@", title, snapshot.stepIndex + 1, tracker.steps.size)
}
