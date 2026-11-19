package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.ui.root.collectAppState

/** Account creation (step `SignIn`), skipped when an account exists. Placeholder. */
@Composable
fun OnboardingSignInScreen() {
    val state by collectAppState()
    OnboardingScaffold(title = L("It's all here, %@", state.firstName)) {}
}

/** Plan being built (step `Loading`): moves on by itself when the ring is full. Placeholder. */
@Composable
fun OnboardingLoadingScreen() {
    OnboardingScaffold(title = L("I'm building your plan")) {}
}
