package com.atala.app.ui.profile

import androidx.compose.runtime.Composable
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation

/** Profile and settings (Atala/Features/Profile/ProfileView.swift), a sheet on the iPhone. */
@Composable
fun ProfileScreen(nav: Navigation) {
    ScreenPlaceholder(title = L("Profile"), onBack = nav.onBack, actions = listOf("Atala Pro" to nav.onOpenPaywall))
}
