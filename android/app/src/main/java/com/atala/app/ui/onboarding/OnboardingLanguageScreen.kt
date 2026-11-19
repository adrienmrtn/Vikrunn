package com.atala.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.L

/**
 * Language picker (step `Language`, right after the welcome). Placeholder.
 *
 * On Android, `AppLanguage.choose` rebuilds the whole tree at once (AtalaRoot cross-fades on the
 * language code) and the rebuilt flow restores itself on the saved step. So a new language is
 * applied after `nav.saveNext()`: one cross-fade, from this screen straight to the next one.
 */
@Composable
fun OnboardingLanguageScreen() {
    val nav = LocalOnboardingNavigator.current
    OnboardingScaffold(
        title = L("First, let's make sure you have the right language"),
        onContinue = { submit(nav, AppLanguage.current) },
    ) {}
}

private fun submit(nav: OnboardingNavigator, code: String) {
    if (code != AppLanguage.current && nav.saveNext()) AppLanguage.choose(code) else nav.next()
}
