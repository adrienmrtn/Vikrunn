package com.atala.app.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.collectAppState

/** Plan tab (Atala/Features/Plan/PlanTabView.swift). The avatar in its header opens the profile. */
@Composable
fun PlanScreen(nav: Navigation) {
    val state by collectAppState()
    ScreenPlaceholder(
        title = L("Plan"),
        actions = state.currentWeek?.workouts.orEmpty().map { workout ->
            workout.title to { nav.onOpenWorkout(workout.id) }
        } + ("Profile" to nav.onOpenProfile),
    )
}
