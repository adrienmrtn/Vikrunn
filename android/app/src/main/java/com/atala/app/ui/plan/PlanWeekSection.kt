package com.atala.app.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanWeek
import com.atala.app.core.model.Weekday
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import com.atala.app.ui.today.FitText
import com.atala.app.ui.today.MainStatusBadge
import com.atala.app.ui.today.icon
import com.atala.app.ui.today.kmNumber
import com.atala.app.ui.today.mainAmountText
import com.atala.app.ui.today.mainDoneKm
import com.atala.app.ui.today.mainDotColor
import java.time.Instant

/** One week of the plan: a header line, and its sessions when expanded (`PlanTabWeekSection`). */
@Composable
internal fun PlanWeekSection(
    week: PlanWeek,
    number: Int,
    isCurrent: Boolean,
    isExpanded: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onOpen: (Workout) -> Unit,
) {
    val state by collectAppState()
    val haptics = rememberHaptics()
    val doneCount = week.workouts.count { it.status == WorkoutStatus.Done }
    val missedCount = week.workouts.count { it.status == WorkoutStatus.Missed }
    // True once the week has started (so "done of planned" makes sense).
    val hasStarted = PlanCalendar.startOfDay(week.startDate) <= Instant.now()
    val collapsedSummary = if (hasStarted) {
        L("%@ · %@ of %@", Fmt.km(week.plannedKm), doneCount, week.workouts.size)
    } else {
        Fmt.km(week.plannedKm)
    }
    val doneText = kmNumber(state.mainDoneKm(week))
    val totalsText = if (missedCount > 0) {
        L("%@ of %@ run · %@ of %@ sessions done · %@ missed", doneText, Fmt.km(week.plannedKm), doneCount, week.workouts.size, missedCount)
    } else {
        L("%@ of %@ run · %@ of %@ sessions done", doneText, Fmt.km(week.plannedKm), doneCount, week.workouts.size)
    }
    // Past weeks are greyed, upcoming ones stay black.
    val titleColor = if (isExpanded || isCurrent || PlanCalendar.addDays(week.startDate, 7) > Instant.now()) Theme.ink else Theme.muted
    val hint = if (isExpanded) L("Hides the sessions") else L("Shows the sessions")

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable(onClickLabel = hint, role = Role.Button) {
                    haptics.tap()
                    onToggle()
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                L("Week %@ · %@", number, week.phase.title),
                style = Theme.font(if (isExpanded) 17 else 15, if (isExpanded) FontWeight.Bold else FontWeight.Normal),
                color = titleColor,
            )
            if (isCurrent && !isExpanded) {
                Text(
                    L("Now"),
                    style = Theme.font(11, FontWeight.SemiBold),
                    color = Theme.orangeDeep,
                    modifier = Modifier
                        .background(Theme.orangeTint, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Box(Modifier.weight(1f))
            Text(
                if (isExpanded) Fmt.km(week.plannedKm) else collapsedSummary,
                style = Theme.font(14, if (isExpanded) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isExpanded) Theme.ink else Theme.muted,
                maxLines = 1,
            )
            Icon(
                if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                null, tint = Theme.faint, modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
        ) {
            Column(Modifier.padding(bottom = 6.dp)) {
                if (week.workouts.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(42.dp), contentAlignment = Alignment.CenterStart) {
                        Text(L("No session this week."), style = Theme.font(14), color = Theme.muted)
                    }
                }
                week.workouts.forEach { workout ->
                    PlanSessionRow(workout, Modifier.clickable {
                        haptics.tap()
                        onOpen(workout)
                    })
                }
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(week.phase.summary, style = Theme.font(12), color = Theme.muted, maxLines = 1)
                    FitText(totalsText, Theme.font(12, FontWeight.Medium), Theme.body, minScale = 0.75f)
                }
            }
        }
        if (!isLast) HorizontalDivider(color = Theme.hairline)
    }
}

/** One session of a week: dot, day, title, amount and state (`PlanTabSessionRow`). */
@Composable
internal fun PlanSessionRow(workout: Workout, modifier: Modifier = Modifier) {
    val isToday = PlanCalendar.isToday(workout.date)
    val done = workout.status == WorkoutStatus.Done
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(workout.mainDotColor, CircleShape))
        Text(
            Weekday.short(PlanCalendar.mondayIndex(workout.date)),
            style = Theme.font(13, if (isToday) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isToday) Theme.ink else Theme.muted,
            maxLines = 1,
            modifier = Modifier.width(32.dp),
        )
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
            Icon(workout.kind.icon, null, tint = Theme.muted, modifier = Modifier.size(14.dp))
        }
        FitText(
            rowTitle(workout),
            Theme.font(15, if (isToday) FontWeight.SemiBold else FontWeight.Normal)
                .copy(textDecoration = if (done) TextDecoration.LineThrough else null),
            if (done) Theme.muted else Theme.ink,
            modifier = Modifier.weight(1f),
            minScale = 0.8f,
        )
        if (workout.status == WorkoutStatus.Missed) {
            MainStatusBadge(WorkoutStatus.Missed)
        } else if (done) {
            Icon(Icons.Filled.Check, L("Done"), tint = Theme.green, modifier = Modifier.size(13.dp))
        }
        Text(
            workout.mainAmountText,
            style = Theme.font(13, if (isToday) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isToday) Theme.ink else Theme.muted,
        )
    }
}

/** "Intervals · 6 × 800 m" for hard sessions, the plain title otherwise. */
private fun rowTitle(workout: Workout): String = when (workout.kind) {
    WorkoutKind.Intervals, WorkoutKind.Tempo, WorkoutKind.RacePace ->
        if (workout.title.lowercase().contains(workout.kind.title.lowercase())) workout.title
        else "${workout.kind.title} · ${workout.title}"
    else -> workout.title
}
