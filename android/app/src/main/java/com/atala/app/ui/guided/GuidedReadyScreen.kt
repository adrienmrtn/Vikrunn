package com.atala.app.ui.guided

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.exercise.GuidedPrescription
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.ProAccess
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.CoachNote
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.run.CoachVoiceRow
import com.atala.app.ui.theme.Theme
import com.atala.app.ui.today.FitText

/** Before the session: what is coming, the voice switch, a word from the coach, then "Start" (`GuidedReadyScreen`). */
@Composable
internal fun GuidedReadyScreen(
    workout: Workout,
    exercises: List<GuidedPrescription>,
    onStart: () -> Unit,
    onClose: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val store = LocalAppStore.current
    val state by collectAppState()
    val isPro by ProAccess.isPro.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val totalMinutes = GuidedPrescription.totalMinutes(exercises)
    val strength = workout.kind == WorkoutKind.Strength

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier
                    .offset(x = (-10).dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        haptics.tap()
                        onClose()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, L("Close"), tint = Theme.ink, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (exercises.size == 1) L("%@ exercise · about %@", exercises.size, Fmt.minutes(totalMinutes))
                    else L("%@ exercises · about %@", exercises.size, Fmt.minutes(totalMinutes)),
                    style = Theme.font(13, FontWeight.SemiBold),
                    color = Theme.orangeDeep,
                )
                FitText(
                    workout.title,
                    Theme.title(32).copy(letterSpacing = (-0.8).sp),
                    Theme.ink,
                    maxLines = 2,
                    minScale = 0.7f,
                )
                Text(
                    if (strength) L("Bodyweight only. A little floor space is all you need.")
                    else L("Find a quiet spot, with a wall nearby and a mat if you have one."),
                    style = Theme.font(15),
                    color = Theme.muted,
                )
            }
            // The coach's voice needs Atala Pro: a live switch with it, a padlock that opens the paywall without.
            Column {
                HorizontalDivider(color = Theme.hairline)
                CoachVoiceRow(
                    coachName = state.coach.name,
                    isPro = isPro,
                    isOn = state.settings.voice.enabled,
                    onChange = { on -> store.updateSettings { it.copy(voice = it.voice.copy(enabled = on)) } },
                    onText = L("Announces each exercise and counts you down"),
                    offText = L("Off for this session"),
                    onLocked = onOpenPaywall,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                HorizontalDivider(color = Theme.hairline)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(L("Exercises"), style = Theme.font(13, FontWeight.SemiBold), color = Theme.ink)
                GuidedExerciseList(exercises)
            }
            CoachNote(
                state.coach,
                if (strength) L("I'll count the sets and time your rests. Go at your own pace, clean movements matter more than speed.")
                else L("I'll time every stretch for you. Ease into each one, breathe, and stop if anything hurts."),
            )
        }
        BottomBar {
            // Starting the session plays its own, stronger haptic.
            PrimaryButton(L("Start"), onClick = onStart, enabled = exercises.isNotEmpty(), haptic = null)
        }
    }
}
