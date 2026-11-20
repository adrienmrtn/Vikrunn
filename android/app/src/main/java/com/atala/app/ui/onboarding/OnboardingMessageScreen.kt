package com.atala.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.CoachAvatar
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.components.rememberReduceMotion
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay
import java.time.Instant

/** SwiftUI's `spring(response: 0.4, dampingFraction: 0.78)`: messages pop in. */
private fun <T> messagePop() = spring<T>(dampingRatio = 0.78f, stiffness = 247f)

/** Seconds the coach "types": the longer the message, the longer it takes. */
private fun messageTypingMillis(text: String): Long = (minOf(1.6, 0.7 + text.length * 0.009) * 1000).toLong()

/**
 * Screen 20: how the user talks to the coach. The user asks: ready-made questions sit at the bottom
 * as chips, tapping one sends it, then the coach types and answers. One of them is a live example:
 * the user warns the coach, the coach proposes a change on a card, and the user applies it or keeps
 * the plan. The button to move on shows up after the coach's first answer.
 * A demo only: nothing is written to the store.
 */
@Composable
fun OnboardingMessageScreen() {
    val state by collectAppState()
    val nav = LocalOnboardingNavigator.current
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    val coach = state.coach
    val example = remember(state.answers.runDays, state.answers.longRunDay) { OnboardingPlanChangeExample.make(state.answers) }
    val topics = remember(example) { OnboardingChatTopic.all(example) }
    val timeLabel = remember { L("Today %@", Fmt.day(Instant.now(), "HH:mm")) }

    val entries = remember { mutableStateListOf<OnboardingThreadEntry>() }
    // Questions already sent: they leave the choices.
    var asked by remember { mutableStateOf(emptySet<Int>()) }
    // The question the coach is answering right now.
    var activeTopic by remember { mutableStateOf<Int?>(null) }
    var typing by remember { mutableStateOf(false) }
    var typingShowsAvatar by remember { mutableStateOf(true) }
    // The coach has said its opening line: the choices appear.
    var opened by remember { mutableStateOf(false) }
    // The coach has given at least one answer: the user may move on.
    var answered by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<OnboardingPlanChangeChoice?>(null) }
    var closingBusy by remember { mutableStateOf(false) }
    var closingShown by remember { mutableStateOf(false) }

    // The coach is typing an answer or its closing line: questions wait.
    val busy = activeTopic != null || closingBusy
    val remaining = topics.filter { it.id !in asked }

    fun append(kind: OnboardingThreadEntry.Kind, text: String, showsAvatar: Boolean = false) {
        entries += OnboardingThreadEntry(entries.size, kind, text, showsAvatar)
        if (kind == OnboardingThreadEntry.Kind.Coach) haptics.play(HapticKind.Soft)
    }

    // The user taps a choice: it becomes their bubble, and the coach starts answering.
    fun ask(topic: OnboardingChatTopic) {
        if (!opened || busy || topic.id in asked) return
        asked = asked + topic.id
        append(OnboardingThreadEntry.Kind.User, topic.question)
        activeTopic = topic.id
    }

    // Resolves the card. Nothing is saved: the plan is only built at the end of the questionnaire.
    fun choose(value: OnboardingPlanChangeChoice) {
        if (choice != null) return
        closingBusy = true
        choice = value
    }

    // The coach opens the conversation, then the choices appear.
    LaunchedEffect(Unit) {
        if (opened) return@LaunchedEffect
        if (reduceMotion) {
            append(OnboardingThreadEntry.Kind.Coach, OnboardingChatTopic.opening, showsAvatar = true)
            opened = true
            return@LaunchedEffect
        }
        delay(500)
        typingShowsAvatar = true
        typing = true
        delay(900)
        typing = false
        append(OnboardingThreadEntry.Kind.Coach, OnboardingChatTopic.opening, showsAvatar = true)
        delay(350)
        opened = true
    }

    // The coach types, then answers the question that was just asked.
    LaunchedEffect(activeTopic) {
        val id = activeTopic ?: return@LaunchedEffect
        val topic = topics.firstOrNull { it.id == id } ?: return@LaunchedEffect
        val count = topic.answers.size
        if (reduceMotion) {
            topic.answers.forEachIndexed { index, text ->
                append(OnboardingThreadEntry.Kind.Coach, text, showsAvatar = index == count - 1 && !topic.opensPlanChange)
            }
            if (topic.opensPlanChange) append(OnboardingThreadEntry.Kind.Card, "")
            answered = true
            activeTopic = null
            return@LaunchedEffect
        }
        topic.answers.forEachIndexed { index, text ->
            val isLast = index == count - 1
            val showsAvatar = isLast && !topic.opensPlanChange
            delay(450)
            typingShowsAvatar = showsAvatar
            typing = true
            delay(messageTypingMillis(text))
            typing = false
            append(OnboardingThreadEntry.Kind.Coach, text, showsAvatar)
            // The live example: the change to accept or not comes with the coach's line.
            if (isLast && topic.opensPlanChange) append(OnboardingThreadEntry.Kind.Card, "")
        }
        // From the first answer on, the user may move on or keep asking.
        if (!answered) {
            delay(400)
            answered = true
        }
        activeTopic = null
    }

    // After the choice: the coach's closing line.
    LaunchedEffect(choice) {
        if (choice == null || closingShown) return@LaunchedEffect
        if (reduceMotion) {
            append(OnboardingThreadEntry.Kind.Coach, OnboardingChatTopic.closingLine, showsAvatar = true)
            closingShown = true
            closingBusy = false
            return@LaunchedEffect
        }
        // Let the coach finish an answer it is still typing.
        while (activeTopic != null) delay(200)
        delay(600)
        typingShowsAvatar = true
        typing = true
        delay(1000)
        typing = false
        append(OnboardingThreadEntry.Kind.Coach, OnboardingChatTopic.closingLine, showsAvatar = true)
        closingShown = true
        closingBusy = false
    }

    // Keeps the newest message (and the card's buttons) on screen, once the layout has settled.
    val scroll = rememberScrollState()
    val scrollToken = "${entries.size}-$typing-${asked.size}-${choice != null}-$answered-$opened"
    LaunchedEffect(scrollToken) {
        delay(60)
        if (reduceMotion) scroll.scrollTo(scroll.maxValue) else scroll.animateScrollTo(scroll.maxValue, tween(300, easing = OnboardingEasing.easeOut))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .navigationBarsPadding(),
    ) {
        OnboardingHeaderSpace()
        MessageTitleHeader(coach, Modifier.padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 10.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 14.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(timeLabel, style = Theme.font(11), color = Theme.muted, modifier = Modifier.padding(bottom = 2.dp))
            entries.forEach { entry ->
                key(entry.id) {
                    when (entry.kind) {
                        OnboardingThreadEntry.Kind.User -> MessageUserBubble(entry.text, animate = !reduceMotion)
                        OnboardingThreadEntry.Kind.Coach -> MessageCoachEntrance(animate = !reduceMotion) {
                            MessageCoachBubble(coach, entry.text, entry.showsAvatar)
                        }
                        OnboardingThreadEntry.Kind.Card -> MessageCoachEntrance(animate = !reduceMotion) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                // Once answered, the avatar moves down to the coach's closing line.
                                if (choice != null) Spacer(Modifier.size(30.dp)) else CoachAvatar(coach, size = 30.dp)
                                OnboardingPlanChangeCard(example, choice, onChoose = ::choose, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (typing) {
                MessageCoachEntrance(animate = !reduceMotion) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        if (typingShowsAvatar) CoachAvatar(coach, size = 30.dp) else Spacer(Modifier.size(30.dp))
                        OnboardingTypingBubble()
                    }
                }
            }
        }
        AnimatedVisibility(opened && remaining.isNotEmpty(), enter = fadeIn(tween(300)), exit = fadeOut()) {
            OnboardingQuestionChips(remaining, enabled = !busy, onAsk = ::ask)
        }
        AnimatedVisibility(answered, enter = fadeIn(tween(300)), exit = fadeOut()) {
            BottomBar {
                PrimaryButton(L("Continue"), onClick = { nav.next() })
            }
        }
    }
}

@Composable
private fun MessageTitleHeader(coach: Coach, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoachAvatar(coach, size = 48.dp)
            Text(
                L("Here's how you talk to %@", coach.name),
                style = Theme.title(24),
                color = Theme.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(thickness = 1.dp, color = Theme.hairline)
    }
}

/** User messages pop in from the end side. */
@Composable
private fun MessageUserBubble(text: String, animate: Boolean) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, messagePop()) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .graphicsLayer {
                val shift = (1f - progress.value) * 120.dp.toPx()
                translationX = if (rtl) -shift else shift
                alpha = progress.value.coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text,
            style = Theme.font(16),
            color = Color.White,
            modifier = Modifier
                .widthIn(max = 270.dp)
                .background(Theme.green, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/** Coach messages grow from their tail (bottom, start side). */
@Composable
private fun MessageCoachEntrance(animate: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, messagePop()) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = 0.85f + 0.15f * progress.value
                scaleX = scale
                scaleY = scale
                alpha = progress.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 1f)
            },
    ) {
        content()
    }
}

@Composable
private fun MessageCoachBubble(coach: Coach, text: String, showsAvatar: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        if (showsAvatar) CoachAvatar(coach, size = 30.dp) else Spacer(Modifier.size(30.dp))
        Text(
            text,
            style = Theme.font(16),
            color = Theme.ink,
            modifier = Modifier
                .widthIn(max = 270.dp)
                .background(
                    MessageBubbleGrey,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = if (showsAvatar) 6.dp else 20.dp),
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}
