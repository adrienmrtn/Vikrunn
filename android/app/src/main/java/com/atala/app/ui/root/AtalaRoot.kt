package com.atala.app.ui.root

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.core.store.AppStore
import com.atala.app.core.voice.VoicePack
import com.atala.app.ui.auth.AuthSyncPrompts
import com.atala.app.ui.main.MainScreen
import com.atala.app.ui.onboarding.OnboardingFlow
import com.atala.app.ui.theme.AtalaTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Root of the interface (`RootView`): onboarding until it is finished, then the app itself, with
 * the launch splash above either one on every cold launch.
 */
@Composable
fun AtalaRoot(store: AppStore) {
    // Survives rotation, so the splash plays once per launch.
    var splashDone by rememberSaveable { mutableStateOf(false) }
    val language by AppLanguage.code.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coachId by remember(store) {
        store.state.map { it.coach.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(store.current.coach.id)
    // Counts the returns to the foreground.
    var foregrounds by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        store.refreshMissed()
        foregrounds += 1
        // A subscription can start, renew or end while the app is away.
        scope.launch { PurchaseService.refresh() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { store.save() }
    LaunchedEffect(coachId, language, foregrounds) {
        // The coach's recorded voice in the app's language: downloaded once, then kept.
        VoicePack.prepare(context, coachId)
    }

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
                        AppContent(store, splashDone)
                        // Restoring overlay and "which plan do you want to keep?".
                        AuthSyncPrompts()
                    }
                }
                if (!splashDone) SplashScreen(onFinished = { splashDone = true })
            }
        }
    }
}

@Composable
private fun AppContent(store: AppStore, splashDone: Boolean) {
    val completed by remember(store) {
        store.state.map { it.hasCompletedOnboarding }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(store.current.hasCompletedOnboarding)
    Crossfade(targetState = completed, animationSpec = tween(350), label = "onboarding") { done ->
        if (done) MainScreen(isSplashDone = splashDone) else OnboardingFlow()
    }
}
