package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.ui.root.collectAppState

/** Sample chat with the coach (step `Message`). Placeholder. */
@Composable
fun OnboardingMessageScreen() {
    val state by collectAppState()
    OnboardingScaffold(title = L("Here's how you talk to %@", state.coach.name)) {}
}
