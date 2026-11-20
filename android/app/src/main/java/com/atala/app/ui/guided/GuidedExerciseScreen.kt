package com.atala.app.ui.guided

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.ui.components.adaptiveHeight
import com.atala.app.core.exercise.ExerciseInfo
import com.atala.app.core.exercise.GuidedPrescription
import com.atala.app.core.l10n.L
import com.atala.app.core.voice.VoiceClip
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.run.RunRoundButton
import com.atala.app.ui.theme.Theme
import com.atala.app.ui.today.FitText

/** Words shown and spoken during a guided session (`GuidedWording`). */
internal object GuidedWording {
    // Recorded clips that exist (`VoiceClip.exerciseIDs`, `.sets`, `.holds`, `.repCounts` in VoiceClips.swift).
    private val exerciseIds = listOf("calves", "hamstrings", "quads", "hip_flexors", "glutes", "squat", "lunge", "glute_bridge", "plank")
    private val setNumbers = 2..6
    private val holds = listOf(20, 30, 40, 45, 60)
    private val repCounts = listOf(8, 10, 12, 15, 20)

    fun position(exercise: GuidedPrescription, index: Int, count: Int): String =
        L("%@ · %@ of %@", exercise.info.kind.title, index + 1, count)

    /** Short status on the right of the header. */
    fun status(segment: GuidedSegment, exercise: GuidedPrescription): String = when (segment.kind) {
        GuidedSegmentKind.GetReady -> L("Get ready")
        GuidedSegmentKind.SwitchSide -> L("Switch sides")
        GuidedSegmentKind.Rest -> L("Rest")
        GuidedSegmentKind.Work -> when {
            exercise.sets > 1 -> L("Set %@ of %@", segment.setNumber, exercise.sets)
            exercise.isTimed -> L("Hold")
            else -> L("At your pace")
        }
    }

    /** Small line above the exercise name. */
    fun eyebrow(segment: GuidedSegment, exercise: GuidedPrescription): String = when (segment.kind) {
        GuidedSegmentKind.GetReady -> if (segment.exerciseIndex == 0) L("First up") else L("Next")
        GuidedSegmentKind.SwitchSide -> L("Other side")
        GuidedSegmentKind.Rest -> L("Next: set %@ of %@", segment.setNumber + 1, exercise.sets)
        GuidedSegmentKind.Work -> exercise.info.muscles
    }

    /** Lines that rotate under the exercise name. */
    fun lines(segment: GuidedSegment, exercise: GuidedPrescription): List<String> = when (segment.kind) {
        GuidedSegmentKind.GetReady -> listOf(exercise.info.summary)
        GuidedSegmentKind.SwitchSide -> listOf(L("Same thing on the other side. Take your time."))
        GuidedSegmentKind.Rest -> listOf(L("Shake it out and breathe."), L("Stop if anything hurts."))
        GuidedSegmentKind.Work -> {
            val lines = if (exercise.isTimed) exercise.info.cues + exercise.info.steps else exercise.info.cues
            lines.ifEmpty { listOf(exercise.info.summary) }
        }
    }

    /**
     * The recorded clips for the start of a segment. A number or an exercise that was never
     * recorded is left out: the coach then says the short version ("Next.", "Go.").
     */
    fun clips(segment: GuidedSegment, exercise: GuidedPrescription): List<VoiceClip> = when (segment.kind) {
        GuidedSegmentKind.GetReady -> buildList {
            add(if (segment.exerciseIndex == 0) VoiceClip.First else VoiceClip.Next)
            if (exercise.info.id in exerciseIds) {
                add(VoiceClip.Exercise(exercise.info.id))
                segment.side?.let { add(if (it == GuidedSide.Left) VoiceClip.Left else VoiceClip.Right) }
            }
        }
        GuidedSegmentKind.SwitchSide -> {
            val side = segment.side
            if (side == null) listOf(VoiceClip.SwitchSides)
            else listOf(VoiceClip.SwitchSides, if (side == GuidedSide.Left) VoiceClip.Left else VoiceClip.Right)
        }
        GuidedSegmentKind.Rest -> listOf(VoiceClip.Rest)
        GuidedSegmentKind.Work -> buildList {
            val numbered = exercise.sets > 1 && segment.setNumber > 1 && segment.setNumber in setNumbers
            add(if (numbered) VoiceClip.SetNumber(segment.setNumber) else VoiceClip.Go)
            val seconds = segment.durationSec
            val reps = segment.reps
            if (seconds != null) {
                if (seconds in holds) add(VoiceClip.Hold(seconds))
            } else if (reps != null && reps in repCounts) {
                add(VoiceClip.Reps(reps))
            }
        }
    }

    /** What the coach says when a segment starts. */
    fun announcement(segment: GuidedSegment, exercise: GuidedPrescription): String = when (segment.kind) {
        GuidedSegmentKind.GetReady -> {
            val first = segment.exerciseIndex == 0
            val side = segment.side
            if (side != null) {
                if (first) L("First up: %@, %@.", exercise.info.name, side.spoken) else L("Next: %@, %@.", exercise.info.name, side.spoken)
            } else {
                if (first) L("First up: %@.", exercise.info.name) else L("Next: %@.", exercise.info.name)
            }
        }
        GuidedSegmentKind.SwitchSide -> segment.side?.let { L("Switch sides. %@.", it.title) } ?: L("Switch sides.")
        GuidedSegmentKind.Rest -> L("Rest. %@ seconds.", segment.durationSec ?: exercise.restSec)
        GuidedSegmentKind.Work -> {
            val numbered = exercise.sets > 1 && segment.setNumber > 1
            val seconds = segment.durationSec
            val reps = segment.reps
            when {
                seconds != null -> if (numbered) L("Set %@. Hold for %@ seconds.", segment.setNumber, seconds) else L("Go. Hold for %@ seconds.", seconds)
                reps != null -> if (numbered) L("Set %@. %@ reps.", segment.setNumber, reps) else L("Go. %@ reps.", reps)
                else -> if (numbered) L("Set %@.", segment.setNumber) else L("Go.")
            }
        }
    }
}

/** The live screen: the exercise in motion, what to think about, and the timer or rep counter (`GuidedExerciseScreen`). */
@Composable
internal fun GuidedExerciseScreen(engine: GuidedSessionEngine, onPause: () -> Unit, onHowTo: (ExerciseInfo) -> Unit) {
    val segment = engine.current
    val exercise = engine.currentExercise
    Column(Modifier.fillMaxSize().background(Color.White)) {
        if (segment == null || exercise == null) return@Column
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GuidedProgressBar(engine.exercises.size, segment.exerciseIndex)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GuidedChip(
                    GuidedWording.position(exercise, segment.exerciseIndex, engine.exercises.size),
                    foreground = Theme.green, background = Theme.greenTint,
                )
                Spacer(Modifier.weight(1f))
                FitText(
                    GuidedWording.status(segment, exercise),
                    Theme.font(14, FontWeight.SemiBold),
                    if (segment.kind == GuidedSegmentKind.Work) Theme.muted else Theme.orangeDeep,
                    minScale = 0.8f,
                )
            }
            Panel(engine, segment, exercise, onHowTo)
            TitleBlock(engine, segment, exercise)
            TimerBlock(engine, segment)
        }
        Controls(engine, onPause)
    }
}

@Composable
private fun Panel(engine: GuidedSessionEngine, segment: GuidedSegment, exercise: GuidedPrescription, onHowTo: (ExerciseInfo) -> Unit) {
    val haptics = rememberHaptics()
    val color = when (segment.kind) {
        GuidedSegmentKind.Work -> Theme.greenTint
        GuidedSegmentKind.Rest -> Theme.orangeTint
        GuidedSegmentKind.GetReady, GuidedSegmentKind.SwitchSide -> Theme.surface
    }
    val moving = engine.state == GuidedSessionState.Running &&
        (segment.kind == GuidedSegmentKind.Work || segment.kind == GuidedSegmentKind.GetReady)
    Box(
        Modifier
            .fillMaxWidth()
            // Lower on small phones, so the timer stays in view under it.
            .height(adaptiveHeight(250.dp))
            .background(color, RoundedCornerShape(28.dp)),
    ) {
        GuidedExerciseAnimation(
            exercise.info, moving,
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 34.dp, bottom = 12.dp)
                // The pictures show one side; the other side is the same picture mirrored.
                .graphicsLayer { scaleX = if (segment.side == GuidedSide.Right) -1f else 1f },
        )
        segment.side?.let { side ->
            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { GuidedChip(side.title) }
        }
        val label = L("How to do this exercise")
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
                .height(34.dp)
                .clip(CircleShape)
                .background(Color.White, CircleShape)
                .clickable(role = Role.Button) {
                    haptics.tap()
                    onHowTo(exercise.info)
                }
                .clearAndSetSemantics { contentDescription = label }
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, null, tint = Theme.ink, modifier = Modifier.size(16.dp))
            Text(L("How to"), style = Theme.font(14, FontWeight.Bold), color = Theme.ink)
        }
    }
}

@Composable
private fun TitleBlock(engine: GuidedSessionEngine, segment: GuidedSegment, exercise: GuidedPrescription) {
    val lines = GuidedWording.lines(segment, exercise)
    val cue = lines[(engine.segmentElapsed / 6).toInt() % maxOf(1, lines.size)]
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            GuidedWording.eyebrow(segment, exercise),
            style = Theme.font(13, FontWeight.SemiBold),
            color = if (segment.kind == GuidedSegmentKind.Work) Theme.green else Theme.orangeDeep,
            textAlign = TextAlign.Center,
        )
        FitText(
            exercise.info.name,
            Theme.font(30, FontWeight.Bold).copy(letterSpacing = (-0.6).sp),
            Theme.ink,
            minScale = 0.6f,
            textAlign = TextAlign.Center,
        )
        AnimatedContent(
            targetState = cue,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "cue",
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            contentAlignment = Alignment.Center,
        ) { text ->
            FitText(text, Theme.font(16), Theme.body, Modifier.fillMaxWidth(), maxLines = 2, minScale = 0.85f, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TimerBlock(engine: GuidedSessionEngine, segment: GuidedSegment) {
    val remaining = engine.remainingSeconds
    val reps = segment.reps
    if (remaining != null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GuidedTimerRing(
                engine.segmentProgress, "$remaining",
                caption = if (remaining == 1) L("second") else L("seconds"),
                tint = if (segment.kind == GuidedSegmentKind.Work) Theme.green else Theme.orange,
            )
            if (segment.kind == GuidedSegmentKind.Rest) {
                Box(
                    Modifier
                        .widthIn(min = 120.dp)
                        .heightIn(min = 44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { engine.completeSegment() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(L("Skip rest"), style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                }
            }
        }
    } else if (reps != null) {
        val rep = engine.pacedRep ?: 1
        val ofReps = L("of %@ reps", reps)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "$rep $ofReps" },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "$rep",
                    style = Theme.font(72, FontWeight.Bold).copy(letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
                    color = Theme.ink,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(ofReps, style = Theme.font(18, FontWeight.SemiBold), color = Theme.muted, modifier = Modifier.alignByBaseline())
            }
            val setDone = L("Set done")
            Row(
                Modifier
                    .size(180.dp, 52.dp)
                    .clip(CircleShape)
                    .background(Theme.green, CircleShape)
                    .clickable(role = Role.Button) { engine.completeSegment() }
                    .clearAndSetSemantics { contentDescription = setDone },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Text(L("Done"), style = Theme.font(17, FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}

@Composable
private fun Controls(engine: GuidedSessionEngine, onPause: () -> Unit) {
    val haptics = rememberHaptics()
    val pause = L("Pause")
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RunRoundButton(Icons.Filled.SkipPrevious, L("Previous exercise")) { engine.previousExercise() }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Theme.ink, CircleShape)
                .clickable(role = Role.Button) {
                    haptics.confirm()
                    onPause()
                }
                .clearAndSetSemantics { contentDescription = pause },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.weight(1f))
        RunRoundButton(Icons.Filled.SkipNext, L("Next exercise")) { engine.nextExercise() }
    }
}
