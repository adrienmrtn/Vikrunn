package com.atala.app.ui.plan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.store.planCanMove
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.SecondaryButton
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.AtalaPreview
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme
import com.atala.app.ui.today.FitText
import com.atala.app.ui.today.MainEmptyState
import com.atala.app.ui.today.MainStatusBadge
import com.atala.app.ui.today.MainWorkoutStats
import com.atala.app.ui.today.lineHeight
import com.atala.app.ui.today.mainGlass
import java.util.UUID

/**
 * A session of the plan, with the actions that fit its state (Atala/Features/Today/WorkoutDetailView.swift):
 * a run starts the run flow or is entered by hand, strength and stretching start the guided session
 * or are simply ticked. A session not done yet can be moved to another day.
 */
@Composable
fun WorkoutDetailScreen(workoutId: UUID, nav: Navigation) {
    val state by collectAppState()
    val workout = state.workout(workoutId)
    var showMove by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding(),
    ) {
        DetailTopBar(
            title = workout?.kind?.title ?: L("Session"),
            onBack = nav.onBack,
            trailing = { if (workout != null) MoreMenu(workout, onMove = { showMove = true }) },
        )
        if (workout == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MainEmptyState(
                    icon = Icons.Filled.EventBusy,
                    title = L("Session not found"),
                    message = L("This session is no longer in your plan."),
                    actionTitle = L("Back"),
                    action = nav.onBack,
                )
            }
        } else {
            Box(Modifier.weight(1f)) { DetailContent(workout, state.run(workout.runId), nav) }
            if (workout.status != WorkoutStatus.Done) ActionBar(workout, nav)
        }
    }

    if (showMove) {
        PlanMoveSheet(workoutId, onDismiss = { showMove = false })
    }
}

/** Inline bar: back, the kind of session as title, and Move (or the menu of a done session). */
@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit) {
    val haptics = rememberHaptics()
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    haptics.tap()
                    onBack()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, L("Back"), tint = Theme.ink, modifier = Modifier.size(20.dp))
        }
        Text(
            title,
            style = Theme.font(17, FontWeight.SemiBold),
            color = Theme.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp),
        )
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { trailing() }
    }
}

/** "More" menu only for a strength or stretching session already marked as done; "Move" otherwise. */
@Composable
private fun MoreMenu(workout: Workout, onMove: () -> Unit) {
    val store = LocalAppStore.current
    val state by collectAppState()
    val haptics = rememberHaptics()
    var open by rememberSaveable { mutableStateOf(false) }
    if (workout.status == WorkoutStatus.Done && !workout.kind.isRun) {
        Box {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { open = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MoreHoriz, L("More options"), tint = Theme.ink, modifier = Modifier.size(24.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color.White) {
                DropdownMenuItem(
                    text = { Text(L("Mark as not done"), style = Theme.font(16), color = Theme.ink) },
                    onClick = {
                        open = false
                        haptics.tap()
                        store.setStatus(WorkoutStatus.Planned, workout.id)
                        store.refreshMissed()
                    },
                )
            }
        }
    } else if (state.planCanMove(workout)) {
        val label = L("Move this session to another day")
        Row(
            Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    haptics.tap()
                    onMove()
                }
                .semantics(mergeDescendants = true) { contentDescription = label }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = Theme.ink, modifier = Modifier.size(18.dp))
            Text(L("Move"), style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
        }
    }
}

/** The illustration is the first element of the scroll content and scrolls away like everything else. */
@Composable
private fun DetailContent(workout: Workout, run: RunRecord?, nav: Navigation) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box {
            Illustration(workout.kind)
            Column(
                Modifier.padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 150.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SummaryPanel(workout)
                WorkoutDetailSteps(workout, Modifier.padding(horizontal = 4.dp))
                WhySection(workout, Modifier.padding(horizontal = 4.dp))
                if (workout.status == WorkoutStatus.Done && run != null) {
                    LinkedRun(run, onOpen = { nav.onOpenRunSummary(run.id) }, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

/** Header image: the running track for runs, a dedicated picture for strength and stretching. */
@Composable
private fun Illustration(kind: WorkoutKind) {
    val name = when (kind) {
        WorkoutKind.Strength -> "illu_strength"
        WorkoutKind.Stretching -> "illu_stretching"
        else -> "illu_session"
    }
    val id = AtalaImages[name] ?: AtalaImages["illu_session"] ?: return
    Box(Modifier.fillMaxWidth().height(240.dp)) {
        Image(painterResource(id), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(50.dp)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White))),
        )
    }
}

@Composable
private fun SummaryPanel(workout: Workout) {
    Column(
        Modifier
            .fillMaxWidth()
            .mainGlass(24.dp)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FitText(
                    L("%@ · effort %@ of 10", Fmt.day(workout.date), workout.effort),
                    Theme.font(13), Color(0xFF5A5A5A), Modifier.weight(1f), minScale = 0.8f,
                )
                MainStatusBadge(workout.status)
            }
            FitText(
                workout.title,
                Theme.font(32, FontWeight.Bold).copy(letterSpacing = (-0.8).sp),
                Theme.ink,
                maxLines = 2,
                minScale = 0.7f,
            )
            if (workout.subtitle.isNotEmpty()) {
                Text(workout.subtitle, style = Theme.font(14), color = Theme.muted)
            }
        }
        MainWorkoutStats(workout)
    }
}

@Composable
private fun WhySection(workout: Workout, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(L("Why this session"), style = Theme.font(13, FontWeight.SemiBold), color = Theme.ink)
        Text(workout.why, style = Theme.font(14).copy(lineHeight = lineHeight(14, 4)), color = Theme.body)
    }
}

@Composable
private fun LinkedRun(run: RunRecord, onOpen: () -> Unit, modifier: Modifier) {
    val haptics = rememberHaptics()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(L("Your run"), style = Theme.font(13, FontWeight.SemiBold), color = Theme.ink)
        Row(
            Modifier
                .fillMaxWidth()
                .card(radius = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    haptics.tap()
                    onOpen()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Filled.Check, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(L("%@ in %@", Fmt.km(run.distanceKm), Fmt.duration(run.durationSec)), style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                Text(
                    L("%@/km on average · see the summary", Fmt.pace(run.avgPace)),
                    style = Theme.font(13), color = Theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Theme.faint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ActionBar(workout: Workout, nav: Navigation) {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = Theme.hairline)
        Row(
            Modifier.padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                !workout.kind.isRun -> {
                    // Strength and stretching, planned or missed: a guided session, or a simple tick.
                    SecondaryButton(L("Mark as done"), onClick = {
                        haptics.play(HapticKind.Success)
                        store.setStatus(WorkoutStatus.Done, workout.id)
                    }, modifier = Modifier.weight(1f), haptic = null)
                    PrimaryButton(L("Start"), onClick = { nav.onStartGuided(workout.id) }, modifier = Modifier.weight(1f))
                }
                workout.status == WorkoutStatus.Missed -> {
                    PrimaryButton(L("I did it"), onClick = { nav.onAddPastRun(workout.id) }, modifier = Modifier.weight(1f))
                }
                else -> {
                    SecondaryButton(L("I already did it"), onClick = { nav.onAddPastRun(workout.id) }, modifier = Modifier.weight(1f))
                    PrimaryButton(L("Start"), onClick = { nav.onStartRun(workout.id) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun WorkoutDetailPreview() {
    AtalaPreview {
        val state by collectAppState()
        state.nextRun?.let { WorkoutDetailScreen(it.id, Navigation.Preview) }
    }
}
