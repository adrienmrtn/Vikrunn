package com.atala.app.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.collectAppState
import java.util.UUID

/**
 * A session of the plan (Atala/Features/Today/WorkoutDetailView.swift): a run starts the run flow,
 * strength and stretching start the guided session.
 */
@Composable
fun WorkoutDetailScreen(workoutId: UUID, nav: Navigation) {
    val state by collectAppState()
    val workout = state.workout(workoutId)
    ScreenPlaceholder(
        title = workout?.title.orEmpty(),
        onBack = nav.onBack,
        actions = when {
            workout == null -> emptyList()
            workout.kind.isRun -> listOf("Start" to { nav.onStartRun(workoutId) }, "Add a past run" to { nav.onAddPastRun(workoutId) })
            else -> listOf("Start" to { nav.onStartGuided(workoutId) })
        },
    )
}
