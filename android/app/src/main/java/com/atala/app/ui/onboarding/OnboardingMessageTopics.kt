package com.atala.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import kotlin.math.max
import kotlin.math.sin

/** One ready-made question the user can ask in the onboarding conversation, with the coach's answer. */
data class OnboardingChatTopic(
    val id: Int,
    val question: String,
    /** The coach's bubbles, in order. */
    val answers: List<String>,
    /** The answer ends with the "Plan change" card (Apply / Keep my plan). */
    val opensPlanChange: Boolean,
) {
    companion object {
        val opening: String get() = L("Ask me anything, any time. Try one of the questions below.")
        val proposalLine: String get() = L("No problem. Here is what I suggest:")
        val closingLine: String get() = L("That's how it works. You always have the last word.")

        /**
         * The questions offered, in the order of the choices. The last one is the live example: its
         * wording follows the days the user ticked ("I can't run this Thursday").
         */
        fun all(example: OnboardingPlanChangeExample): List<OnboardingChatTopic> = listOf(
            OnboardingChatTopic(
                0, L("How should I warm up today?"),
                listOf(
                    L("Five minutes of easy jogging, then leg swings, high knees and a few relaxed strides."),
                    L("Before a hard session, add two short accelerations so your first rep doesn't come as a shock."),
                ),
                opensPlanChange = false,
            ),
            OnboardingChatTopic(
                1, L("What if I miss a session?"),
                listOf(L("Nothing breaks. It's simply marked as missed, and the rest of your plan stays as it is.")),
                opensPlanChange = false,
            ),
            OnboardingChatTopic(2, example.userLine, listOf(proposalLine), opensPlanChange = true),
        )
    }
}

/** One item of the conversation, in the order it was added. */
data class OnboardingThreadEntry(
    val id: Int,
    val kind: Kind,
    val text: String,
    /** The avatar is shown on the last bubble of a group of coach bubbles. */
    val showsAvatar: Boolean,
) {
    enum class Kind { User, Coach, Card }
}

/** Grey of the coach's bubbles (iMessage-like). */
internal val MessageBubbleGrey = Color(0xFFE9E9EB)

/** Three breathing dots in a coach bubble, shown while the coach "types". */
@Composable
fun OnboardingTypingBubble(modifier: Modifier = Modifier) {
    val reduceMotion = rememberProjectionReduceMotion()
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) withFrameNanos { time = (it / 1_000_000L % 1_000_000L) / 1000f }
    }
    val label = L("Typing")
    Row(
        modifier
            .height(38.dp)
            .background(MessageBubbleGrey, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp)
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { dot ->
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = 0.3f + 0.6f * max(0f, sin(time * 5.5f - dot * 0.9f)) }
                    .background(Theme.muted, CircleShape),
            )
        }
    }
}

/** The questions still to ask, as tappable chips above the bottom button. Tapping one sends it. */
@Composable
fun OnboardingQuestionChips(
    topics: List<OnboardingChatTopic>,
    /** False while the coach is answering: the chips wait. */
    enabled: Boolean,
    onAsk: (OnboardingChatTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, tween(200, easing = ProjectionEaseOut), label = "chips")
    val hint = L("Sends this question to your coach")
    Column(modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(thickness = 1.dp, color = Theme.hairline)
        Column(
            Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 10.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(L("Tap a question to ask it"), style = Theme.font(12, FontWeight.SemiBold), color = Theme.muted)
            topics.forEach { topic ->
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .background(Theme.greenTint, CircleShape)
                        .border(1.dp, Theme.green.copy(alpha = 0.3f), CircleShape)
                        .clickable(enabled = enabled, onClickLabel = hint, role = Role.Button, interactionSource = null, indication = null) {
                            haptics.tap()
                            onAsk(topic)
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        topic.question,
                        style = Theme.font(15, FontWeight.SemiBold),
                        color = Theme.green,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
