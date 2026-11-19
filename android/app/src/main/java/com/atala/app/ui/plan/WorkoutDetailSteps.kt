package com.atala.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atala.app.core.exercise.GuidedPrescription
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutBlock
import com.atala.app.core.model.WorkoutStep
import com.atala.app.ui.guided.GuidedExerciseList
import com.atala.app.ui.theme.Theme
import kotlin.math.roundToInt

/** Step-by-step breakdown of a session, built from its blocks (`WorkoutDetailSteps`). */
@Composable
internal fun WorkoutDetailSteps(workout: Workout, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            if (workout.kind.isRun) L("Step by step") else L("Exercises"),
            style = Theme.font(13, FontWeight.SemiBold),
            color = Theme.ink,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (workout.kind.isRun) {
            if (workout.blocks.isEmpty()) {
                Text(
                    L("Run as you feel, with no set structure."),
                    style = Theme.font(14), color = Theme.muted,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            workout.blocks.forEach { block ->
                if (block.repeats > 1) {
                    RepeatBlock(block)
                } else {
                    block.steps.forEach { StepRow(it) }
                }
            }
        } else {
            // Strength and stretching: picture, name and amount. A tap shows how to do it.
            GuidedExerciseList(GuidedPrescription.session(workout))
        }
    }
}

/** A single step: dot, name, pace hint and its duration or distance. */
@Composable
private fun StepRow(step: WorkoutStep) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(WorkoutDetailFormat.dotColor(step), CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(WorkoutDetailFormat.title(step), style = Theme.font(15), color = Theme.ink)
                WorkoutDetailFormat.detail(step)?.let { Text(it, style = Theme.font(12), color = Theme.muted) }
            }
            Text(WorkoutDetailFormat.amount(step), style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
        }
        HorizontalDivider(color = Theme.hairline)
    }
}

/** A group of steps repeated several times, shown as "6 ×". */
@Composable
private fun RepeatBlock(block: WorkoutBlock) {
    val times = L("%@ times", block.repeats)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(Theme.orange, RoundedCornerShape(14.dp))
                    .semantics { contentDescription = times },
                contentAlignment = Alignment.Center,
            ) {
                Text("${block.repeats} ×", style = Theme.font(15, FontWeight.Bold), color = Theme.ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                block.steps.forEach { step ->
                    Row(
                        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                WorkoutDetailFormat.title(step),
                                style = Theme.font(15, if (step.kind == StepKind.Work) FontWeight.SemiBold else FontWeight.Normal),
                                color = Theme.ink,
                            )
                            WorkoutDetailFormat.detail(step)?.let { Text(it, style = Theme.font(12), color = Theme.muted) }
                        }
                        Text(WorkoutDetailFormat.repeatAmount(step), style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                    }
                }
            }
        }
        HorizontalDivider(color = Theme.hairline)
    }
}

/** Text helpers for the steps of a session (`WorkoutDetailFormat`). */
internal object WorkoutDetailFormat {
    fun dotColor(step: WorkoutStep): Color = when (step.kind) {
        StepKind.Work -> Theme.orange
        StepKind.Exercise -> Theme.yellow
        StepKind.Warmup, StepKind.Recovery, StepKind.Cooldown, StepKind.Steady -> Theme.greenSoft
    }

    fun title(step: WorkoutStep): String = when (step.kind) {
        StepKind.Warmup, StepKind.Cooldown, StepKind.Recovery -> step.kind.title
        StepKind.Work, StepKind.Steady, StepKind.Exercise -> step.label.ifEmpty { step.kind.title }
    }

    private fun paceRange(step: WorkoutStep): String? {
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

    fun meters(meters: Int): String = if (meters >= 1000) Fmt.km(meters / 1000.0) else "$meters m"

    fun seconds(seconds: Int): String = if (seconds % 60 == 0) L("%@ min", seconds / 60) else Fmt.duration(seconds)

    /** Distance or duration of the step. */
    fun amount(step: WorkoutStep): String {
        step.distanceM?.let { return meters(it) }
        step.durationSec?.let { return seconds(it) }
        return ""
    }

    /** Inside a repeat block, a distance effort named after its distance shows its expected time. */
    fun repeatAmount(step: WorkoutStep): String {
        val distance = step.distanceM
        val fast = step.paceFast
        val slow = step.paceSlow
        if (step.kind == StepKind.Work && distance != null && fast != null && slow != null &&
            (title(step) == meters(distance) || title(step) == "$distance m")
        ) {
            val pace = (fast + slow) / 2.0
            return Fmt.duration((distance / 1000.0 * pace).roundToInt())
        }
        return amount(step)
    }
}
