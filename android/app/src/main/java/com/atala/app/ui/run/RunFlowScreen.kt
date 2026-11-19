package com.atala.app.ui.run

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.atala.app.core.l10n.L
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSource
import com.atala.app.ui.components.ScreenPlaceholder
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import java.time.Instant
import java.util.UUID

/**
 * A run with GPS, from the ready screen to the feeling (Atala/Features/Run/RunFlowView.swift).
 * [workoutId] is the plan session being run, null for a free run. When the run is saved
 * (`store.addRun`), call `nav.onOpenRunSummary(run.id)`: the summary replaces this flow.
 */
@Composable
fun RunFlowScreen(workoutId: UUID?, nav: Navigation) {
    val store = LocalAppStore.current
    val state by collectAppState()
    val title = workoutId?.let { state.workout(it)?.title } ?: L("Free run")
    ScreenPlaceholder(
        title = title,
        onBack = nav.onBack,
        actions = listOf(
            "Save a 5 km sample run" to {
                val run = RunRecord(workoutId = workoutId, title = title, date = Instant.now(), source = RunSource.Gps,
                    distanceM = 5000.0, durationSec = 30 * 60)
                store.addRun(run)
                nav.onOpenRunSummary(run.id)
            },
        ),
    )
}
