package com.atala.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Experience
import com.atala.app.core.model.Gender
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.WeeklyVolume
import com.atala.app.core.plan.PlanEngine
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.OptionCard
import com.atala.app.ui.components.VignetteImage
import com.atala.app.ui.components.card
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.util.Locale

/** Screen 5 (step `Gender`). */
@Composable
fun OnboardingGenderScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    OnboardingScaffold(title = L("You are…"), canContinue = state.answers.gender != null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Gender.entries.forEach { gender ->
                OnboardingPlainOptionRow(gender.title, selected = state.answers.gender == gender) {
                    store.updateAnswers { it.copy(gender = gender) }
                }
            }
        }
    }
}

/** Screen 6 (step `Experience`): how long have you been running. */
@Composable
fun OnboardingExperienceScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    OnboardingScaffold(title = L("How long have you been running?"), canContinue = state.answers.experience != null) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Experience.entries.forEach { experience ->
                OptionCard(
                    experience.title,
                    selected = state.answers.experience == experience,
                    onClick = { store.updateAnswers { it.copy(experience = experience) } },
                ) { VignetteImage(experience.vignette) }
            }
        }
    }
}

/** Screen 7 (step `Volume`): current weekly volume. */
@Composable
fun OnboardingVolumeScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    OnboardingScaffold(title = L("How much do you run in a week?"), canContinue = state.answers.weeklyVolume != null) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            WeeklyVolume.entries.forEach { volume ->
                OptionCard(
                    volume.title,
                    selected = state.answers.weeklyVolume == volume,
                    onClick = {
                        store.updateAnswers {
                            // The recent-result and target-time questions are skipped for someone
                            // who does not run yet: nothing is kept from an earlier pass.
                            if (volume == WeeklyVolume.NotYet) {
                                it.copy(weeklyVolume = volume, recentResult = null, targetSeconds = null)
                            } else {
                                it.copy(weeklyVolume = volume)
                            }
                        }
                    },
                ) { VignetteImage(volume.vignette) }
            }
        }
    }
}

/** One health flag stored in `OnboardingAnswers.healthFlags`. */
private data class HealthOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val background: Color,
) {
    companion object {
        const val NOTHING = "none"

        val all: List<HealthOption>
            get() = listOf(
                HealthOption(NOTHING, L("Nothing to report"), L("I feel fine to start"), Icons.Filled.Check, Theme.green, Theme.greenTint),
                HealthOption("pain", L("A pain or an injury right now"), L("See a professional first, we start gently"),
                    Icons.Filled.Healing, Theme.orangeDeep, Theme.orangeTint),
                HealthOption("heart", L("A heart or breathing condition"), L("Check with your doctor before starting"),
                    Icons.Filled.Favorite, Theme.orangeDeep, Theme.orangeTint),
                HealthOption("break", L("Back after a long break"), L("We rebuild step by step"), Icons.Filled.Replay, Theme.green, Theme.greenTint),
            )
    }
}

/** Screen 9 (step `Health`, out of the flow for now). The coach never diagnoses, it only starts more gently. */
@Composable
fun OnboardingHealthScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    fun toggle(id: String) = store.updateAnswers { answers ->
        var flags = answers.healthFlags
        if (id == HealthOption.NOTHING) {
            flags = if (id in flags) emptyList() else listOf(id)
        } else {
            flags = flags - HealthOption.NOTHING
            flags = if (id in flags) flags - id else flags + id
        }
        answers.copy(healthFlags = flags)
    }
    OnboardingScaffold(title = L("Anything about your health I should know?"), canContinue = state.answers.healthFlags.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            HealthOption.all.forEach { option ->
                OptionCard(
                    option.title,
                    selected = option.id in state.answers.healthFlags,
                    onClick = { toggle(option.id) },
                    subtitle = option.subtitle,
                    multi = true,
                ) { IconTile(option.icon, tint = option.tint, background = option.background) }
            }
        }
    }
}

/**
 * Screen 10 (step `Mirror`): "Here's what I see", built from the answers so far. Only shown to
 * someone who gave a recent result: every figure here comes from that time.
 */
@Composable
fun OnboardingMirrorScreen() {
    val nav = LocalOnboardingNavigator.current
    val state by collectAppState()
    val answers = state.answers
    // Header, hero card, the two tiles, the volume line, then the buttons.
    val delays = listOf(0.3, 0.35, 0.35, 0.25, 0.4)
    val stage = rememberOnboardingStages(delays)
    val vma = PlanEngine.estimateVma(answers)
    val reference = if ((answers.experience ?: Experience.Starting) == Experience.Starting) GoalType.FiveK else GoalType.TenK
    val source = answers.recentResult?.let {
        L("Based on your %@ in %@", OnboardingCopy.distanceName(it.distanceKm), Fmt.goalTime(it.seconds))
    } ?: L("Based on your answers")
    val level = when (answers.experience ?: Experience.Starting) {
        Experience.Starting -> L("Beginner")
        Experience.UnderSixMonths -> L("Getting started")
        Experience.SixMonthsToTwoYears -> L("Intermediate")
        Experience.OverTwoYears -> L("Experienced")
    }
    val volume = answers.weeklyVolume ?: WeeklyVolume.Under10
    val usualVolume = if (volume == WeeklyVolume.NotYet) L("Not running yet") else L("%@ a week", volume.title)
    val easyPace = "${Fmt.pace(PlanEngine.paceFor(vma, 0.65))} /km"
    val predicted = PlanEngine.predictedSeconds(vma, reference) ?: 0

    OnboardingScaffold(
        buttonTitle = L("That's me"),
        buttonsVisible = stage >= delays.size,
        secondaryTitle = L("Change something"),
        onSecondary = { nav.go(OnboardingStep.Experience) },
    ) {
        Row(
            Modifier.onboardingStaged(0, stage),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoachAvatar(state.coach, size = 52.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(L("Here's what I see"), style = Theme.title(26), color = Theme.ink, modifier = Modifier.semantics { heading() })
                Text(source, style = Theme.font(14, FontWeight.SemiBold), color = Theme.green)
            }
        }
        // The headline figure, white on green, with the level as a chip.
        val heroShape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .onboardingStaged(1, stage)
                .fillMaxWidth()
                .shadow(16.dp, heroShape, ambientColor = Theme.green.copy(alpha = 0.25f), spotColor = Theme.green.copy(alpha = 0.25f))
                .background(Brush.linearGradient(listOf(Theme.green, Theme.forest)), heroShape)
                .padding(22.dp)
                .clearAndSetSemantics {
                    contentDescription = L("Your %@ today: %@. Level: %@.", reference.title, Fmt.goalTime(predicted), level)
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    L("YOUR %@ TODAY", reference.title.uppercase(AppLanguage.locale)),
                    style = Theme.font(12, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
                    color = Theme.greenOnDark,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    level,
                    style = Theme.font(12, FontWeight.Bold),
                    color = Theme.forest,
                    modifier = Modifier
                        .background(Color.White, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            OnboardingCountingValue(
                value = if (stage > 1) predicted.toDouble() else 0.0,
                format = { Fmt.goalTime(it) },
                style = Theme.font(60, FontWeight.Bold),
                color = Color.White,
            )
            Text(L("Your starting point. Your goal comes next."), style = Theme.font(15), color = Theme.greenOnDark)
        }
        Row(Modifier.onboardingStaged(2, stage), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MirrorTile(
                Icons.Filled.Bolt, Theme.orangeDeep, Theme.orangeTint,
                String.format(Locale.ROOT, "%.1f km/h", vma), L("Aerobic speed (VMA)"), Modifier.weight(1f),
            )
            MirrorTile(
                Icons.AutoMirrored.Filled.DirectionsRun, Theme.green, Theme.greenTint,
                easyPace, L("Easy pace"), Modifier.weight(1f),
            )
        }
        Row(
            Modifier
                .onboardingStaged(3, stage)
                .fillMaxWidth()
                .card(radius = 20.dp)
                .padding(14.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Filled.CalendarMonth, tint = Theme.yellowDeep, background = Theme.yellowTint, size = 40.dp)
            Text(L("Usual volume"), style = Theme.font(15), color = Theme.muted, modifier = Modifier.weight(1f))
            OnboardingFitText(usualVolume, Theme.font(16, FontWeight.SemiBold), Theme.ink, minScale = 0.8f)
        }
    }
}

@Composable
private fun MirrorTile(icon: ImageVector, tint: Color, background: Color, value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .card(radius = 22.dp)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, tint = tint, background = background, size = 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OnboardingFitText(value, Theme.font(22, FontWeight.Bold).copy(fontFeatureSettings = "tnum"), Theme.ink, minScale = 0.7f)
            OnboardingFitText(label, Theme.font(13), Theme.muted, minScale = 0.8f)
        }
    }
}
