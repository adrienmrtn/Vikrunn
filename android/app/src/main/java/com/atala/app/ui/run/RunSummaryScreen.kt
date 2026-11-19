package com.atala.app.ui.run

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation
import java.util.UUID

/** A saved run (Atala/Features/Run/RunSummaryView.swift). "Talk to my coach" is `nav.onOpenCoach`. */
@Composable
fun RunSummaryScreen(runId: UUID, nav: Navigation) {
    ScreenPlaceholder(title = L("Run summary"), onBack = nav.onBack, actions = listOf("Talk to my coach" to nav.onOpenCoach))
}
