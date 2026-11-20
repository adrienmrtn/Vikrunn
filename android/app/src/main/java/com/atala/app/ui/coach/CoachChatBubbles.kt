package com.atala.app.ui.coach

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.L
import com.atala.app.core.model.ChatMessage
import com.atala.app.core.model.ChatRole
import com.atala.app.core.model.Coach
import com.atala.app.core.model.CoachPose
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.CoachPoseImage
import com.atala.app.ui.theme.Theme

private val userShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp, topEnd = 20.dp)
private val coachShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp, topEnd = 20.dp)
private val bubbleText = Theme.font(15).copy(lineHeight = 21.sp)

/** One message of the conversation: user bubble at the end, coach bubble (and its proposal) at the start. */
@Composable
internal fun CoachChatMessageRow(message: ChatMessage, coach: Coach, interactive: Boolean = true) {
    if (message.role == ChatRole.User) {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
            Text(
                message.text,
                style = bubbleText,
                color = Color.White,
                modifier = Modifier
                    .background(Theme.ink, userShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(end = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                CoachAvatar(coach, size = 30.dp)
                Text(
                    message.text,
                    style = bubbleText,
                    color = Theme.ink,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .background(Theme.bubble, coachShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            val proposal = message.proposal
            if (proposal != null && interactive) CoachChatProposalCard(message.id, proposal)
        }
    }
}

/** Three pulsing dots next to the coach's avatar while the answer is on its way. */
@Composable
internal fun CoachChatTypingRow(coach: Coach) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = L("%@ is writing", coach.name) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        CoachAvatar(coach, size = 30.dp)
        Row(
            Modifier
                .height(44.dp)
                .background(Theme.bubble, coachShape)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    0.3f, 1f,
                    infiniteRepeatable(tween(600), RepeatMode.Reverse, StartOffset(index * 200)),
                    label = "dot$index",
                )
                Box(Modifier.size(7.dp).alpha(alpha).background(Theme.muted, CircleShape))
            }
        }
    }
}

/** First row of the conversation, under the title: avatar with the online dot and a caption. */
@Composable
internal fun CoachChatHeader(coach: Coach, runCount: Int) {
    val subtitle = when {
        runCount == 1 -> L("Your coach · knows your %@ run", runCount)
        runCount > 1 -> L("Your coach · knows your %@ runs", runCount)
        else -> L("Your coach")
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CoachAvatar(coach, size = 44.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(13.dp)
                    .background(Color.White, CircleShape)
                    .padding(2.5.dp)
                    .background(Theme.greenSoft, CircleShape)
                    .clearAndSetSemantics { contentDescription = L("Online") },
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(coach.trait, style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Theme.font(13), color = Theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Without Atala Pro and with no conversation yet: a short example of an exchange, seen blurred
 * behind the padlock card. Never sent anywhere, never saved.
 */
@Composable
internal fun CoachChatSample(coach: Coach) {
    Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CoachChatMessageRow(ChatMessage(role = ChatRole.User, text = L("My legs feel heavy today. Should I still run?")), coach, interactive = false)
        CoachChatMessageRow(ChatMessage(role = ChatRole.Coach, text = L("Thanks for telling me. Swap today for an easy 20 minutes, and we move your intervals to Thursday. Does that work for you?")), coach, interactive = false)
        CoachChatMessageRow(ChatMessage(role = ChatRole.User, text = L("Perfect. And what pace for my long run on Sunday?")), coach, interactive = false)
        CoachChatMessageRow(ChatMessage(role = ChatRole.Coach, text = L("Slow enough to talk the whole way. I will tell you when to hold back and when to push.")), coach, interactive = false)
    }
}

/** Shown before the first message: a short invitation and suggested messages, sent as soon as they are tapped. */
@Composable
internal fun CoachChatEmptyState(coach: Coach, prompts: List<String>, onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L("Ask %@ anything", coach.name), style = Theme.font(20, FontWeight.Bold), color = Theme.ink)
                Text(
                    L("Your plan, a session, your paces, a busy week or tired legs. Write whenever you need."),
                    style = Theme.font(14).copy(lineHeight = 20.sp),
                    color = Theme.muted,
                )
            }
            CoachPoseImage(coach, Modifier.size(width = 84.dp, height = 132.dp).clearAndSetSemantics {}, pose = CoachPose.Present)
        }
        Row(
            Modifier
                .padding(top = 2.dp)
                .fillMaxWidth()
                .background(Theme.greenTint, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.EditCalendar, null, tint = Theme.green, modifier = Modifier.size(16.dp))
            Text(
                L("%@ can suggest a change to your plan. Nothing moves until you tap Apply.", coach.name),
                style = Theme.font(13, FontWeight.Medium).copy(lineHeight = 18.sp),
                color = Theme.green,
            )
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            prompts.forEach { prompt ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Theme.surface)
                        .clickable { onPick(prompt) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(prompt, style = Theme.font(14, FontWeight.Medium), color = Theme.ink, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.NorthEast, null, tint = Theme.muted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Suggested messages kept within reach above the input field. Tapping one sends it. */
@Composable
internal fun CoachChatQuickPrompts(prompts: List<String>, onPick: (String) -> Unit) {
    LazyRow(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Theme.screenPadding),
    ) {
        items(prompts) { prompt ->
            Box(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(Theme.surface)
                    .clickable { onPick(prompt) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(prompt, style = Theme.font(13, FontWeight.Medium), color = Theme.ink, maxLines = 1)
            }
        }
    }
}
