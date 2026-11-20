package com.atala.app.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.store.AppState
import com.atala.app.core.store.AppStore
import com.atala.app.core.store.SampleData
import com.atala.app.ui.theme.AtalaTheme

/**
 * The one [AppStore] of the app, available to every composable (`@EnvironmentObject store` on the
 * iPhone). Read with `LocalAppStore.current`; observe with [collectAppState].
 */
val LocalAppStore = staticCompositionLocalOf<AppStore> {
    error("No AppStore: wrap the content in AtalaRoot, or in AtalaPreview for a preview")
}

/** The app state, recomposing the caller on every change: `val state by collectAppState()`. */
@Composable
fun collectAppState(): State<AppState> = LocalAppStore.current.state.collectAsStateWithLifecycle()

/** Theme, a white background and an in-memory store with a sample runner, for `@Preview`s. */
@Composable
fun AtalaPreview(content: @Composable () -> Unit) {
    val store = remember { SampleData.store() }
    AtalaTheme {
        CompositionLocalProvider(LocalAppStore provides store) {
            Box(Modifier.background(Color.White)) { content() }
        }
    }
}
