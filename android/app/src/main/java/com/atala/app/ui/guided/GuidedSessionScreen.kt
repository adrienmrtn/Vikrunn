package com.atala.app.ui.guided

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.collectAppState
import java.util.UUID

/** Guided strength or stretching session (Atala/Features/Guided/GuidedSessionView.swift). */
@Composable
fun GuidedSessionScreen(workoutId: UUID, nav: Navigation) {
    val state by collectAppState()
    ScreenPlaceholder(title = state.workout(workoutId)?.title.orEmpty(), onBack = nav.onBack)
}
