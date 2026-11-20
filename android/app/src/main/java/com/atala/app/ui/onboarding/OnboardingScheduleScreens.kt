package com.atala.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.CoachTone
import com.atala.app.core.model.Constraint
import com.atala.app.core.model.Weekday
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.OptionCard
import com.atala.app.ui.components.SelectionMark
import com.atala.app.ui.components.VignetteImage
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme

/** Screen 17: tick the days you can run. One ticked day, one session. */
@Composable
fun OnboardingDaysScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    val runDays = state.answers.runDays
    val count = runDays.size
    val haptics = rememberHaptics()

    fun toggle(day: Int) {
        store.updateAnswers { answers ->
            val days = (if (day in answers.runDays) answers.runDays - day else answers.runDays + day).sorted()
            val long = when {
                days.size == 1 -> days[0]
                answers.longRunDay != null && answers.longRunDay !in days -> null
                else -> answers.longRunDay
            }
            answers.copy(runDays = days, longRunDay = long)
        }
    }

    OnboardingScaffold(title = L("When can you run?"), subtitle = L("One ticked day, one session."), canContinue = count > 0) {
        Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp)) {
            for (day in 1..7) {
                if (day > 1) HorizontalDivider(thickness = 1.dp, color = Color(0xFFEFEFEF))
                ScheduleDayRow(day, ticked = day in runDays) {
                    haptics.select()
                    toggle(day)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Theme.surface, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoachAvatar(state.coach, size = 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(scheduleSummaryTitle(count), style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
                Text(scheduleSummaryDetail(count), style = Theme.font(13), color = Theme.green)
            }
        }
    }
}

private fun scheduleSummaryTitle(count: Int): String = when (count) {
    0 -> L("No day ticked yet")
    1 -> L("1 day ticked, 1 session a week")
    else -> L("%@ days ticked, %@ sessions a week", count, count)
}

private fun scheduleSummaryDetail(count: Int): String = when (count) {
    0 -> L("Tick at least one day to continue")
    1 -> L("A gentle start. You can add a day later")
    2 -> L("Enough to build a real habit")
    3, 4 -> L("A good rhythm to progress steadily")
    5, 6 -> L("A full week: I'll keep most runs easy")
    else -> L("Every day: I'll make sure you recover")
}

@Composable
private fun ScheduleDayRow(day: Int, ticked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val fill by animateColorAsState(if (ticked) Theme.green else Color.Transparent, tween(150), label = "tick")
    val stroke by animateColorAsState(if (ticked) Theme.green else Color(0xFFC4C4C4), tween(150), label = "tick border")
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { selected = ticked }
            .clickable(role = Role.Checkbox, interactionSource = null, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Weekday.long(day),
            style = Theme.font(16, if (ticked) FontWeight.SemiBold else FontWeight.Normal),
            color = if (ticked) Theme.ink else Theme.muted,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(26.dp)
                .background(fill, shape)
                .border(1.5.dp, stroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp).alpha(if (ticked) 1f else 0f))
        }
    }
}

/** Screen 18: the long run day, among the ticked days. */
@Composable
fun OnboardingLongRunScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    val days = state.answers.runDays.sorted()
    val longDay = state.answers.longRunDay
    OnboardingScaffold(title = L("Which day for your long run?"), canContinue = longDay != null && longDay in days) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEach { day ->
                OnboardingPlainOptionRow(Weekday.long(day), selected = longDay == day) {
                    store.updateAnswers { it.copy(longRunDay = day) }
                }
            }
        }
    }
}

/** Screen 19: constraints, all optional. No injury option: no injury or medical wording at sign-up. */
@Composable
fun OnboardingConstraintsScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    val chosen = state.answers.constraints
    OnboardingScaffold(title = L("Anything else to know?"), subtitle = L("Optional.")) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Constraint.entries.filter { it != Constraint.PastInjury }.forEach { constraint ->
                OptionCard(
                    title = constraint.title,
                    subtitle = scheduleConstraintSubtitle(constraint),
                    selected = constraint in chosen,
                    multi = true,
                    onClick = {
                        store.updateAnswers {
                            it.copy(constraints = if (constraint in it.constraints) it.constraints - constraint else it.constraints + constraint)
                        }
                    },
                ) {
                    VignetteImage(constraint.vignette)
                }
            }
        }
    }
}

private fun scheduleConstraintSubtitle(constraint: Constraint): String = when (constraint) {
    Constraint.PastInjury -> L("Your coach keeps it in mind")
    Constraint.Travel -> L("Tell your coach, sessions can move")
    Constraint.Treadmill -> L("You can log those runs by hand")
    Constraint.Strength -> L("Good news: it makes you a stronger runner")
    Constraint.IrregularSleep -> L("Easy runs stay truly easy")
}

/** Screen 21: the coach's tone. */
@Composable
fun OnboardingToneScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    val haptics = rememberHaptics()
    OnboardingScaffold(title = L("Which tone do you prefer?")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CoachTone.entries.forEach { tone ->
                val selected = state.answers.tone == tone
                Column(
                    Modifier
                        .fillMaxWidth()
                        .card(selected)
                        .semantics { this.selected = selected }
                        .clickable(role = Role.RadioButton) {
                            haptics.select()
                            store.updateAnswers { it.copy(tone = tone) }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tone.title, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink, modifier = Modifier.weight(1f).padding(end = 12.dp))
                        SelectionMark(selected)
                    }
                    Text("“${tone.example}”", style = Theme.font(14).copy(lineHeight = 20.sp), color = Theme.body)
                }
            }
        }
        Text(L("You can change the tone at any time."), style = Theme.font(13), color = Theme.muted)
    }
}
