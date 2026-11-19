package com.atala.app.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.collectAppState

/** Progress tab (Atala/Features/Progress/ProgressTabView.swift). Pro only. */
@Composable
fun ProgressScreen(nav: Navigation) {
    val state by collectAppState()
    ScreenPlaceholder(
        title = L("Progress"),
        actions = state.runs.take(3).map { run -> run.title to { nav.onOpenRunSummary(run.id) } } +
            ("Atala Pro" to nav.onOpenPaywall),
    )
}
