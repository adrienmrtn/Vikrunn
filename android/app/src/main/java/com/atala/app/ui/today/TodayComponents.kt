package com.atala.app.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanWeek
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.Weekday
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.SecondaryButton
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.time.Instant
import java.time.ZoneId

/** Glass card with the session of the day, or the next one on a rest day (`TodaySessionCard`). */
@Composable
internal fun TodaySessionCard(workout: Workout, isToday: Boolean, restDay: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val caption = if (isToday) workout.kind.title else L("Next · %@ · %@", Fmt.day(workout.date, "EEEE d MMM"), workout.kind.title)
    Column(
        modifier
            .fillMaxWidth()
            .mainGlass(22.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (restDay) {
                Text(
                    L("Rest day"),
                    style = Theme.font(12, FontWeight.SemiBold),
                    color = Theme.green,
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .background(Theme.greenTint, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(workout.mainDotColor, CircleShape))
                Text(caption, style = Theme.font(13), color = Color(0xFF5A5A5A), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                MainStatusBadge(workout.status)
            }
            FitText(
                workout.title,
                Theme.font(26, FontWeight.Bold).copy(letterSpacing = (-0.5).sp),
                Theme.ink,
                maxLines = 2,
                minScale = 0.75f,
            )
        }
        MainWorkoutStats(workout)
        // The haptic follows the tap, not the press.
        PrimaryButton(L("View session"), onClick = onOpen)
    }
}

/** Glass card used when there is nothing to run (no plan, or plan finished) (`TodayMessageCard`). */
@Composable
internal fun TodayMessageCard(title: String, message: String, actionTitle: String, modifier: Modifier = Modifier, action: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .mainGlass(22.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = Theme.font(24, FontWeight.Bold).copy(letterSpacing = (-0.5).sp), color = Theme.ink)
        Text(message, style = Theme.font(14).copy(lineHeight = lineHeight(14, 4)), color = Theme.body)
        PrimaryButton(actionTitle, onClick = action)
    }
}

private enum class DayState { None, Planned, Done, Missed }

/** Monday-to-Sunday strip with the state of each day (`TodayWeekStrip`). */
@Composable
internal fun TodayWeekStrip(modifier: Modifier = Modifier) {
    val state by collectAppState()
    val monday = PlanCalendar.mondayOfWeek(Instant.now())
    Row(modifier.fillMaxWidth()) {
        for (offset in 0 until 7) {
            val day = PlanCalendar.addDays(monday, offset)
            val workouts = state.workouts(day)
            val dayState = when {
                workouts.isEmpty() -> DayState.None
                workouts.any { it.status == WorkoutStatus.Missed } -> DayState.Missed
                workouts.all { it.status == WorkoutStatus.Done } -> DayState.Done
                else -> DayState.Planned
            }
            DayColumn(day, offset, dayState, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayColumn(day: Instant, offset: Int, dayState: DayState, modifier: Modifier) {
    val isToday = PlanCalendar.isToday(day)
    val number = day.atZone(ZoneId.systemDefault()).dayOfMonth
    val letter = Weekday.short(offset + 1).take(1)
    val stateLabel = when (dayState) {
        DayState.None -> L("no session")
        DayState.Planned -> L("session planned")
        DayState.Done -> L("done")
        DayState.Missed -> L("missed")
    }
    val description = L("%@ %@, %@", Weekday.long(offset + 1), number, stateLabel)
    Column(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            letter,
            style = Theme.font(12, if (isToday) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isToday) Theme.ink else Theme.muted,
        )
        Box(
            Modifier
                .size(36.dp)
                .background(if (isToday) Theme.ink else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = Theme.font(15, if (isToday) FontWeight.SemiBold else FontWeight.Medium),
                color = if (isToday) Color.White else Theme.ink,
            )
        }
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            when (dayState) {
                DayState.None -> Unit
                DayState.Planned -> Canvas(Modifier.size(7.dp)) {
                    val width = 1.5.dp.toPx()
                    drawCircle(Theme.faint, radius = (size.minDimension - width) / 2, style = Stroke(width))
                }
                DayState.Done -> Icon(Icons.Filled.CheckCircle, null, tint = Theme.green, modifier = Modifier.size(14.dp))
                DayState.Missed -> Icon(Icons.Filled.Cancel, null, tint = Theme.orange, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Strength and stretching sessions of the day (`TodayExtraSessions`). */
@Composable
internal fun TodayExtraSessions(workouts: List<Workout>, onOpen: (Workout) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MainSectionTitle(L("Also today"))
        workouts.forEach { workout ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .card(radius = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        haptics.tap()
                        onOpen(workout)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(workout.kind.icon, tint = Theme.yellowDeep, background = Theme.yellowTint, size = 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(workout.title, style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                    Text(workout.subtitle, style = Theme.font(13), color = Theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MainStatusBadge(workout.status)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Theme.faint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Weekly volume: kilometres and runs done against the plan (`TodayWeekVolume`). */
@Composable
internal fun TodayWeekVolume(week: PlanWeek, modifier: Modifier = Modifier) {
    val state by collectAppState()
    val done = state.mainDoneKm(week)
    val planned = week.plannedKm
    val runs = week.workouts.filter { it.kind.isRun }
    val runsDone = runs.count { it.status == WorkoutStatus.Done }
    val missed = week.workouts.count { it.status == WorkoutStatus.Missed }
    val summary = if (missed > 0) {
        if (runs.size == 1) L("%@ of %@ run done · %@ missed", runsDone, runs.size, missed)
        else L("%@ of %@ runs done · %@ missed", runsDone, runs.size, missed)
    } else {
        if (runs.size == 1) L("%@ of %@ run done", runsDone, runs.size) else L("%@ of %@ runs done", runsDone, runs.size)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MainSectionTitle(L("This week"), trailing = L("%@ of %@", kmNumber(done), Fmt.km(planned)))
        MainProgressBar(if (planned > 0) done / planned else 0.0, tint = Theme.green, label = L("Weekly volume"))
        Text(summary, style = Theme.font(13), color = Theme.muted)
    }
}

/** Days left before the race, when the plan has one (`TodayRaceCountdown`). */
@Composable
internal fun TodayRaceCountdown(modifier: Modifier = Modifier) {
    val state by collectAppState()
    val plan = state.plan ?: return
    val raceDate = plan.raceDate ?: return
    val days = PlanCalendar.daysBetween(PlanCalendar.startOfDay(Instant.now()), PlanCalendar.startOfDay(raceDate))
    if (days < 0) return
    val title = raceTitle(plan.goal, days)
    val detail = raceDetail(plan, raceDate)
    Row(
        modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .background(Theme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = "$title, $detail" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Filled.SportsScore, tint = Theme.orangeDeep, background = Theme.orangeTint, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
            Text(detail, style = Theme.font(13), color = Theme.muted)
        }
    }
}

private fun raceTitle(goal: GoalType, days: Int): String = when (days) {
    0 -> L("%@: race day is today", goal.title)
    1 -> L("%@ tomorrow", goal.title)
    else -> L("%@ in %@ days", goal.title, days)
}

private fun raceDetail(plan: TrainingPlan, raceDate: Instant): String {
    val text = Fmt.day(raceDate, "EEEE d MMMM")
    val target = plan.targetSeconds ?: return text
    return L("%@ · goal %@", text, Fmt.goalTime(target))
}

/** The next sessions, one line each (`TodayUpcomingList`). */
@Composable
internal fun TodayUpcomingList(workouts: List<Workout>, onOpen: (Workout) -> Unit) {
    val haptics = rememberHaptics()
    Column {
        workouts.forEach { workout ->
            HorizontalDivider(color = Theme.hairline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clickable {
                        haptics.tap()
                        onOpen(workout)
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(workout.mainDotColor, CircleShape))
                Text(
                    "${Fmt.day(workout.date, "EEE d")} · ${workout.title}",
                    style = Theme.font(15), color = Theme.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(workout.mainAmountText, style = Theme.font(14), color = Theme.muted)
            }
        }
    }
}

/** "You ran 5.2 km today. Count it as today's session?" (`MainCountRunCard`). */
@Composable
internal fun MainCountRunCard(run: RunRecord, session: Workout, modifier: Modifier = Modifier) {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    val isToday = PlanCalendar.isToday(run.date)
    val distance = Fmt.km(run.distanceKm)
    val duration = Fmt.duration(run.durationSec)
    val title = if (isToday) L("You ran %@ in %@ today", distance, duration)
    else L("You ran %@ in %@ on %@", distance, duration, Fmt.day(run.date, "EEEE"))
    val button = if (isToday) L("Count it as today's session") else L("Count it as %@'s session", Fmt.day(run.date, "EEEE"))
    Column(
        modifier
            .fillMaxWidth()
            .card(radius = 18.dp)
            .padding(14.dp)
            .animateContentSize(tween(250)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            IconTile(Icons.AutoMirrored.Filled.DirectionsRun, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                Text(L("%@ is still open on that day.", session.title), style = Theme.font(13), color = Theme.muted)
            }
        }
        SecondaryButton(button, onClick = {
            haptics.play(HapticKind.Success)
            store.mainCount(run.id, session.id)
        }, haptic = null)
    }
}
