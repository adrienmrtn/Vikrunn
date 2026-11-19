package com.atala.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanPhase
import com.atala.app.core.model.PlanWeek
import com.atala.app.core.model.ProAccess
import com.atala.app.core.model.TrainingPlan
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.paywall.ProUpsellCard
import com.atala.app.ui.root.AtalaPreview
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import com.atala.app.ui.today.FitText
import com.atala.app.ui.today.MainEmptyState
import com.atala.app.ui.today.MainHeroImage
import com.atala.app.ui.today.MainPlanEndedCard
import com.atala.app.ui.today.MainProgressBar
import com.atala.app.ui.today.MainStagedEntrance
import com.atala.app.ui.today.MainStagedScreen
import com.atala.app.ui.today.mainGlass
import com.atala.app.ui.today.mainPlanEnded
import com.atala.app.ui.today.mainPositionWeek
import com.atala.app.ui.today.mainStagedAppear
import com.atala.app.ui.today.mainThisWeek
import com.atala.app.ui.today.overlapTop
import java.time.Instant

/** Weeks shown open, kept across tab switches and configuration changes. */
private val expandedSaver = listSaver<MutableState<Set<String>>, String>(
    save = { it.value.toList() },
    restore = { mutableStateOf(it.toSet()) },
)

/** Plan tab (Atala/Features/Plan/PlanTabView.swift): goal, phases and every week with its sessions. */
@Composable
fun PlanScreen(nav: Navigation) {
    val state by collectAppState()
    val isPro by ProAccess.isPro.collectAsStateWithLifecycle()
    val plan = state.plan
    val planKey = plan?.id?.toString().orEmpty()
    var expanded by rememberSaveable(planKey, saver = expandedSaver) { mutableStateOf(emptySet()) }
    var didExpandCurrent by rememberSaveable(planKey) { mutableStateOf(false) }

    // The week the runner is in starts open.
    LaunchedEffect(planKey) {
        val week = state.mainPositionWeek
        if (!didExpandCurrent && week != null) {
            didExpandCurrent = true
            expanded = expanded + week.id.toString()
        }
    }

    MainStagedEntrance(MainStagedScreen.Plan) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 4.dp, bottom = 28.dp),
        ) {
            // Large title of the navigation bar.
            Text(
                L("Plan"),
                style = Theme.title(34),
                color = Theme.ink,
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp).semantics { heading() },
            )
            FitText(headerCaption(plan), Theme.font(13), Theme.muted, Modifier.mainStagedAppear(0), minScale = 0.8f)
            Box(Modifier.padding(top = 14.dp).mainStagedAppear(1)) { MainHeroImage("illu_plan", height = 170.dp) }
            if (plan != null) {
                val ended = state.mainPlanEnded
                PlanOverviewPanel(
                    plan, state.mainPositionWeek?.id?.toString(), ended,
                    Modifier.overlapTop(16.dp).padding(horizontal = 12.dp).mainStagedAppear(2),
                )
                if (ended) {
                    MainPlanEndedCard(nav.onOpenProfile, Modifier.padding(top = 14.dp).mainStagedAppear(3))
                }
                // "Now" only marks a week that really contains today.
                val currentId = state.mainThisWeek?.id
                Column(Modifier.padding(top = 12.dp).mainStagedAppear(3)) {
                    plan.weeks.forEachIndexed { index, week ->
                        val key = week.id.toString()
                        PlanWeekSection(
                            week = week,
                            number = index + 1,
                            isCurrent = week.id == currentId,
                            isExpanded = key in expanded,
                            isLast = index == plan.weeks.lastIndex,
                            onToggle = { expanded = if (key in expanded) expanded - key else expanded + key },
                            onOpen = { nav.onOpenWorkout(it.id) },
                        )
                    }
                }
                if (!isPro) {
                    ProUpsellCard(nav.onOpenPaywall, Modifier.padding(top = 20.dp).mainStagedAppear(4))
                }
            } else {
                MainEmptyState(
                    icon = Icons.Filled.CalendarMonth,
                    title = L("No plan yet"),
                    message = L("Choose a goal and your running days in your profile to build your plan."),
                    modifier = Modifier
                        .overlapTop(16.dp)
                        .padding(horizontal = 12.dp)
                        .mainStagedAppear(2)
                        .mainGlass(20.dp),
                    actionTitle = L("Open my profile"),
                    action = nav.onOpenProfile,
                )
            }
        }
    }
}

private fun headerCaption(plan: TrainingPlan?): String {
    if (plan == null) return L("No plan yet")
    val parts = mutableListOf(plan.goal.title)
    plan.raceDate?.let { parts += L("race on %@", Fmt.day(it, "d MMM")) }
    plan.targetSeconds?.let { parts += L("goal %@", Fmt.goalTime(it)) }
    return parts.joinToString(" · ")
}

/** Glass panel: progress through the plan and its phases (`PlanTabOverviewPanel`). */
@Composable
private fun PlanOverviewPanel(plan: TrainingPlan, currentWeekId: String?, ended: Boolean, modifier: Modifier) {
    val currentIndex = plan.weeks.indexOfFirst { it.id.toString() == currentWeekId }.coerceAtLeast(0)
    val currentNumber = currentIndex + 1
    val fraction = when {
        ended -> 1.0
        plan.weeks.isEmpty() -> 0.0
        else -> currentNumber.toDouble() / plan.weeks.size
    }
    Column(
        modifier
            .fillMaxWidth()
            .mainGlass(20.dp)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MainProgressBar(fraction, label = L("Plan progress"))
        Row(Modifier.fillMaxWidth()) {
            Text(
                if (ended) L("Plan complete") else L("Week %@ of %@", currentNumber, plan.weeks.size),
                style = Theme.font(13, FontWeight.SemiBold), color = Theme.ink,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f).width(8.dp))
            Text(
                trailingText(plan, currentNumber, ended),
                style = Theme.font(13), color = Color(0xFF5A5A5A),
                modifier = Modifier.alignByBaseline(),
            )
        }
        PlanPhaseBar(plan.weeks, currentIndex, Modifier.padding(top = 2.dp))
    }
}

private fun trailingText(plan: TrainingPlan, currentNumber: Int, ended: Boolean): String {
    val today = PlanCalendar.startOfDay(Instant.now())
    if (ended && plan.raceDate == null) {
        val done = plan.allWorkouts.count { it.status == WorkoutStatus.Done }
        return L("%@ of %@ sessions done", done, plan.allWorkouts.size)
    }
    val raceDate = plan.raceDate
    if (raceDate != null) {
        val days = PlanCalendar.daysBetween(today, PlanCalendar.startOfDay(raceDate))
        if (days < 0) return L("Race done")
        if (days == 0) return L("Race day")
        if (days < 14) return if (days == 1) L("Race in %@ day", days) else L("Race in %@ days", days)
        return L("Race in %@ weeks", days / 7)
    }
    val left = plan.weeks.size - currentNumber
    if (left <= 0) return L("Last week")
    return if (left == 1) L("%@ week to go", left) else L("%@ weeks to go", left)
}

private class PhaseGroup(val phase: PlanPhase, val indices: MutableList<Int>)

/** One small segment per week, grouped and labelled by phase (`PlanTabPhaseBar`). */
@Composable
private fun PlanPhaseBar(weeks: List<PlanWeek>, currentIndex: Int, modifier: Modifier) {
    val groups = mutableListOf<PhaseGroup>()
    weeks.forEachIndexed { index, week ->
        val last = groups.lastOrNull()
        if (last != null && last.phase == week.phase) last.indices += index else groups += PhaseGroup(week.phase, mutableListOf(index))
    }
    val description = L("Phases: %@", groups.joinToString(", ") { it.phase.title })
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val spacing = 4.dp
        val count = maxOf(1, weeks.size)
        val unit = maxOf(2.dp, (maxWidth - spacing * (count - 1)) / count)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            groups.forEach { group ->
                val width = unit * group.indices.size + spacing * (group.indices.size - 1)
                val isCurrent = currentIndex in group.indices
                Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        group.indices.forEach { index ->
                            val color = when {
                                index < currentIndex -> Theme.green
                                index == currentIndex -> Theme.orange
                                else -> Theme.ink.copy(alpha = 0.12f)
                            }
                            Box(Modifier.width(unit).height(5.dp).background(color, CircleShape))
                        }
                    }
                    FitText(
                        group.phase.title,
                        Theme.font(11, if (isCurrent) FontWeight.SemiBold else FontWeight.Normal),
                        if (isCurrent) Theme.ink else Theme.muted,
                        minScale = 0.6f,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun PlanScreenPreview() {
    AtalaPreview { PlanScreen(Navigation.Preview) }
}
