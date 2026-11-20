package com.atala.app.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Feeling
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.RunSource
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

/**
 * A run done without the app, typed in (ManualRunEntryView.swift): when, how far, how long. The
 * pace is worked out live, and anything that stops the run from being saved is explained in words.
 * [workoutId] is the plan session it stands for, null from the "+" of the tab bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRunEntryScreen(workoutId: UUID?, nav: Navigation) {
    val store = LocalAppStore.current
    val state by collectAppState()
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    val workout = remember(workoutId) { workoutId?.let { store.current.workout(it) } }

    val initialMinutes = workout?.let { maxOf(1, it.durationMin) } ?: 30
    var dateMillis by rememberSaveable {
        // A past session is prefilled on its own day, at midday.
        val now = Instant.now()
        val initial = workout?.let { PlanCalendar.startOfDay(it.date).plusSeconds(12 * 3600) }?.let { if (it.isAfter(now)) now else it } ?: now
        mutableStateOf(initial.toEpochMilli())
    }
    var distanceText by rememberSaveable {
        mutableStateOf(workout?.takeIf { it.distanceKm > 0 }?.let { String.format(Locale.ROOT, "%.1f", it.distanceKm) } ?: "")
    }
    var hours by rememberSaveable { mutableIntStateOf(initialMinutes / 60) }
    var minutes by rememberSaveable { mutableIntStateOf(initialMinutes % 60) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var feeling by rememberSaveable { mutableStateOf<Feeling?>(null) }
    var distanceFocused by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val distanceFocus = remember { FocusRequester() }

    val date = Instant.ofEpochMilli(dateMillis)
    val distanceKm = distanceText.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
    val durationSec = hours * 3600 + minutes * 60 + seconds
    val pace = if (distanceKm > 0 && durationSec > 0) (durationSec / distanceKm).toInt() else 0
    // Faster than 2:00 per km or slower than 30:00 per km is a typing mistake, not a run.
    val isValid = distanceKm in 0.1..100.0 && durationSec >= 60 && pace in 120..1800 && !date.isAfter(Instant.now())
    val problem: String? = when {
        distanceKm <= 0 || durationSec <= 0 -> null
        distanceKm < 0.1 -> L("The distance is too short to be saved.")
        distanceKm > 100 -> L("That is more than 100 km. Check the distance.")
        durationSec < 60 -> L("The duration must be at least one minute.")
        pace < 120 -> L("That is %@ per km, faster than anyone runs. Check the distance and the duration.", Fmt.pace(pace))
        pace > 1800 -> L("That is %@ per km. Check the distance and the duration.", Fmt.pace(pace))
        else -> null
    }
    val sessionToComplete = workoutId?.let { state.workout(it) }?.takeIf { it.status != WorkoutStatus.Done }

    fun save() {
        if (!isValid) return
        focus.clearFocus()
        // A session already done keeps its run: this one is saved as a plain run.
        val current = workoutId?.let { store.current.workout(it) }
        val linked = if (current != null && current.status == WorkoutStatus.Done) null else workoutId
        store.addRun(
            RunRecord(
                workoutId = linked, title = workout?.title ?: "Run", date = date, source = RunSource.Manual,
                distanceM = distanceKm * 1000, durationSec = durationSec, feeling = feeling,
            ),
        )
        haptics.play(HapticKind.Success)
        nav.onBack()
    }

    Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        // Navigation bar: Cancel, the title in the middle.
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            RunTextButton(L("Cancel"), size = 17, modifier = Modifier.align(Alignment.CenterStart)) { nav.onBack() }
            Text(L("Add a run"), style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink, modifier = Modifier.align(Alignment.Center))
            if (distanceFocused) {
                RunTextButton(L("Done"), size = 17, modifier = Modifier.align(Alignment.CenterEnd)) { focus.clearFocus() }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Theme.screenPadding)
                .padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Intro
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L("Ran on a treadmill? Enter the distance and time shown on the machine."), style = Theme.font(15), color = Theme.muted)
                if (sessionToComplete != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Theme.greenTint, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Theme.green, modifier = Modifier.size(18.dp))
                        Text(L("Counts as your session: %@", sessionToComplete.title), style = Theme.font(14, FontWeight.SemiBold), color = Theme.green)
                    }
                }
            }
            // Date
            Row(
                Modifier.fillMaxWidth().card().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Filled.CalendarMonth, size = 40.dp)
                Text(L("Date"), style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink, modifier = Modifier.weight(1f))
                val local = LocalDateTime.ofInstant(date, ZoneId.systemDefault())
                DateChip(Fmt.day(date, "d MMM yyyy")) { pickingDate = true }
                DateChip(local.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(com.atala.app.core.l10n.AppLanguage.locale))) {
                    pickingTime = true
                }
            }
            // Distance
            Row(
                Modifier
                    .fillMaxWidth()
                    .card(selected = distanceFocused)
                    .clickable(indication = null, interactionSource = null) { distanceFocus.requestFocus() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Filled.Straighten, size = 40.dp)
                Text(L("Distance"), style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
                Spacer(Modifier.weight(1f))
                val label = L("Distance in kilometres")
                Box(Modifier.widthIn(max = 120.dp), contentAlignment = Alignment.CenterEnd) {
                    if (distanceText.isEmpty()) {
                        Text("0.0", style = Theme.font(32, FontWeight.Bold).digits(), color = Theme.faint, textAlign = TextAlign.End)
                    }
                    BasicTextField(
                        value = distanceText,
                        onValueChange = { text -> distanceText = text.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                        textStyle = Theme.font(32, FontWeight.Bold).digits().copy(color = Theme.ink, textAlign = TextAlign.End),
                        singleLine = true,
                        cursorBrush = SolidColor(Theme.green),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        modifier = Modifier
                            .width(120.dp)
                            .focusRequester(distanceFocus)
                            .onFocusChanged { distanceFocused = it.isFocused }
                            .semantics { contentDescription = label },
                    )
                }
                Text("km", style = Theme.font(17, FontWeight.SemiBold), color = Theme.muted)
            }
            // Duration
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.Timer, size = 40.dp)
                    Text(L("Duration"), style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
                    Spacer(Modifier.weight(1f))
                    FitText(Fmt.duration(durationSec), Theme.font(32, FontWeight.Bold).digits(), Theme.ink, minScale = 0.6f)
                }
                Row(Modifier.fillMaxWidth().height(120.dp)) {
                    WheelPicker(0 until 10, hours, L("h"), Modifier.weight(1f)) { hours = it }
                    WheelPicker(0 until 60, minutes, L("min"), Modifier.weight(1f)) { minutes = it }
                    WheelPicker(0 until 60, seconds, L("s"), Modifier.weight(1f)) { seconds = it }
                }
            }
            // Pace, or what is wrong.
            when {
                problem != null -> Row(
                    Modifier.fillMaxWidth().background(Theme.orangeTint, RoundedCornerShape(18.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Theme.orangeDeep, modifier = Modifier.size(18.dp))
                    Text(problem, style = Theme.font(14, FontWeight.SemiBold), color = Theme.orangeDeep)
                }
                pace > 0 -> {
                    val description = L("That is %@ per km.", Fmt.pace(pace))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Theme.greenTint, RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics(mergeDescendants = true) { contentDescription = description },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(L("Average pace"), style = Theme.font(15, FontWeight.SemiBold), color = Theme.green, modifier = Modifier.weight(1f).padding(bottom = 6.dp))
                        Text(Fmt.pace(pace), style = Theme.font(32, FontWeight.Bold).digits(), color = Theme.green)
                        Text("/km", style = Theme.font(17, FontWeight.SemiBold), color = Theme.green, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                else -> Text(
                    L("Enter a distance and a duration to see your pace."),
                    style = Theme.font(14), color = Theme.muted,
                    modifier = Modifier.fillMaxWidth().background(Theme.surface, RoundedCornerShape(18.dp)).padding(14.dp),
                )
            }
            // Feeling
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(L("How was it?"), style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink, modifier = Modifier.weight(1f))
                    Text(L("Optional"), style = Theme.font(13), color = Theme.muted)
                }
                Feeling.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { option ->
                            FeelingChip(option, feeling == option, Modifier.weight(1f)) {
                                haptics.select()
                                feeling = if (feeling == option) null else option
                            }
                        }
                    }
                }
                Text(L("It helps your coach tune the next sessions."), style = Theme.font(13), color = Theme.muted)
            }
        }
        BottomBar {
            // Saving plays its own success haptic.
            PrimaryButton(L("Save run"), onClick = { save() }, enabled = isValid, haptic = null)
        }
    }

    if (pickingDate) {
        val today = PlanCalendar.startOfDay(Instant.now())
        val selectedUtc = LocalDateTime.ofInstant(date, ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedUtc,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !day.isAfter(today.atZone(ZoneId.systemDefault()).toLocalDate())
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = LocalDateTime.ofInstant(date, ZoneId.systemDefault()).toLocalTime()
                        val picked = day.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
                        dateMillis = minOf(picked.toEpochMilli(), System.currentTimeMillis())
                    }
                    pickingDate = false
                }) { Text(L("Done"), color = Theme.green) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(L("Cancel"), color = Theme.ink) } },
            colors = DatePickerDefaults.colors(containerColor = Color.White),
        ) {
            DatePicker(
                pickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    selectedDayContainerColor = Theme.green,
                    todayDateBorderColor = Theme.green,
                    todayContentColor = Theme.green,
                ),
            )
        }
    }

    if (pickingTime) {
        val local = LocalDateTime.ofInstant(date, ZoneId.systemDefault())
        val timeState = rememberTimePickerState(local.hour, local.minute)
        Dialog(onDismissRequest = { pickingTime = false }) {
            Column(
                Modifier.background(Color.White, RoundedCornerShape(24.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(
                    timeState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = Theme.green,
                        timeSelectorSelectedContainerColor = Theme.greenTint,
                        timeSelectorSelectedContentColor = Theme.green,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { pickingTime = false }) { Text(L("Cancel"), color = Theme.ink) }
                    TextButton(onClick = {
                        val picked = local.withHour(timeState.hour).withMinute(timeState.minute).atZone(ZoneId.systemDefault()).toInstant()
                        dateMillis = minOf(picked.toEpochMilli(), System.currentTimeMillis())
                        pickingTime = false
                    }) { Text(L("Done"), color = Theme.green) }
                }
            }
        }
    }
}

@Composable
private fun DateChip(text: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .background(Theme.surface, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Theme.font(15, FontWeight.SemiBold), color = Theme.green, maxLines = 1)
    }
}

@Composable
private fun FeelingChip(option: Feeling, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 56.dp)
            .card(selected = selected, radius = 16.dp)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeelingBadge(option, size = 34, iconSize = 16)
        FitText(option.title, Theme.font(14, FontWeight.SemiBold), Theme.ink, Modifier.weight(1f), minScale = 0.8f, maxLines = 2)
    }
}

/** A wheel of whole numbers ("12 min"), the selected one in the middle row. */
@Composable
private fun WheelPicker(range: IntRange, value: Int, unit: String, modifier: Modifier, onChange: (Int) -> Unit) {
    val itemHeight = 40.dp
    val values = range.toList()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(value).coerceAtLeast(0))
    val haptics = rememberHaptics()
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.firstVisibleItemIndex }
            .filter { !it.first }
            .distinctUntilChanged()
            .collect { (_, _) ->
                val index = if (list.firstVisibleItemScrollOffset > 0) list.firstVisibleItemIndex + 1 else list.firstVisibleItemIndex
                val picked = values.getOrNull(index.coerceIn(0, values.lastIndex)) ?: return@collect
                if (picked != value) {
                    haptics.select()
                    onChange(picked)
                }
            }
    }
    LaunchedEffect(value) {
        val index = values.indexOf(value)
        if (index >= 0 && !list.isScrollInProgress && list.firstVisibleItemIndex != index) list.scrollToItem(index)
    }
    Box(modifier.height(itemHeight * 3), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(itemHeight).background(Theme.surface, RoundedCornerShape(10.dp)))
        LazyColumn(
            state = list,
            flingBehavior = rememberSnapFlingBehavior(list),
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(values.size) { index ->
                val selected = values[index] == value
                Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "${values[index]} $unit",
                        style = Theme.font(if (selected) 20 else 17, if (selected) FontWeight.SemiBold else FontWeight.Normal).digits(),
                        color = if (selected) Theme.ink else Theme.faint,
                    )
                }
            }
        }
    }
}
