package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.core.model.GoalType
import com.atala.app.ui.root.collectAppState

/** Step `Goal`, written to `answers.goal`. Placeholder. */
@Composable
fun OnboardingGoalScreen() {
    OnboardingScaffold(title = L("What's your goal?"), canContinue = true) {}
}

/** Race date (step `Race`), skipped for open-ended goals. Placeholder. */
@Composable
fun OnboardingRaceScreen() {
    OnboardingScaffold(title = L("A race in sight?")) {}
}

/** Step `Why`, written to `answers.motivation`. Placeholder. */
@Composable
fun OnboardingWhyScreen() {
    val state by collectAppState()
    val title = when (state.answers.goal ?: GoalType.Regular) {
        GoalType.FiveK -> L("Why this 5K?")
        GoalType.TenK -> L("Why this 10K?")
        GoalType.Half -> L("Why this half marathon?")
        GoalType.Regular -> L("Why do you want to run regularly?")
        GoalType.Shape -> L("Why do you want to get in shape?")
    }
    OnboardingScaffold(title = title) {}
}
