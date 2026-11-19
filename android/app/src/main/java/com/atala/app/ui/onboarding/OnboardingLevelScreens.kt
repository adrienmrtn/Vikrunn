package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Experience
import com.atala.app.ui.root.collectAppState

/** Beginners only (step `LevelIntro`, see `OnboardingNavigator.isStarting`). Placeholder. */
@Composable
fun OnboardingLevelIntroScreen() {
    OnboardingScaffold(title = L("Starting is the hardest part.")) {}
}

/** Intermediate and advanced runners (step `LevelFocus`). Placeholder. */
@Composable
fun OnboardingLevelFocusScreen() {
    val state by collectAppState()
    OnboardingScaffold(title = if (state.answers.experience == Experience.OverTwoYears) L("More training isn't better training.") else L("Tired of the same loop?")) {}
}
