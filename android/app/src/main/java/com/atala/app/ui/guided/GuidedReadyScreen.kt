package com.atala.app.ui.guided

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
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
                GuidedCoachVoiceRow(
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

/**
 * The coach's voice switch (`CoachVoiceRow`). With Atala Pro it is a live toggle. Without it, the
 * row shows a padlock and an "Atala Pro" tag (never a switch) and a tap calls [onLocked].
 */
@Composable
internal fun GuidedCoachVoiceRow(
    coachName: String,
    isPro: Boolean,
    isOn: Boolean,
    onChange: (Boolean) -> Unit,
    onText: String,
    offText: String,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    if (isPro) {
        Row(
            modifier.fillMaxWidth().heightIn(min = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.AutoMirrored.Filled.VolumeUp, tint = Theme.orangeDeep, background = Theme.orangeTint, size = 44.dp)
            VoiceTexts(coachName, if (isOn) onText else offText, Modifier.weight(1f))
            Switch(
                checked = isOn,
                onCheckedChange = {
                    haptics.select()
                    onChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Theme.green,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = Theme.hairline,
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = Theme.hairline,
                ),
            )
        }
    } else {
        val label = L("%@'s voice, included in Atala Pro", coachName)
        val hint = L("Shows what Atala Pro includes")
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable {
                    haptics.tap()
                    onLocked()
                }
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                    onClick(label = hint) { onLocked(); true }
                },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.AutoMirrored.Filled.VolumeUp, tint = Theme.muted, background = Theme.locked, size = 44.dp)
            VoiceTexts(coachName, L("Included in Atala Pro"), Modifier.weight(1f))
            GuidedProTag()
        }
    }
}

@Composable
private fun VoiceTexts(coachName: String, subtitle: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(L("%@'s voice", coachName), style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
        Text(subtitle, style = Theme.font(13), color = Theme.muted)
    }
}

/** The small "Atala Pro" tag with a padlock, where a control needs the subscription (`RunProTag`). */
@Composable
internal fun GuidedProTag() {
    Row(
        Modifier
            .height(28.dp)
            .background(Theme.greenTint, CircleShape)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, null, tint = Theme.green, modifier = Modifier.size(12.dp))
        Text("Atala Pro", style = Theme.font(13, FontWeight.Bold), color = Theme.green, maxLines = 1)
    }
}
