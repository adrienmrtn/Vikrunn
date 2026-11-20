package com.atala.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.CoachPose
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.Motivation
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme

/** Screen 4 (step `Hello`): the chosen coach says hello, word by word, over the photo background. */
@Composable
fun OnboardingHelloScreen() {
    val state by collectAppState()
    var greeted by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val speech = L(
        "I'm %@, and I'll be with you all the way to your goal. Tell me about your running and we'll build your plan together.",
        state.coach.name,
    )
    OnboardingTapScreen(
        hintColor = Color.White,
        hintShadow = true,
        canContinue = finished,
        pose = CoachPose.Wave,
        lightStatusBar = true,
        backdrop = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2F5A2A)),
            ) {
                AtalaImages["bg_hello"]?.let { id ->
                    Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        },
    ) {
        val bubble = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, bubble, ambientColor = Color(0xFF14280A).copy(alpha = 0.18f), spotColor = Color(0xFF14280A).copy(alpha = 0.18f))
                .background(Color.White.copy(alpha = 0.95f), bubble)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnboardingSpokenText(
                L("Nice to meet you, %@.", state.firstName),
                size = 28, weight = FontWeight.Bold, lineSpacing = 2.dp,
                startDelay = OnboardingRevealTiming.SPEECH, isHeading = true,
                onFinished = { greeted = true },
            )
            OnboardingSpokenText(
                speech, size = 16, color = Color(0xFF333333), lineSpacing = 5.dp,
                isActive = greeted, onFinished = { finished = true },
            )
        }
    }
}

/**
 * A large soft circle rising from the bottom of an explainer's backdrop (`OnboardingBackdropCircle`).
 * Sized from the screen: 1.6 times its width, its top 46 % of the height above the bottom, so it
 * stays behind the coach on small and large phones alike.
 */
@Composable
private fun BackdropCircle(background: Color, circle: Color) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(background),
    ) {
        val diameter = maxWidth * 1.6f
        val visible = maxHeight * 0.46f
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = diameter - visible)
                .requiredSize(diameter)
                .background(circle, CircleShape),
        )
    }
}

/** Screen 11 (step `GoalIntro`): full-screen green moment before the goal questions. */
@Composable
fun OnboardingGoalIntroScreen() {
    val state by collectAppState()
    var introduced by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    // Nothing is "known" about someone who does not run yet: the coach only says how it will go.
    val opening = if (OnboardingNavigator.isNotRunningYet(state.answers)) {
        L("We'll take it step by step, %@.", state.firstName)
    } else {
        L("I know where you're starting from, %@.", state.firstName)
    }
    OnboardingTapScreen(
        lightContent = true,
        canContinue = finished,
        pose = CoachPose.Present,
        backdrop = { BackdropCircle(Theme.green, Color(0xFF2A6E5B)) },
    ) {
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OnboardingSpokenText(
                opening, size = 14, weight = FontWeight.SemiBold, color = Theme.greenOnDark, lineSpacing = 3.dp,
                startDelay = OnboardingRevealTiming.SPEECH, onFinished = { introduced = true },
            )
            OnboardingSpokenText(
                L("Now it's time to set your goal."), size = 38, weight = FontWeight.Bold, color = Color.White,
                lineSpacing = 2.dp, isActive = introduced, isHeading = true, onFinished = { finished = true },
            )
        }
    }
}

/** Screen 16 (step `CoachReply`): the coach answers the "why". */
@Composable
fun OnboardingCoachReplyScreen() {
    val state by collectAppState()
    var replied by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val answers = state.answers
    val motivation = answers.motivation ?: Motivation.Prove
    val chip = when (motivation) {
        Motivation.Athletic -> L("You want to get in better shape")
        Motivation.Prove -> L("You want to prove to yourself you can do it")
        Motivation.Record -> L("You want to beat your record")
        Motivation.Health -> L("You want to take care of your health")
        Motivation.ClearHead -> L("You want to clear your head")
        Motivation.Together -> L("You want to run with people you love")
    }
    val detail = run {
        val goal = answers.goal ?: GoalType.Regular
        val habit = L("A running habit is built session after session. I'll be there for each one.")
        if (goal.isOpenEnded) return@run habit
        // One full sentence per goal: the article and the agreements change with the noun.
        val target = answers.targetSeconds
        if (target != null) {
            val time = Fmt.goalTime(target)
            when (goal) {
                GoalType.FiveK -> L("A 5K in %@ is built session after session. I'll be there for each one.", time)
                GoalType.TenK -> L("A 10K in %@ is built session after session. I'll be there for each one.", time)
                GoalType.Half -> L("A half marathon in %@ is built session after session. I'll be there for each one.", time)
                GoalType.Regular, GoalType.Shape -> habit
            }
        } else {
            when (goal) {
                GoalType.FiveK -> L("A 5K is built session after session. I'll be there for each one.")
                GoalType.TenK -> L("A 10K is built session after session. I'll be there for each one.")
                GoalType.Half -> L("A half marathon is built session after session. I'll be there for each one.")
                GoalType.Regular, GoalType.Shape -> habit
            }
        }
    }
    OnboardingTapScreen(
        hintColor = Theme.orangeDeep,
        canContinue = finished,
        pose = CoachPose.ThumbsUp,
        backdrop = { BackdropCircle(Theme.orangeTint, Color(0xFFF6D3BD)) },
    ) {
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OnboardingChip(chip)
            OnboardingSpokenText(
                motivation.coachReply, size = 34, weight = FontWeight.Bold, lineSpacing = 2.dp,
                startDelay = OnboardingRevealTiming.SPEECH, isHeading = true, onFinished = { replied = true },
            )
            OnboardingSpokenText(
                detail, size = 17, color = Color(0xFF4A3A30), lineSpacing = 5.dp,
                isActive = replied, onFinished = { finished = true },
            )
        }
    }
}
