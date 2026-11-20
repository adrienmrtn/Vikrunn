package com.atala.app.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.CoachPose
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.ProAccess
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSource
import com.atala.app.core.model.RunSplit
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.run.RunAnalysis
import com.atala.app.core.run.RunFormat
import com.atala.app.core.store.AppState
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.CoachPoseImage
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.SecondaryButton
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.paywall.ProLocked
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Summary of a run (RunSummaryView.swift). It opens with the route on a map (or a plain header when
 * there is none), then the key figures, the splits per kilometre, the pace and elevation charts and a
 * word from the coach. The map, the distance, the time and the average pace are free; the detailed
 * figures need Atala Pro.
 */
@Composable
fun RunSummaryScreen(runId: UUID, nav: Navigation) {
    val state by collectAppState()
    // Kept while closing after a deletion, so the screen does not go blank as it slides away.
    var lastShown by remember { mutableStateOf<RunRecord?>(null) }
    val run = state.run(runId)?.also { lastShown = it } ?: lastShown
    if (run == null) {
        Box(Modifier.fillMaxSize().background(Color.White))
        return
    }
    RunSummaryContent(run, state, nav)
}

@Composable
private fun RunSummaryContent(run: RunRecord, state: AppState, nav: Navigation) {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    val isPro by ProAccess.isPro.collectAsStateWithLifecycle()
    var showShare by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val analysis = remember(run) { RunAnalysis(run) }
    val hasRoute = run.route.size >= 2
    val fastSplits = run.splits.filter { it.kind == StepKind.Work }
    val day = Fmt.day(run.date)
    val dateLine = when (run.source) {
        RunSource.Gps -> day
        RunSource.Manual -> L("%@ · Entered by hand", day)
        // Imported from Apple Health on the iPhone; Health Connect on Android.
        RunSource.Health -> L("%@ · From Apple Health", day)
    }
    val title = if (run.title.isEmpty()) L("Run") else L(run.title)
    val titleLine = if (run.workoutId != null && state.workout(run.workoutId) != null) L("%@, done", title) else title
    // Right after the run: the coach in full, cheering.
    val justFinished = run.source == RunSource.Gps &&
        Duration.between(run.date.plusSeconds(run.durationSec.toLong()), Instant.now()).toMinutes() < 15

    Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Theme.screenPadding)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (hasRoute) {
                RunRouteMap(run.route, RunFormat.distance(run.distanceM))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(dateLine, style = Theme.font(13), color = Theme.muted)
                    Text(titleLine, style = Theme.title(28), color = Theme.ink)
                }
            } else {
                val icon = when (run.source) {
                    RunSource.Gps -> Icons.AutoMirrored.Filled.DirectionsRun
                    RunSource.Manual -> Icons.Filled.EditNote
                    RunSource.Health -> Icons.Filled.Favorite
                }
                RunSummaryPlainHeader(icon, dateLine, titleLine)
            }
            RunSummaryFiguresCard(run, analysis, isPro)
            DetailCards(run, analysis, fastSplits, isPro, nav.onOpenPaywall)
            CoachLine(state, run, justFinished)
            run.feeling?.let { FeelingRow(it) }
            // Actions: count the run as the session of its day, talk to the coach, delete it.
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sessionToCount(state, run)?.let { session -> CountRunCard(run, session) }
                RunSecondaryButton(L("Talk to my coach"), Icons.AutoMirrored.Outlined.Chat, onClick = nav.onOpenCoach)
                RunTextButton(L("Delete this run"), color = Color(0xFFD7332B), size = 15, modifier = Modifier.fillMaxWidth()) {
                    haptics.play(HapticKind.Warning)
                    confirmDelete = true
                }
            }
        }
        BottomBar {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val shareLabel = L("Share")
                Box(
                    Modifier
                        .size(Theme.buttonHeight)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Theme.ink, CircleShape)
                        .clickable(role = Role.Button) {
                            haptics.tap()
                            showShare = true
                        }
                        .semantics { contentDescription = shareLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Share, null, tint = Theme.ink, modifier = Modifier.size(20.dp))
                }
                PrimaryButton(L("Done"), onClick = nav.onBack, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showShare) RunShareSheet(run, onClose = { showShare = false })

    if (confirmDelete) {
        var message = L("It is removed from your history and your totals.")
        if (run.workoutId != null && state.workout(run.workoutId) != null) message += " " + L("Its session goes back to not done.")
        if (run.source == RunSource.Health) message += " " + L("It stays in Apple Health and will not be imported again.")
        RunConfirmDialog(
            title = L("Delete this run?"),
            message = message,
            actions = listOf(
                RunDialogAction(L("Delete"), destructive = true) {
                    confirmDelete = false
                    store.deleteRun(run.id)
                    nav.onBack()
                },
                RunDialogAction(L("Cancel"), cancel = true) { confirmDelete = false },
            ),
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Fast repetitions, splits per kilometre, pace and elevation: shown with Atala Pro, offered otherwise. */
@Composable
private fun DetailCards(run: RunRecord, analysis: RunAnalysis, fastSplits: List<RunSplit>, isPro: Boolean, onOpenPaywall: () -> Unit) {
    val hasSplits = analysis.kmSplits.size >= 2
    val hasPace = analysis.paceProfile.size >= 4
    val gain = analysis.elevationGainM
    val hasElevation = gain != null && analysis.elevation.size >= 2
    val hasHeartRate = run.avgHeartRate != null || run.maxHeartRate != null
    if (isPro) {
        if (fastSplits.size >= 2) RunRepsCard(fastSplits)
        if (hasSplits) RunSplitsCard(analysis.kmSplits, analysis.fastestSplit?.id, run.avgPace)
        if (hasPace) RunPaceChartCard(analysis.paceProfile, run.avgPace)
        if (gain != null && hasElevation) RunElevationCard(analysis.elevation, gain)
    } else if (hasSplits) {
        // The splits blurred behind a padlock.
        ProLocked(
            title = L("Splits, charts and heart rate"),
            message = L("See the detail of every run with Atala Pro"),
            onUnlock = onOpenPaywall,
        ) {
            RunSplitsCard(analysis.kmSplits.take(6), analysis.fastestSplit?.id)
        }
    } else if (hasElevation || hasHeartRate || fastSplits.size >= 2) {
        RunSummaryLockedRow(onOpenPaywall)
    }
}

@Composable
private fun CoachLine(state: AppState, run: RunRecord, justFinished: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        if (justFinished) {
            CoachPoseImage(state.coach, Modifier.width(76.dp).height(122.dp), CoachPose.Cheer)
        } else {
            CoachAvatar(state.coach, size = 36.dp)
        }
        Text(
            RunSummaryCoach.line(run),
            style = Theme.font(15).copy(lineHeight = 21.sp),
            color = Theme.ink,
            modifier = Modifier
                .weight(1f, fill = false)
                .background(Theme.bubble, RunBubbleShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FeelingRow(feeling: Feeling) {
    Row(
        Modifier
            .fillMaxWidth()
            .card()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeelingBadge(feeling)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(L("How it felt"), style = Theme.font(13), color = Theme.muted)
            Text(feeling.title, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
        }
    }
}

/** The run session planned on the day of this run and not done yet, when the run belongs to no session (MainRunMatching.swift). */
private fun sessionToCount(state: AppState, run: RunRecord): Workout? {
    if (run.workoutId != null && state.workout(run.workoutId) != null) return null
    return state.workouts(run.date).firstOrNull { it.kind.isRun && it.status != WorkoutStatus.Done }
}

/** "You ran 5.2 km today. Count it as today's session?" (MainCountRunCard). */
@Composable
private fun CountRunCard(run: RunRecord, session: Workout) {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    val isToday = PlanCalendar.isSameDay(run.date, Instant.now())
    val distance = Fmt.km(run.distanceKm)
    val duration = Fmt.duration(run.durationSec)
    val title = if (isToday) L("You ran %@ in %@ today", distance, duration)
    else L("You ran %@ in %@ on %@", distance, duration, Fmt.day(run.date, "EEEE"))
    Column(Modifier.fillMaxWidth().card(radius = 18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.AutoMirrored.Filled.DirectionsRun, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                Text(L("%@ is still open on that day.", session.title), style = Theme.font(13), color = Theme.muted)
            }
        }
        SecondaryButton(
            if (isToday) L("Count it as today's session") else L("Count it as %@'s session", Fmt.day(run.date, "EEEE")),
            onClick = {
                haptics.play(HapticKind.Success)
                val renamed = run.title.isEmpty() || run.title == "Run" || run.title == "Free run"
                store.addRun(run.copy(workoutId = session.id, title = if (renamed) session.title else run.title))
            },
            haptic = null,
        )
    }
}

/** Rule-based comment of the coach, computed from the run itself (RunSummaryCoach). */
object RunSummaryCoach {
    fun line(run: RunRecord): String {
        if (run.feeling == Feeling.Pain) {
            return L("Thank you for telling me. Rest for the next few days and skip anything that hurts. If it is still there after that, or if it gets worse, please see a health professional. I can lighten your plan in the meantime, just ask me.")
        }
        val reps = run.splits.filter { it.kind == StepKind.Work && it.distanceM > 0 }
        if (reps.size >= 2) return repsLine(reps, run.feeling)
        if (run.distanceM < 50) {
            return L("No distance was recorded this time (location off, or an indoor run). Your time, %@, still counts.", Fmt.duration(run.durationSec))
        }
        val distance = RunFormat.distance(run.distanceM)
        val pace = Fmt.pace(run.avgPace)
        return when (run.feeling) {
            Feeling.Easy -> L("That looked comfortable: %@ at %@/km. Exactly how an easy day should feel.", distance, pace)
            Feeling.Hard -> L("A tough one, and you saw it through: %@. Sleep well and keep the next run really easy.", distance)
            else -> L("%@ in %@, at %@/km. One more in the bank.", distance, Fmt.duration(run.durationSec), pace)
        }
    }

    private fun repsLine(reps: List<RunSplit>, feeling: Feeling?): String {
        val onTarget = reps.count { it.onTarget }
        val paces = reps.map { it.pace }
        val fastest = paces.minOrNull() ?: 0
        val fastestNumber = paces.indexOf(fastest).coerceAtLeast(0) + 1
        val lastIsFastest = fastestNumber == reps.size
        var text = when {
            onTarget == reps.size -> if (lastIsFastest) L("%@ steady reps, all on target, and the last one was your fastest.", reps.size)
            else L("%@ steady reps, all on target. Rep %@ was your fastest.", reps.size, fastestNumber)
            onTarget * 2 >= reps.size -> if (lastIsFastest) L("%@ of %@ reps on target. You finished with your fastest one, a good sign.", onTarget, reps.size)
            else L("%@ of %@ reps on target. Rep %@ was your fastest. Next time, aim for the same pace from first to last.", onTarget, reps.size, fastestNumber)
            else -> L("The pace was hard to hold today, and that happens. We will come back to it with fresh legs.")
        }
        text += " " + if (feeling == Feeling.Hard) L("It cost you a lot, so the next run stays really easy.") else L("Keep your next run easy to recover.")
        return text
    }
}
