package com.atala.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.core.model.CoachTone
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.OnboardingAnswers
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.Weekday
import com.atala.app.core.plan.PlanEngine
import com.atala.app.core.store.AppState
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.onboarding.OnboardingTimeRules
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.theme.Theme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// Identity

@Composable
internal fun ProfileIdentitySection(state: AppState) {
    val store = LocalAppStore.current
    FormSection(header = L("You")) {
        FormRow {
            Text(L("First name"), style = Theme.font(17), color = Theme.ink)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                val name = state.answers.firstName
                if (name.isEmpty()) Text(L("Your first name"), style = Theme.font(17), color = Theme.faint)
                BasicTextField(
                    value = name,
                    onValueChange = { value -> store.updateAnswers { it.copy(firstName = value) } },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 17.sp, color = Theme.ink, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(Theme.green),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// Coach

@Composable
internal fun ProfileCoachSection(state: AppState) {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    FormSection(
        header = L("Your coach"),
        footer = L("All coaches follow the same method. Only the face and the name change."),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Coach.all.forEach { coach ->
                val selected = state.answers.coachId == coach.id
                Column(
                    Modifier
                        .weight(1f)
                        .semantics {
                            this.selected = selected
                            contentDescription = coach.name
                        }
                        .clickable(role = Role.RadioButton) {
                            if (!selected) haptics.select()
                            store.updateAnswers { it.copy(coachId = coach.id) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CoachAvatar(
                        coach, size = 56.dp,
                        modifier = Modifier
                            .border(2.5.dp, if (selected) Theme.green else Color.Transparent, CircleShape)
                            .padding(3.dp),
                    )
                    Text(
                        coach.name, style = Theme.font(13, if (selected) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (selected) Theme.ink else Theme.muted,
                    )
                }
            }
        }
        FormDivider()
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            SegmentedControl(
                options = CoachTone.entries.map { it.title },
                selected = CoachTone.entries.indexOf(state.answers.tone),
                onSelect = { index ->
                    haptics.select()
                    store.updateAnswers { it.copy(tone = CoachTone.entries[index]) }
                },
            )
        }
        FormDivider()
        Text(
            "“${state.answers.tone.example}”",
            style = Theme.font(14).copy(fontStyle = FontStyle.Italic), color = Theme.muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** The iPhone's segmented picker. */
@Composable
internal fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color(0xFFEEEEF0), RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        options.forEachIndexed { index, title ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { if (!isSelected) onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(title, style = Theme.font(13, if (isSelected) FontWeight.SemiBold else FontWeight.Medium), color = Theme.ink, maxLines = 1)
            }
        }
    }
}

// Goal

/** A row that opens a menu of choices (the iPhone's menu picker). */
@Composable
internal fun FormMenuRow(title: String, value: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    FormRow(onClick = { haptics.tap(); open = true }) {
        Text(title, style = Theme.font(17), color = Theme.ink)
        Spacer(Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, style = Theme.font(17), color = Theme.muted, maxLines = 1)
                Icon(Icons.Filled.UnfoldMore, null, tint = Theme.muted, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color.White) {
                options.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label, style = Theme.font(16), color = Theme.ink) },
                        onClick = {
                            open = false
                            haptics.select()
                            action()
                        },
                    )
                }
            }
        }
    }
}

private fun earliestRace(): Instant = PlanCalendar.addDays(PlanCalendar.startOfDay(Instant.now()), 1)

private fun defaultRace(): Instant = PlanCalendar.addDays(PlanCalendar.startOfDay(Instant.now()), 42)

/** Goal, race date and target time (edited on the draft). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileGoalSection(draft: OnboardingAnswers, onDraft: (OnboardingAnswers) -> Unit, onOpenTarget: () -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    FormSection(
        header = L("Goal"),
        footer = L("Plans cover up to %@ weeks. If your race is further away, the plan builds your base first.", PlanEngine.MAX_WEEKS),
    ) {
        val options = buildList<Pair<String, () -> Unit>> {
            GoalType.entries.forEach { goal ->
                add(goal.title to {
                    // A target time belongs to one distance: changing the goal clears it.
                    if (goal != draft.goal) onDraft(draft.copy(goal = goal, targetSeconds = null))
                })
            }
        }
        FormMenuRow(L("Goal"), draft.goal?.title ?: L("Choose"), options)
        val goal = draft.goal
        if (goal != null && !goal.isOpenEnded) {
            FormDivider()
            FormToggleRow(L("I have a race date"), draft.hasRace) { isOn ->
                onDraft(draft.copy(hasRace = isOn, raceDate = if (isOn && draft.raceDate == null) defaultRace() else draft.raceDate))
            }
            if (draft.hasRace) {
                FormDivider()
                val shown = maxOf(draft.raceDate ?: defaultRace(), earliestRace())
                FormRow(onClick = { pickDate = true }) {
                    Text(L("Race date"), style = Theme.font(17), color = Theme.ink)
                    Spacer(Modifier.weight(1f))
                    Text(
                        Fmt.day(shown, "d MMM yyyy"), style = Theme.font(17), color = Theme.ink,
                        modifier = Modifier
                            .background(Color(0xFFEEEEF0), RoundedCornerShape(7.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                if (pickDate) {
                    val zone = ZoneId.systemDefault()
                    val earliestDay = earliestRace().atZone(zone).toLocalDate()
                    val picker = rememberDatePickerState(
                        initialSelectedDateMillis = shown.atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                        selectableDates = object : SelectableDates {
                            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(earliestDay)
                        },
                    )
                    DatePickerDialog(
                        onDismissRequest = { pickDate = false },
                        confirmButton = {
                            TextButton(onClick = {
                                picker.selectedDateMillis?.let { millis ->
                                    val day: LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                    onDraft(draft.copy(raceDate = day.atStartOfDay(zone).toInstant()))
                                }
                                pickDate = false
                            }) { Text(L("Done"), color = Theme.green, style = Theme.font(15, FontWeight.SemiBold)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { pickDate = false }) { Text(L("Cancel"), color = Theme.ink) }
                        },
                        colors = DatePickerDefaults.colors(containerColor = Color.White),
                    ) {
                        DatePicker(
                            state = picker,
                            colors = DatePickerDefaults.colors(
                                containerColor = Color.White,
                                selectedDayContainerColor = Theme.green,
                                todayDateBorderColor = Theme.green,
                                todayContentColor = Theme.green,
                            ),
                        )
                    }
                }
            }
            FormDivider()
            FormValueRow(
                L("Target time"),
                value = draft.targetSeconds?.let { Fmt.goalTime(it) } ?: L("None"),
                chevron = true,
                onClick = onOpenTarget,
            )
        }
    }
}

// Training days

@Composable
internal fun ProfileDaysSection(draft: OnboardingAnswers, onDraft: (OnboardingAnswers) -> Unit) {
    val haptics = rememberHaptics()
    FormSection(header = L("Training days"), footer = L("One ticked day is one run per week.")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (day in 1..7) {
                val selected = day in draft.runDays
                val name = Weekday.long(day)
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Theme.green else Theme.surface)
                        .semantics {
                            this.selected = selected
                            contentDescription = name
                        }
                        .clickable(role = Role.Checkbox) {
                            haptics.select()
                            if (selected) {
                                onDraft(draft.copy(
                                    runDays = draft.runDays - day,
                                    longRunDay = if (draft.longRunDay == day) null else draft.longRunDay,
                                ))
                            } else {
                                onDraft(draft.copy(runDays = (draft.runDays + day).sorted()))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Weekday.short(day), style = Theme.font(12, FontWeight.SemiBold), color = if (selected) Color.White else Theme.ink)
                }
            }
        }
        FormDivider()
        val options = buildList<Pair<String, () -> Unit>> {
            add(L("Automatic") to { onDraft(draft.copy(longRunDay = null)) })
            draft.runDays.sorted().forEach { day -> add(Weekday.long(day) to { onDraft(draft.copy(longRunDay = day)) }) }
        }
        FormMenuRow(L("Long run day"), draft.longRunDay?.let { Weekday.long(it) } ?: L("Automatic"), options)
    }
}

// Paces

/** Training paces of the current plan (read-only). */
@Composable
internal fun ProfilePacesSection(state: AppState) {
    val plan = state.plan
    FormSection(
        header = L("Training paces"),
        footer = if (plan != null) L("Calculated by your coach from your level and your goal. They follow your plan.") else null,
    ) {
        if (plan == null) {
            FormNote(L("Your paces appear once your plan is built."), size = 17)
        } else {
            val rows = listOf(
                L("Easy") to plan.paces.easy,
                L("Long run") to plan.paces.long,
                L("Tempo") to plan.paces.tempo,
                L("Intervals") to plan.paces.interval,
                (if (plan.goal.isOpenEnded) L("Brisk") else L("Race pace")) to plan.paces.race,
            )
            rows.forEachIndexed { index, (title, pace) ->
                if (index > 0) FormDivider()
                FormValueRow(title, "${Fmt.pace(pace)}/km")
            }
        }
    }
}

// Target time editor

/** Target time editor: hours, minutes and seconds, or no target at all (a pushed page on the iPhone). */
@Composable
internal fun ProfileTargetTimeEditor(seconds: Int?, goal: GoalType, answers: OnboardingAnswers, onChange: (Int?) -> Unit) {
    val todaySeconds = PlanEngine.predictedSeconds(PlanEngine.estimateVma(answers), goal)
    val km = goal.distanceKm
    val defaultSeconds = if (km == null) 30 * 60 else {
        val base = todaySeconds ?: OnboardingTimeRules.defaultSeconds(km)
        OnboardingTimeRules.clamp(base / 60 * 60, km)
    }
    FormSection(footer = L("Without a target time, your paces are set from your current level.")) {
        FormToggleRow(L("Aim for a time"), seconds != null) { isOn -> onChange(if (isOn) seconds ?: defaultSeconds else null) }
    }
    val value = seconds ?: return
    FormSection(header = L("Target for your %@", goal.title), footer = levelNote(value, todaySeconds, goal)) {
        Row(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 8.dp)) {
            WheelPicker(0..3, value / 3600, L("h"), Modifier.weight(1f)) { h ->
                onChange(maxOf(60, h * 3600 + value % 3600))
            }
            WheelPicker(0..59, (value % 3600) / 60, L("min"), Modifier.weight(1f)) { m ->
                onChange(maxOf(60, (value / 3600) * 3600 + m * 60 + value % 60))
            }
            WheelPicker(0..59, value % 60, L("s"), Modifier.weight(1f)) { s ->
                onChange(maxOf(60, value - value % 60 + s))
            }
        }
        FormDivider()
        FormRow {
            Text(L("Target time"), style = Theme.font(17), color = Theme.ink)
            Spacer(Modifier.weight(1f))
            Text(Fmt.goalTime(value), style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink)
        }
    }
}

/** Gentle note under the wheels: how the target compares with today's level. */
private fun levelNote(target: Int, today: Int?, goal: GoalType): String {
    val km = goal.distanceKm
    if (today == null || km == null) return ""
    val bounds = OnboardingTimeRules.range(km)
    if (target < bounds.first || target > bounds.last) {
        return L("Outside what the plan can work with: it will be brought back between %@ and %@.", Fmt.goalTime(bounds.first), Fmt.goalTime(bounds.last))
    }
    val reachable = (today * 0.95).toInt()
    if (target < reachable) {
        return L("Ambitious from your level today (around %@). It stays your goal, and the plan trains you towards %@ first.", Fmt.goalTime(today), Fmt.goalTime(reachable))
    }
    if (target <= today) return L("A real step up from your level today (around %@), and within reach.", Fmt.goalTime(today))
    return L("Comfortable for your level today (around %@).", Fmt.goalTime(today))
}

/** A wheel of whole numbers (the iPhone's `.wheel` picker): five rows, the middle one chosen. */
@Composable
private fun WheelPicker(range: IntRange, value: Int, suffix: String, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val rowHeight = 28.dp
    val values = range.toList()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (value - range.first).coerceIn(0, values.lastIndex))
    val centre by remember { derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > 0 && state.layoutInfo.visibleItemsInfo.firstOrNull()?.let { state.firstVisibleItemScrollOffset > it.size / 2 } == true) 1 else 0 } }
    val haptics = rememberHaptics()
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val picked = values.getOrNull(centre) ?: return@LaunchedEffect
            if (picked != value) {
                haptics.select()
                onChange(picked)
            }
        }
    }
    LaunchedEffect(value) {
        val index = (value - range.first).coerceIn(0, values.lastIndex)
        if (!state.isScrollInProgress && centre != index) state.scrollToItem(index)
    }
    Box(modifier.height(rowHeight * 5), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(rowHeight).background(Color(0xFFEEEEF0), RoundedCornerShape(8.dp)))
        LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state), modifier = Modifier.fillMaxWidth()) {
            items(2) { Spacer(Modifier.height(rowHeight)) }
            items(values.size) { index ->
                val selected = index == centre
                Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.Center) {
                    Text(
                        "${values[index]} $suffix",
                        style = Theme.font(if (selected) 20 else 17, if (selected) FontWeight.Medium else FontWeight.Normal),
                        color = if (selected) Theme.ink else Theme.faint,
                    )
                }
            }
            items(2) { Spacer(Modifier.height(rowHeight)) }
        }
    }
}
