package com.atala.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.core.model.CoachPose
import com.atala.app.core.model.Experience
import com.atala.app.core.model.WorkoutKind
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.CoachPoseImage
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.card
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme

/** The three levels the coach speaks to, derived from "How long have you been running?". */
enum class OnboardingRunnerLevel {
    Beginner, Intermediate, Advanced;

    companion object {
        fun of(experience: Experience?): OnboardingRunnerLevel = when (experience ?: Experience.Starting) {
            Experience.Starting, Experience.UnderSixMonths -> Beginner
            Experience.SixMonthsToTwoYears -> Intermediate
            Experience.OverTwoYears -> Advanced
        }
    }
}

/** Wording of the level screens. */
private object LevelCopy {
    val beginnerHeadline get() = L("Starting is the hardest part.")
    val beginnerPraise get() = L("You just did it.")
    val beginnerPromise get() = L("In a few months, you'll be fit, with better cardio than ever.")
    val intermediateHeadline get() = L("Tired of the same loop?")
    val intermediateBody get() = L("Your plan changes every week: easy runs, intervals, long runs, strength.")
    val intermediateClosing get() = L("And I tell you why each one matters.")
    val advancedHeadline get() = L("More training isn't better training.")
    val advancedBody get() = L("I manage your load week by week, so you progress without overtraining.")
    val advancedChartCaption get() = L("Your weekly load builds, then eases off")
}

/** The coach's avatar and name above what they say. */
@Composable
private fun LevelCoachHeader(coach: Coach) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoachAvatar(coach, size = 56.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(coach.name, style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink)
            Text(L("Your coach"), style = Theme.font(14, FontWeight.SemiBold), color = Theme.green)
        }
    }
}

/** The coach in full, in a pose that fits what was just said. Springs in when [visible]. */
@Composable
private fun LevelCoachFigure(coach: Coach, pose: CoachPose, visible: Boolean, height: Dp = 210.dp) {
    CoachPoseImage(
        coach,
        pose = pose,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .onboardingSpringIn(visible)
            .clearAndSetSemantics {},
    )
}

/** Beginners only (step `LevelIntro`): congratulations first. */
@Composable
fun OnboardingLevelIntroScreen() {
    val state by collectAppState()
    // 0 nothing said, 1 headline, 2 praise, 3 everything.
    var spoken by remember { mutableIntStateOf(0) }
    OnboardingScaffold(buttonsVisible = spoken >= 3) {
        LevelCoachHeader(state.coach)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingSpokenText(
                LevelCopy.beginnerHeadline, size = 34, weight = FontWeight.Bold, lineSpacing = 2.dp,
                startDelay = 0.35, isHeading = true, onFinished = { spoken = maxOf(spoken, 1) },
            )
            OnboardingSpokenText(
                LevelCopy.beginnerPraise, size = 34, weight = FontWeight.Bold, color = Theme.green, lineSpacing = 2.dp,
                isActive = spoken >= 1, onFinished = { spoken = maxOf(spoken, 2) },
            )
        }
        OnboardingSpokenText(
            LevelCopy.beginnerPromise, size = 20, color = Theme.body, lineSpacing = 6.dp,
            isActive = spoken >= 2, onFinished = { spoken = maxOf(spoken, 3) },
        )
        // The coach cheers as soon as "You just did it." has been said.
        LevelCoachFigure(state.coach, CoachPose.Cheer, visible = spoken >= 2, height = 230.dp)
    }
}

/** Intermediate and advanced runners (step `LevelFocus`): one screen each. */
@Composable
fun OnboardingLevelFocusScreen() {
    val state by collectAppState()
    // Raised by each sentence when the coach has finished saying it.
    var spoken by remember { mutableIntStateOf(0) }
    val advanced = OnboardingRunnerLevel.of(state.answers.experience) == OnboardingRunnerLevel.Advanced
    OnboardingScaffold(buttonsVisible = spoken >= 3) {
        LevelCoachHeader(state.coach)
        if (advanced) {
            OnboardingSpokenText(
                LevelCopy.advancedHeadline, Modifier.padding(top = 8.dp), size = 34, weight = FontWeight.Bold,
                lineSpacing = 2.dp, startDelay = 0.35, isHeading = true, onFinished = { spoken = maxOf(spoken, 1) },
            )
            OnboardingSpokenText(
                LevelCopy.advancedBody, size = 20, color = Theme.body, lineSpacing = 6.dp,
                isActive = spoken >= 1, onFinished = { spoken = maxOf(spoken, 3) },
            )
            LevelLoadChart(grown = spoken >= 3, modifier = Modifier.onboardingStaged(spoken >= 3))
            LevelCoachFigure(state.coach, CoachPose.Crossed, visible = spoken >= 3, height = 190.dp)
        } else {
            OnboardingSpokenText(
                LevelCopy.intermediateHeadline, Modifier.padding(top = 8.dp), size = 34, weight = FontWeight.Bold,
                lineSpacing = 2.dp, startDelay = 0.35, isHeading = true, onFinished = { spoken = maxOf(spoken, 1) },
            )
            OnboardingSpokenText(
                LevelCopy.intermediateBody, size = 20, color = Theme.body, lineSpacing = 6.dp,
                isActive = spoken >= 1, onFinished = { spoken = maxOf(spoken, 2) },
            )
            LevelSessionTiles(Modifier.onboardingStaged(spoken >= 2))
            OnboardingSpokenText(
                LevelCopy.intermediateClosing, size = 20, weight = FontWeight.SemiBold, lineSpacing = 6.dp,
                isActive = spoken >= 2, startDelay = 0.35, onFinished = { spoken = maxOf(spoken, 3) },
            )
            LevelCoachFigure(state.coach, CoachPose.Present, visible = spoken >= 3, height = 190.dp)
        }
    }
}

/** The four kinds of session of a week, as small tiles. */
@Composable
private fun LevelSessionTiles(modifier: Modifier = Modifier) {
    val kinds = listOf(
        WorkoutKind.Easy to Icons.AutoMirrored.Filled.DirectionsRun,
        WorkoutKind.Intervals to Icons.Filled.Bolt,
        WorkoutKind.Long to Icons.Filled.Route,
        WorkoutKind.Strength to Icons.Filled.FitnessCenter,
    )
    Row(
        modifier
            .fillMaxWidth()
            .card(radius = 22.dp)
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        kinds.forEach { (kind, icon) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTile(
                    icon,
                    tint = if (kind.isQuality) Theme.orangeDeep else Theme.green,
                    background = if (kind.isQuality) Theme.orangeTint else Theme.greenTint,
                    size = 48.dp,
                )
                OnboardingFitText(kind.title, Theme.font(12, FontWeight.SemiBold), Theme.ink, minScale = 0.75f)
            }
        }
    }
}

/** Eight weeks of load: three that build, then a lighter one, as the plan does. */
@Composable
private fun LevelLoadChart(grown: Boolean, modifier: Modifier = Modifier) {
    val loads = listOf(0.50f, 0.60f, 0.70f, 0.56f, 0.76f, 0.86f, 0.96f, 0.77f)
    val chartHeight = 96.dp
    Column(
        modifier
            .fillMaxWidth()
            .card(radius = 24.dp)
            .padding(18.dp)
            .clearAndSetSemantics { contentDescription = LevelCopy.advancedChartCaption },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(LevelCopy.advancedChartCaption, style = Theme.font(13, FontWeight.SemiBold), color = Theme.muted)
        Row(
            Modifier
                .fillMaxWidth()
                .height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            loads.forEachIndexed { index, load ->
                val barHeight by animateDpAsState(
                    if (grown) chartHeight * load else 6.dp,
                    tween(500 + index * 40, easing = OnboardingEasing.easeOut),
                    label = "bar",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(barHeight)
                        .background(if (index % 4 == 3) Theme.greenTint else Theme.green, RoundedCornerShape(7.dp)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(L("Build"), Theme.green)
            Legend(L("Lighter week"), Theme.greenTint)
        }
    }
}

@Composable
private fun Legend(title: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp)),
        )
        Text(title, style = Theme.font(12), color = Theme.muted)
    }
}
