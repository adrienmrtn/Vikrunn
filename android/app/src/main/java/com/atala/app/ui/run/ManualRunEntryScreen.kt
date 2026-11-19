package com.atala.app.ui.run

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation
import java.util.UUID

/**
 * A run done without the app, typed in (Atala/Features/Run/ManualRunEntryView.swift). [workoutId]
 * is the plan session it stands for, null from the "+" of the tab bar.
 */
@Composable
fun ManualRunEntryScreen(workoutId: UUID?, nav: Navigation) {
    ScreenPlaceholder(title = L("Add a past run"), onBack = nav.onBack)
}
