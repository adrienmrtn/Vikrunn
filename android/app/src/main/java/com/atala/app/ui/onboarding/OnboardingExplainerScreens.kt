package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.ui.root.collectAppState

/** Full-screen coach explainer (step `Hello`, a tap screen: no header, draws its own reveal). Placeholder. */
@Composable
fun OnboardingHelloScreen() {
    val state by collectAppState()
    OnboardingScaffold(title = L("Nice to meet you, %@.", state.firstName)) {}
}

/** Tap screen (step `GoalIntro`). Placeholder. */
@Composable
fun OnboardingGoalIntroScreen() {
    OnboardingScaffold(title = L("Now it's time to set your goal.")) {}
}

/** Tap screen (step `CoachReply`): the coach answers the "why". Placeholder. */
@Composable
fun OnboardingCoachReplyScreen() {
    val state by collectAppState()
    OnboardingScaffold(title = state.answers.motivation?.coachReply ?: "") {}
}
