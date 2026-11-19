package com.atala.app.ui.coach

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation

/** Coach tab, the chat with the coach (Atala/Features/Coach/CoachChatView.swift). Pro only. */
@Composable
fun CoachScreen(nav: Navigation) {
    ScreenPlaceholder(title = L("Coach"), actions = listOf("Atala Pro" to nav.onOpenPaywall))
}
