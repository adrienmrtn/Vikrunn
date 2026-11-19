package com.atala.app.ui.guided

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.SecondaryButton
import com.atala.app.ui.components.SelectionMark
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import com.atala.app.ui.today.FitText
import com.atala.app.ui.today.lineHeight
import kotlin.math.roundToInt

/** Paused: the clock stands still until the user resumes or ends the session (`GuidedPauseScreen`). */
@Composable
internal fun GuidedPauseScreen(engine: GuidedSessionEngine, coach: Coach, onResume: () -> Unit, onEnd: () -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            GuidedProgressBar(engine.exercises.size, engine.current?.exerciseIndex ?: 0)
            Column(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    L("Paused"),
                    style = Theme.font(15, FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.background(Theme.ink, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
                )
                FitText(
                    Fmt.duration(engine.totalElapsed),
                    Theme.font(84, FontWeight.Bold).copy(letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
                    Theme.ink,
                    Modifier.padding(top = 10.dp),
                    minScale = 0.5f,
                )
                Text(positionLine(engine), style = Theme.font(15), color = Theme.muted, textAlign = TextAlign.Center)
            }
            PauseStats(engine)
            PauseCoachBubble(coach, L("Take your time. We pick it up whenever you like."))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(CircleShape)
                    .background(Theme.ink, CircleShape)
                    .clickable(role = Role.Button) {
                        haptics.confirm()
                        onResume()
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(L("Resume"), style = Theme.font(17, FontWeight.SemiBold), color = Color.White)
            }
            SecondaryButton(L("End session"), onClick = onEnd)
        }
    }
}

private fun positionLine(engine: GuidedSessionEngine): String {
    val segment = engine.current ?: return L("Session")
    val exercise = engine.currentExercise ?: return L("Session")
    val name = exercise.info.name
    val side = segment.side
    if (side != null && segment.kind != GuidedSegmentKind.Rest) {
        if (exercise.sets > 1) return L("%@, %@ · set %@ of %@", name, side.spoken, segment.setNumber, exercise.sets)
        return L("%@, %@", name, side.spoken)
    }
    if (exercise.sets > 1) return L("%@ · set %@ of %@", name, segment.setNumber, exercise.sets)
    return name
}

/** Three small figures side by side (`RunStatsRow`). */
@Composable
private fun PauseStats(engine: GuidedSessionEngine) {
    val number = (engine.current?.exerciseIndex ?: 0) + 1
    val items = listOf(
        L("%@ of %@", number, engine.exercises.size) to L("Exercise"),
        "${engine.completedExercises.size}" to L("Completed"),
        Fmt.minutes(maxOf(1, (engine.plannedSeconds / 60.0).roundToInt())) to L("Planned"),
    )
    Row(
        Modifier.fillMaxWidth().card(radius = 24.dp).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items.forEach { (value, label) ->
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FitText(value, Theme.font(24, FontWeight.Bold).copy(fontFeatureSettings = "tnum"), Theme.ink, minScale = 0.6f)
                Text(label, style = Theme.font(12), color = Theme.muted)
            }
        }
    }
}

/** The coach's words: avatar and a white speech bubble (`RunCoachBubble` without the speaker). */
@Composable
private fun PauseCoachBubble(coach: Coach, text: String) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        CoachAvatar(coach, size = 36.dp)
        Text(
            text,
            style = Theme.font(15).copy(lineHeight = lineHeight(15, 2)),
            color = Theme.ink,
            modifier = Modifier
                .weight(1f, fill = false)
                .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.08f))
                .background(Color.White, shape)
                .border(1.dp, Theme.cardBorder, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** After the last exercise: the figures, a word from the coach, an optional feeling, then "Done" (`GuidedFinishScreen`). */
@Composable
internal fun GuidedFinishScreen(
    workout: Workout,
    elapsedSec: Int,
    doneCount: Int,
    totalCount: Int,
    coach: Coach,
    onDone: (Feeling?) -> Unit,
) {
    var feeling by rememberSaveable { mutableStateOf<Feeling?>(null) }
    val minutes = maxOf(1, (elapsedSec / 60.0).roundToInt())
    val dataLine = if (doneCount == 1) L("%@ exercise · %@ min", doneCount, minutes) else L("%@ exercises · %@ min", doneCount, minutes)
    val kindTitle = workout.kind.title
    val stats = listOf(
        SessionCompleteStat(elapsedSec.toDouble(), L("Total time")) { Fmt.duration(it.roundToInt()) },
        SessionCompleteStat(doneCount.toDouble(), L("Exercises done")) { L("%@ of %@", it.roundToInt(), totalCount) },
        SessionCompleteStat(0.0, L("Session")) { kindTitle },
    )
    val coachLine = when {
        feeling == Feeling.Pain -> L("Thank you for telling me. Take it easy today, and tell me more in the chat whenever you like.")
        doneCount < totalCount -> L("Every bit counts. You showed up, and that is what builds the habit.")
        workout.kind == WorkoutKind.Strength -> L("Well done. Stronger legs and core will carry you through the end of your runs.")
        else -> L("Well done. A few calm minutes like these help you feel fresh for your next run.")
    }
    GuidedSessionCompleteScreen(
        eyebrow = workout.title,
        dataLine = dataLine,
        stats = stats,
        coach = coach,
        coachLine = coachLine,
        buttonTitle = L("Done"),
        onPrimary = { onDone(feeling) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(L("How did it feel? (optional)"), style = Theme.font(13, FontWeight.SemiBold), color = Theme.ink)
            Feeling.entries.forEach { option ->
                val subtitle = when (option) {
                    Feeling.Easy -> L("I could have done more")
                    Feeling.Good -> L("Just right")
                    Feeling.Hard -> L("That was a lot for me")
                    Feeling.Pain -> L("I'd rather tell %@", coach.name)
                }
                FeelingRow(option, subtitle, feeling == option) {
                    feeling = if (feeling == option) null else option
                }
            }
        }
    }
}

@Composable
private fun FeelingRow(feeling: Feeling, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .card(selected)
            .clip(RoundedCornerShape(Theme.cardRadius))
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton) {
                haptics.select()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(GuidedFeelingStyle.background(feeling), CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Icon(GuidedFeelingStyle.icon(feeling), null, tint = GuidedFeelingStyle.tint(feeling), modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(feeling.title, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
            Text(subtitle, style = Theme.font(13), color = Theme.muted)
        }
        SelectionMark(selected)
    }
}
