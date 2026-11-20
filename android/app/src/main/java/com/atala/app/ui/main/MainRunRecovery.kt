package com.atala.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.run.ActiveRun
import com.atala.app.core.run.RunBackup
import com.atala.app.core.run.RunBackupStore
import com.atala.app.core.run.RunFormat
import com.atala.app.ui.components.ChoiceDialog
import com.atala.app.ui.components.DialogChoice
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The run flow comes back when the app opens during a run (after it was swiped away, or from the
 * "Run in progress" notification): live or pause screen, or the feeling screen of a run that just
 * ended. At launch, a run left in `active_run.json` by a process that ended mid-run is offered to
 * be saved.
 */
@Composable
internal fun MainRunRecovery(controller: NavHostController, nav: Navigation, isReady: Boolean, isSplashDone: Boolean) {
    val store = LocalAppStore.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val showRequests by ActiveRun.showRequests.collectAsStateWithLifecycle()

    LaunchedEffect(showRequests, isReady) {
        if (!isReady) return@LaunchedEffect
        val run = ActiveRun.resumable ?: return@LaunchedEffect
        if (controller.currentDestination?.hasRoute(RunFlowRoute::class) == true) return@LaunchedEffect
        controller.navigate(RunFlowRoute(run.workoutId?.toString())) { launchSingleTop = true }
    }

    var interrupted by remember { mutableStateOf<RunBackup?>(null) }
    LaunchedEffect(Unit) {
        // Once per process; a run recorded right now owns the file.
        if (RunBackupStore.offered || ActiveRun.current?.isRecording == true) return@LaunchedEffect
        RunBackupStore.offered = true
        val backup = withContext(Dispatchers.IO) { RunBackupStore.read(context) }
        if (ActiveRun.current?.isRecording == true) return@LaunchedEffect
        if (backup == null || !backup.isWorthSaving) {
            // Unreadable, or too short to keep.
            RunBackupStore.delete(context)
            return@LaunchedEffect
        }
        interrupted = backup
    }

    val backup = interrupted
    if (backup != null && isSplashDone) {
        ChoiceDialog(
            title = L("Your last run was interrupted"),
            message = L(
                "Atala was closed during your run. %@ in %@ had been recorded. Save it to your history?",
                RunFormat.distance(backup.distance), Fmt.duration(backup.activeSeconds.roundToInt()),
            ),
            choices = listOf(
                DialogChoice(L("Save run")) {
                    val run = backup.makeRecord()
                    store.addRun(run)
                    store.save()
                    RunBackupStore.delete(context)
                    haptics.play(HapticKind.Success)
                    nav.onOpenRunSummary(run.id)
                },
                DialogChoice(L("Discard it"), destructive = true) {
                    RunBackupStore.delete(context)
                },
            ),
            // Asked again at the next launch.
            onDismiss = { interrupted = null },
            cancelTitle = L("Not now"),
        )
    }
}
