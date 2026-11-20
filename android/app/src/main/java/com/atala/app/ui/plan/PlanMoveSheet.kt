package com.atala.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.Workout
import com.atala.app.core.store.PlanMoveSlot
import com.atala.app.core.store.planMove
import com.atala.app.core.store.planMoveDays
import com.atala.app.core.store.planMoveIsTight
import com.atala.app.core.store.planMoveSlot
import com.atala.app.core.store.planOthers
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/** "Today", "Tomorrow" or "Thu 19 Nov" (`CoachChatPlanText.day`). */
internal fun planDayText(date: Instant): String = when {
    PlanCalendar.isToday(date) -> L("Today")
    PlanCalendar.isSameDay(date, PlanCalendar.addDays(Instant.now(), 1)) -> L("Tomorrow")
    else -> Fmt.day(date, "EEE d MMM")
}

/** Sheet listing the coming days: tap one to move the session there (`PlanMoveSheet`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanMoveSheet(workoutId: UUID, onDismiss: () -> Unit) {
    val state by collectAppState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    fun close() {
        scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color.White) {
        Column {
            // Inline navigation bar: Cancel, and the title in the middle.
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    L("Cancel"),
                    style = Theme.font(17),
                    color = Theme.ink,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            haptics.tap()
                            close()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                Text(L("Move this session"), style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink, textAlign = TextAlign.Center)
            }
            val workout = state.workout(workoutId)
            if (workout != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${workout.title} · ${planDayText(workout.date)}", style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                    Text(
                        L("Pick a day. If a session is already there, the two trade places."),
                        style = Theme.font(14), color = Theme.muted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    state.planMoveDays().forEach { day ->
                        MoveDayRow(day, workout, onMoved = {
                            haptics.play(HapticKind.Success)
                            close()
                        }, onRefused = { haptics.play(HapticKind.Warning) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveDayRow(day: Instant, workout: Workout, onMoved: () -> Unit, onRefused: () -> Unit) {
    val store = LocalAppStore.current
    val state by collectAppState()
    val slot = state.planMoveSlot(workout, day)
    val others = state.planOthers(day, workout)
    val tight = state.planMoveIsTight(workout, day)
    val enabled = slot is PlanMoveSlot.Free || slot is PlanMoveSlot.Swap
    val current = slot is PlanMoveSlot.Current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .background(Color.White, shape)
            .border(if (current) 1.5.dp else 1.dp, if (current) Theme.ink else Theme.controlBorder, shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button) {
                if (store.planMove(workout.id, day)) onMoved() else onRefused()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(planDayText(day), style = Theme.font(16, FontWeight.SemiBold), color = if (enabled) Theme.ink else Theme.faint)
            Text(
                if (others.isEmpty()) L("Rest day") else others.joinToString(", ") { it.title },
                style = Theme.font(13), color = Theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (tight && enabled) {
                Text(L("Hard session the day before or after"), style = Theme.font(12, FontWeight.SemiBold), color = Theme.orangeDeep)
            }
        }
        SlotBadge(slot)
    }
}

@Composable
private fun SlotBadge(slot: PlanMoveSlot) {
    val (title, tint, background) = when (slot) {
        PlanMoveSlot.Current -> Triple(L("Now"), Color.White, Theme.ink)
        PlanMoveSlot.Free -> Triple(L("Move here"), Theme.green, Theme.greenTint)
        is PlanMoveSlot.Swap -> Triple(L("Swap"), Theme.orangeDeep, Theme.orangeTint)
        is PlanMoveSlot.Blocked -> Triple(slot.reason, Theme.muted, Theme.surface)
    }
    Text(
        title,
        style = Theme.font(12, FontWeight.SemiBold),
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .background(background, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
