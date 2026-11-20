package com.atala.app.ui.root

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.store.AppStore
import com.atala.app.ui.auth.AuthSyncPrompts
import com.atala.app.ui.main.MainScreen
import com.atala.app.ui.onboarding.OnboardingFlow
import com.atala.app.ui.theme.AtalaTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Root of the interface (`RootView`): onboarding until it is finished, then the app itself, with
 * the launch splash above either one on every cold launch.
 */
@Composable
fun AtalaRoot(store: AppStore) {
    // Survives rotation, so the splash plays once per launch.
    var splashDone by rememberSaveable { mutableStateOf(false) }
    val language by AppLanguage.code.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { store.refreshMissed() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { store.save() }

    AtalaTheme {
        CompositionLocalProvider(LocalAppStore provides store) {
            // White is what shows through while two languages cross-fade.
            Box(Modifier.fillMaxSize().background(Color.White)) {
                // A new language rebuilds every screen (Crossfade composes each code apart), so each
                // sentence is read again: the old tree fades out while the new one fades in.
                // The splash is outside: it never replays.
                Crossfade(targetState = language, animationSpec = tween(300), label = "language") { code ->
                    val direction = if (code == "ar" || code == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        AppContent(store)
                        // Restoring overlay and "which plan do you want to keep?" (account team).
                        AuthSyncPrompts()
                    }
                }
                if (!splashDone) SplashScreen(onFinished = { splashDone = true })
            }
        }
    }
}

@Composable
private fun AppContent(store: AppStore) {
    val completed by remember(store) {
        store.state.map { it.hasCompletedOnboarding }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(store.current.hasCompletedOnboarding)
    Crossfade(targetState = completed, animationSpec = tween(350), label = "onboarding") { done ->
        if (done) MainScreen() else OnboardingFlow()
    }
}
