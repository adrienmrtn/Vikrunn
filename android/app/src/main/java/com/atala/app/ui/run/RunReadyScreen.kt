package com.atala.app.ui.run

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.ProAccess
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutStep
import com.atala.app.core.run.RunFormat
import com.atala.app.core.run.RunGpsQuality
import com.atala.app.core.run.RunTracker
import com.atala.app.core.run.RunTrackerSnapshot
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.CoachNote
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * "Ready to go?" (RunReadyScreen.swift): what the session is, the GPS signal on one line, the voice
 * settings in one card, a word from the coach, then one big start button.
 */
@Composable
fun RunReadyScreen(
    workout: Workout?,
    tracker: RunTracker,
    snapshot: RunTrackerSnapshot,
    approximateOnly: Boolean,
    onStart: () -> Unit,
    onClose: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val store = LocalAppStore.current
    val state by collectAppState()
    val isPro by ProAccess.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val voice = state.settings.voice
    val hasPlannedSteps = workout?.blocks?.isNotEmpty() == true
    val voiceIsOn = isPro && voice.enabled
    val coach = state.coach

    fun updateVoice(change: (com.atala.app.core.model.VoiceSettings) -> com.atala.app.core.model.VoiceSettings) =
        store.updateSettings { it.copy(voice = change(it.voice)) }

    Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Theme.screenPadding)
                .padding(top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val closeLabel = L("Close")
            Box(
                Modifier
                    .offset(x = (-10).dp)
                    .size(44.dp)
                    .clickable(role = Role.Button) {
                        haptics.tap()
                        onClose()
                    }
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = Theme.ink, modifier = Modifier.size(22.dp))
            }
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(L("Ready to go?"), style = Theme.font(13, FontWeight.SemiBold), color = Theme.orangeDeep)
                FitText(workout?.title ?: L("Free run"), Theme.title(32).tracking(-0.8f), Theme.ink, minScale = 0.7f, maxLines = 2)
                if (workout == null) {
                    Text(L("No plan, no target. Your distance, time and pace are recorded."), style = Theme.font(15), color = Theme.muted)
                }
            }
            // Approximate location only: no distance can be measured, whatever the signal says.
            val approximate = approximateOnly && !snapshot.isSimulating
            RunReadyGpsLine(
                quality = if (approximate) RunGpsQuality.Denied else snapshot.gps,
                simulated = snapshot.isSimulating,
                text = if (approximate) {
                    L("Approximate location only: your distance can't be measured. Allow precise location in Settings, or add the run by hand afterwards.")
                } else {
                    gpsText(snapshot)
                },
                onSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            )
            if (workout != null) RunReadySessionCard(workout)
            // Settings
            Column(
                Modifier
                    .fillMaxWidth()
                    .card()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                CoachVoiceRow(
                    coachName = coach.name,
                    isPro = isPro,
                    isOn = voice.enabled,
                    onChange = { on -> updateVoice { it.copy(enabled = on) } },
                    onText = if (hasPlannedSteps) L("Announces each step") else L("Announces each kilometre"),
                    offText = L("Off for this run"),
                    onLocked = onOpenPaywall,
                )
                AnimatedVisibility(voiceIsOn, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        if (hasPlannedSteps) {
                            RunReadyToggleRow(L("Step changes"), voice.announceSteps) { on -> updateVoice { it.copy(announceSteps = on) } }
                            RunReadyToggleRow(L("Pace too fast or too slow"), voice.announcePace) { on -> updateVoice { it.copy(announcePace = on) } }
                        }
                        RunReadyToggleRow(L("Every kilometre"), voice.announceEveryKm) { on -> updateVoice { it.copy(announceEveryKm = on) } }
                        Spacer(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(Theme.hairline))
                        Row(
                            Modifier.padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.MusicNote, null, tint = Theme.yellowDeep, modifier = Modifier.size(16.dp))
                            Text(L("Your music gets quieter when %@ speaks", coach.name), style = Theme.font(13), color = Theme.muted)
                        }
                    }
                }
            }
            CoachNote(coach, coachLine(tracker.steps))
        }
        BottomBar {
            RunBigButton(L("Start"), Icons.Filled.PlayArrow, onStart)
        }
    }
}

private fun gpsText(snapshot: RunTrackerSnapshot): String {
    if (snapshot.isSimulating) return L("Simulated signal (simulator)")
    val metres = snapshot.accuracy?.roundToInt()
    return when (snapshot.gps) {
        RunGpsQuality.Unknown -> L("Looking for satellites…")
        RunGpsQuality.Denied -> L("Location is off: only your time will be measured. Turn it on in Settings, or add the run by hand afterwards.")
        RunGpsQuality.Poor -> if (metres != null) L("Weak, accuracy %@ m. Move to an open spot.", metres) else L("Weak. Move to an open spot.")
        RunGpsQuality.Fair -> if (metres != null) L("Fair, accuracy %@ m", metres) else L("Fair")
        RunGpsQuality.Good -> if (metres != null) L("Good, accuracy %@ m", metres) else L("Good")
    }
}

private fun coachLine(steps: List<WorkoutStep>): String {
    val first = steps.firstOrNull() ?: return L("No plan today. Run the way you feel, I'll keep track of everything.")
    val hasFastPart = steps.any { it.kind == StepKind.Work }
    val seconds = first.durationSec
    if (first.kind == StepKind.Warmup && seconds != null) {
        val minutes = maxOf(1, seconds / 60)
        return if (hasFastPart) L("We start with %@ very easy minutes. I'll tell you when to speed up.", minutes)
        else L("We start with %@ very easy minutes.", minutes)
    }
    if (first.kind == StepKind.Work) return L("Today is your day. Start a touch slower than you want to, I'll guide you.")
    return L("Easy and steady today. You should be able to talk the whole way.")
}

/** The GPS signal on one line: a coloured dot, "GPS" and its state. */
@Composable
private fun RunReadyGpsLine(quality: RunGpsQuality, simulated: Boolean, text: String, onSettings: () -> Unit) {
    val color = gpsColor(quality, simulated)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Theme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).background(color.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("GPS", style = Theme.font(15, FontWeight.Bold), color = Theme.ink)
            Text(text, style = Theme.font(14), color = Theme.body)
        }
        if (quality == RunGpsQuality.Denied && !simulated) {
            RunTextButton(L("Settings"), onClick = onSettings)
        }
    }
}

/** Colour of the GPS dot (ready and live screens). */
fun gpsColor(quality: RunGpsQuality, simulated: Boolean): Color = when {
    simulated -> Theme.green
    quality == RunGpsQuality.Good || quality == RunGpsQuality.Fair -> Theme.green
    quality == RunGpsQuality.Poor -> Theme.orange
    quality == RunGpsQuality.Denied -> Theme.orangeDeep
    else -> Theme.faint
}

/** A secondary switch of the settings card, lined up under the title of the voice row. */
@Composable
private fun RunReadyToggleRow(title: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Theme.font(15), color = Theme.ink, modifier = Modifier.weight(1f))
        RunSwitch(isOn, onChange)
    }
}

// Session card

/** One line of the session at a glance. */
private data class RunReadyPlanRow(val color: Color, val title: String, val detail: String?, val amount: String)

/** Text helpers for the steps of a session (WorkoutDetailFormat in WorkoutDetailSteps.swift). */
internal object RunStepText {
    fun title(step: WorkoutStep): String = when (step.kind) {
        StepKind.Warmup, StepKind.Cooldown, StepKind.Recovery -> step.kind.title
        else -> step.label.ifEmpty { step.kind.title }
    }

    fun paceRange(step: WorkoutStep): String? {
        val fast = step.paceFast
        val slow = step.paceSlow
        if (fast != null && slow != null) {
            return if (fast == slow) "${Fmt.pace(fast)}/km" else "${Fmt.pace(fast)} – ${Fmt.pace(slow)}/km"
        }
        return (fast ?: slow)?.let { "${Fmt.pace(it)}/km" }
    }

    fun detail(step: WorkoutStep): String? {
        val range = paceRange(step)
        return when (step.kind) {
            StepKind.Warmup, StepKind.Cooldown, StepKind.Recovery -> {
                val parts = listOfNotNull(step.label.ifEmpty { null }, range)
                if (parts.isEmpty()) null else parts.joinToString(", ")
            }
            StepKind.Work, StepKind.Steady -> range ?: L("Free pace")
            StepKind.Exercise -> null
        }
    }

    private fun meters(meters: Int): String = if (meters >= 1000) Fmt.km(meters / 1000.0) else "$meters m"

    private fun seconds(seconds: Int): String = if (seconds % 60 == 0) L("%@ min", seconds / 60) else Fmt.duration(seconds)

    fun amount(step: WorkoutStep): String {
        step.distanceM?.let { return meters(it) }
        step.durationSec?.let { return seconds(it) }
        return ""
    }
}

/** What the session is: its key figures, its shape as a bar, and its steps in a few lines. */
@Composable
private fun RunReadySessionCard(workout: Workout) {
    val steps = workout.flatSteps.filter { it.kind != StepKind.Exercise }
    val rows = buildList {
        for (block in workout.blocks) {
            val blockSteps = block.steps.filter { it.kind != StepKind.Exercise }
            val main = blockSteps.firstOrNull { it.kind == StepKind.Work } ?: blockSteps.firstOrNull()
            if (block.repeats > 1 && main != null) {
                val parts = listOfNotNull(
                    RunStepText.paceRange(main),
                    blockSteps.firstOrNull { it.kind == StepKind.Recovery }?.let { L("Recovery %@", RunFormat.amount(it)) },
                )
                add(RunReadyPlanRow(RunColors.dot(main.kind), "${block.repeats} × ${RunFormat.amount(main)}",
                    parts.takeIf { it.isNotEmpty() }?.joinToString(" · "), main.kind.title))
            } else {
                blockSteps.forEach { step ->
                    add(RunReadyPlanRow(RunColors.dot(step.kind), RunStepText.title(step), RunStepText.detail(step), RunStepText.amount(step)))
                }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().card(radius = 22.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (workout.distanceKm > 0) Fact(Fmt.km(workout.distanceKm), L("Distance"), Modifier.weight(1f))
            Fact(Fmt.minutes(workout.durationMin), L("Duration"), Modifier.weight(1f))
            workout.targetPace?.let { Fact("${Fmt.pace(it)}/km", L("Target pace"), Modifier.weight(1f)) }
        }
        if (steps.isNotEmpty()) RunSegmentBar(steps, currentIndex = 0, complete = false, preview = true)
        if (rows.isEmpty()) {
            Text(L("Run as you feel, with no set structure."), style = Theme.font(14), color = Theme.muted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 6.dp).size(9.dp).background(row.color, CircleShape))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(row.title, style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                            row.detail?.let { Text(it, style = Theme.font(12), color = Theme.muted) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(row.amount, style = Theme.font(15, FontWeight.SemiBold).digits(), color = Theme.ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FitText(value, Theme.font(22, FontWeight.Bold).digits(), Theme.ink, minScale = 0.6f)
        FitText(label, Theme.font(12), Theme.muted, minScale = 0.8f)
    }
}
