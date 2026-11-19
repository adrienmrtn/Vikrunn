package com.atala.app.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.collectAppState

/** Today tab (Atala/Features/Today/TodayView.swift). The avatar in its header opens the profile. */
@Composable
fun TodayScreen(nav: Navigation) {
    val state by collectAppState()
    val next = state.nextRun
    ScreenPlaceholder(
        title = L("Today"),
        actions = listOfNotNull(
            next?.let { workout -> "Open ${workout.title}" to { nav.onOpenWorkout(workout.id) } },
            "Profile" to nav.onOpenProfile,
            "Free run" to { nav.onStartRun(null) },
        ),
    )
}
