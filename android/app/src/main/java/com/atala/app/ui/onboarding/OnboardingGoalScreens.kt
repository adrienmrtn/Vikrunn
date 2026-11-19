package com.atala.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.Motivation
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.plan.PlanEngine
import com.atala.app.ui.components.OptionCard
import com.atala.app.ui.components.VignetteImage
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Screen 12 (step `Goal`), written to `answers.goal`. */
@Composable
fun OnboardingGoalScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    fun select(goal: GoalType) = store.updateAnswers { answers ->
        val km = goal.distanceKm
        val target = answers.targetSeconds
        when {
            // The race and target screens are skipped for these goals.
            goal.isOpenEnded -> answers.copy(goal = goal, hasRace = false, raceDate = null, targetSeconds = null)
            target != null && km != null -> answers.copy(goal = goal, targetSeconds = OnboardingTimeRules.clamp(target, km))
            else -> answers.copy(goal = goal)
        }
    }
    OnboardingScaffold(title = L("What's your goal?"), canContinue = state.answers.goal != null) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            GoalType.entries.forEach { goal ->
                OptionCard(
                    goal.title,
                    selected = state.answers.goal == goal,
                    onClick = { select(goal) },
                    subtitle = goal.subtitle,
                ) { VignetteImage(goal.vignette) }
            }
        }
    }
}

/** The days a race can be on: from tomorrow to the end of the longest plan. */
private fun raceRange(): ClosedRange<LocalDate> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val monday = PlanCalendar.mondayOfWeek(Instant.now()).atZone(zone).toLocalDate()
    val first = today.plusDays(1)
    val last = monday.plusDays((PlanEngine.MAX_WEEKS * 7 - 1).toLong())
    return first..maxOf(first, last)
}

private fun LocalDate.instant(): Instant = atStartOfDay(ZoneId.systemDefault()).toInstant()

/** Screen 13 (step `Race`): a race in sight? A plan covers eight weeks at most. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingRaceScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    val range = remember { raceRange() }
    val zone = ZoneId.systemDefault()
    // null until the user answers.
    var hasRace by remember { mutableStateOf<Boolean?>(null) }
    var date by remember {
        val saved = store.current.answers.raceDate?.atZone(zone)?.toLocalDate()
        mutableStateOf((saved ?: LocalDate.now(zone).plusDays(42)).coerceIn(range))
    }
    var showPicker by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    LaunchedEffect(Unit) {
        if (store.current.answers.hasRace) {
            hasRace = true
            // A date saved on an earlier day can be out of range by now: store the corrected one.
            val day = date.instant()
            if (store.current.answers.raceDate != day) store.updateAnswers { it.copy(raceDate = day) }
        }
    }

    fun choose(value: Boolean) {
        hasRace = value
        store.updateAnswers { it.copy(hasRace = value, raceDate = if (value) date.instant() else null) }
    }

    val weeksAway = maxOf(1, ((java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(zone), date)).toInt() + 6) / 7)
    val weeksNote = if (weeksAway <= 2) {
        L("That's in %@: very soon. I'll keep you fresh for your %@.", OnboardingCopy.weeks(weeksAway), OnboardingCopy.goalNoun(state.answers.goal))
    } else {
        L("That's in %@: I'll build your whole plan towards that day.", OnboardingCopy.weeks(weeksAway))
    }

    OnboardingScaffold(title = L("A race in sight?"), canContinue = hasRace != null) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OptionCard(
                L("Yes, I have a date"),
                selected = hasRace == true,
                onClick = { choose(true) },
                subtitle = L("I set the whole plan on race day"),
            ) { VignetteImage("vignette_race_date") }
            OptionCard(
                L("Not yet"),
                selected = hasRace == false,
                onClick = { choose(false) },
                subtitle = L("We start with an 8-week plan"),
            ) { VignetteImage("vignette_race_none") }
        }
        AnimatedVisibility(
            visible = hasRace == true,
            enter = fadeIn(tween(250)) + expandVertically(tween(250, easing = OnboardingEasing.easeInOut), expandFrom = Alignment.Top),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(250, easing = OnboardingEasing.easeInOut), shrinkTowards = Alignment.Top),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(L("Race date"), style = Theme.font(13, FontWeight.SemiBold), color = Theme.ink)
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .background(Color.White, shape)
                            .border(1.dp, Theme.controlBorder, shape)
                            .clickable(role = Role.Button, onClickLabel = L("Race date")) {
                                haptics.tap()
                                showPicker = true
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Fmt.day(date.instant(), "EEEE d MMMM yyyy"),
                            style = Theme.font(16, FontWeight.SemiBold),
                            color = Theme.ink,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        // The compact date button of the iPhone.
                        Text(
                            Fmt.day(date.instant(), "d MMM yyyy"),
                            style = Theme.font(15),
                            color = Theme.green,
                            modifier = Modifier
                                .background(OnboardingControlFill, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Text(weeksNote, style = Theme.font(14, FontWeight.SemiBold), color = Theme.green)
                Text(
                    L("A plan covers %@ weeks at most. If your race is further away, choose \"Not yet\": we start building now.", PlanEngine.MAX_WEEKS),
                    style = Theme.font(13).copy(lineHeight = 18.sp),
                    color = Theme.muted,
                )
            }
        }
    }

    if (showPicker) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = range.start.year..range.endInclusive.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in range

                override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
            },
        )
        val colors = DatePickerDefaults.colors(
            containerColor = Color.White,
            selectedDayContainerColor = Theme.ink,
            selectedDayContentColor = Color.White,
            todayDateBorderColor = Theme.green,
            todayContentColor = Theme.green,
            selectedYearContainerColor = Theme.ink,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().coerceIn(range)
                        date = picked
                        haptics.select()
                        store.updateAnswers { it.copy(raceDate = picked.instant()) }
                    }
                    showPicker = false
                }) { Text(L("Done"), color = Theme.ink, style = Theme.font(15, FontWeight.SemiBold)) }
            },
            colors = colors,
        ) {
            DatePicker(state = picker, colors = colors, showModeToggle = false)
        }
    }
}

/** Screen 15 (step `Why`), written to `answers.motivation`. */
@Composable
fun OnboardingWhyScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    val title = when (state.answers.goal ?: GoalType.Regular) {
        GoalType.FiveK -> L("Why this 5K?")
        GoalType.TenK -> L("Why this 10K?")
        GoalType.Half -> L("Why this half marathon?")
        GoalType.Regular -> L("Why do you want to run regularly?")
        GoalType.Shape -> L("Why do you want to get in shape?")
    }
    OnboardingScaffold(title = title, canContinue = state.answers.motivation != null) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Motivation.entries.forEach { motivation ->
                OptionCard(
                    motivation.title,
                    selected = state.answers.motivation == motivation,
                    onClick = { store.updateAnswers { it.copy(motivation = motivation) } },
                ) { VignetteImage(motivation.vignette) }
            }
        }
    }
}
