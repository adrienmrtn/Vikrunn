package com.atala.app.ui.coach

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.coach.CoachPlanText
import com.atala.app.core.coach.CoachService
import com.atala.app.core.coach.coachApply
import com.atala.app.core.l10n.L
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanChange
import com.atala.app.core.model.PlanProposal
import com.atala.app.core.model.ProposalStatus
import com.atala.app.core.store.AppState
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import java.util.UUID

/** One change of a proposal, ready to display. */
private class ChangeLine(
    val id: UUID,
    val beforeDay: String,
    val beforeTitle: String,
    val afterDay: String,
    val afterTitle: String,
    val removed: Boolean,
    /** Once applied: what changed, in a few words ("Moved from Tue 17 Nov"). */
    val note: String,
)

private val dayColor = Color(0xFF5A5A5A)

/**
 * "Plan change" card under a coach message: each change before → after, then Apply / Keep my plan.
 * Once answered, the card stays in the conversation with a clear "Applied" or "Plan kept" state.
 */
@Composable
internal fun CoachChatProposalCard(messageId: UUID, proposal: PlanProposal) {
    val store = LocalAppStore.current
    val state by collectAppState()
    val haptics = rememberHaptics()
    val lines = proposal.changes.mapNotNull {
        if (proposal.status == ProposalStatus.Applied) appliedLine(it, state) else proposedLine(it, state)
    }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = Theme.forest.copy(alpha = 0.06f), spotColor = Theme.forest.copy(alpha = 0.06f))
            .background(Color.White, shape)
            .border(1.dp, if (proposal.status == ProposalStatus.Applied) Theme.green.copy(alpha = 0.35f) else Theme.hairline, shape)
            .animateContentSize(tween(250))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Plan change"), style = Theme.font(12, FontWeight.SemiBold), color = Theme.muted, modifier = Modifier.weight(1f))
            when (proposal.status) {
                ProposalStatus.Pending -> Unit
                ProposalStatus.Applied -> Pill(L("Applied"), Icons.Filled.Check, Theme.green, Theme.greenTint)
                ProposalStatus.Declined -> Pill(L("Plan kept"), Icons.Filled.Remove, Theme.muted, Theme.surface)
            }
        }
        lines.forEach { line ->
            if (proposal.status == ProposalStatus.Applied) {
                AppliedRow(line)
            } else {
                ComparisonRow(line, Modifier.alpha(if (proposal.status == ProposalStatus.Declined) 0.55f else 1f))
            }
        }
        when (proposal.status) {
            ProposalStatus.Pending -> if (lines.isEmpty()) {
                Text(L("This suggestion has expired: your plan has changed since."), style = Theme.font(13), color = Theme.muted)
            } else {
                if (proposal.summary.isNotEmpty()) {
                    Text(proposal.summary, style = Theme.font(12).copy(lineHeight = 16.sp), color = Theme.muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapsuleAction(L("Apply"), filled = true, Modifier.weight(1f)) {
                        store.coachApply(messageId)
                        haptics.play(HapticKind.Success)
                        CoachService.report(messageId, applied = true, store = store)
                    }
                    CapsuleAction(L("Keep my plan"), filled = false, Modifier.weight(1f)) {
                        haptics.tap()
                        store.declineProposal(messageId)
                        CoachService.report(messageId, applied = false, store = store)
                    }
                }
            }
            ProposalStatus.Applied ->
                StateLabel(L("Applied. Your plan is up to date in Plan and Today."), Icons.Filled.CheckCircle, Theme.green)
            ProposalStatus.Declined ->
                StateLabel(L("Plan kept. Nothing was changed."), Icons.Outlined.RemoveCircleOutline, Theme.muted)
        }
    }
}

@Composable
private fun Pill(title: String, icon: ImageVector, tint: Color, background: Color) {
    Row(
        Modifier.background(background, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(10.dp))
        Text(title, style = Theme.font(12, FontWeight.SemiBold), color = tint)
    }
}

@Composable
private fun StateLabel(text: String, icon: ImageVector, color: Color) {
    Row(
        Modifier.heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = Theme.font(13, FontWeight.SemiBold), color = color)
    }
}

@Composable
private fun ComparisonRow(line: ChangeLine, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SessionBox(line.beforeDay, line.beforeTitle, Theme.greenSoft, Theme.bubble, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, L("becomes"), tint = Theme.ink, modifier = Modifier.size(16.dp))
        SessionBox(line.afterDay, line.afterTitle, Theme.orange, Theme.orangeTint, Modifier.weight(1f))
    }
}

@Composable
private fun SessionBox(day: String, title: String, dot: Color, background: Color, modifier: Modifier) {
    Column(
        modifier.background(background, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(dot, CircleShape))
            Text(day, style = Theme.font(11), color = dayColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(title, style = Theme.font(14, FontWeight.SemiBold), color = Theme.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The session as it is now in the plan, with what changed underneath. */
@Composable
private fun AppliedRow(line: ChangeLine) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.greenTint, RoundedCornerShape(14.dp))
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (line.afterDay.isNotEmpty()) Text(line.afterDay, style = Theme.font(11), color = dayColor, maxLines = 1)
        Text(
            line.afterTitle,
            style = Theme.font(14, FontWeight.SemiBold).copy(textDecoration = if (line.removed) TextDecoration.LineThrough else null),
            color = Theme.ink,
            maxLines = 2,
        )
        Text(line.note, style = Theme.font(12, FontWeight.Medium), color = Theme.green)
    }
}

@Composable
private fun CapsuleAction(title: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (filled) Theme.ink else Color.White, CircleShape)
            .border(1.dp, Theme.ink, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = Theme.font(14, FontWeight.SemiBold), color = if (filled) Color.White else Theme.ink)
    }
}

/** Before → after, while the change is only proposed (or was declined). */
private fun proposedLine(change: PlanChange, state: AppState): ChangeLine? {
    // A session that no longer exists (plan rebuilt since) has nothing to show.
    val workout = state.workout(change.workoutId) ?: return null
    val before = CoachPlanText.detail(workout)
    val beforeDay = CoachPlanText.day(workout.date)
    if (change.remove) {
        return ChangeLine(change.id, beforeDay, before, beforeDay, L("Removed"), removed = false, note = "")
    }
    val after = CoachPlanText.detail(
        change.newTitle ?: workout.title,
        change.newKind ?: workout.kind,
        change.newDistanceKm ?: workout.distanceKm,
        change.newDurationMin ?: workout.durationMin,
    )
    return ChangeLine(change.id, beforeDay, before, CoachPlanText.day(change.newDate ?: workout.date), after, removed = false, note = "")
}

/** Once applied, the session already holds the new values: show them, and what they replaced. */
private fun appliedLine(change: PlanChange, state: AppState): ChangeLine? {
    val previousDay = change.previousDate?.let(CoachPlanText::day).orEmpty()
    if (change.remove) {
        return ChangeLine(change.id, previousDay, "", previousDay, change.previousDetail ?: L("Session"),
            removed = true, note = L("Removed from your plan"))
    }
    val workout = state.workout(change.workoutId) ?: return null
    val now = CoachPlanText.detail(workout)
    val notes = mutableListOf<String>()
    val previousDate = change.previousDate
    if (previousDate != null && !PlanCalendar.isSameDay(previousDate, workout.date)) notes.add(L("Moved from %@", previousDay))
    val detail = change.previousDetail
    if (detail != null && detail != now) notes.add(L("Was %@", detail))
    return ChangeLine(change.id, previousDay, detail.orEmpty(), CoachPlanText.day(workout.date), now,
        removed = false, note = if (notes.isEmpty()) L("Updated") else notes.joinToString(" · "))
}
