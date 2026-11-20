package com.atala.app.ui.coach

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.coach.CoachLocalProposals
import com.atala.app.core.coach.CoachService
import com.atala.app.core.coach.CoachWords
import com.atala.app.core.l10n.L
import com.atala.app.core.model.CoachTone
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.main.Navigation
import com.atala.app.ui.paywall.ProLocked
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme

/**
 * Coach tab, the chat with the coach (Atala/Features/Coach/CoachChatView.swift). The coach only
 * answers, never writes first. Talking to the coach is part of Atala Pro: without it the
 * conversation is shown blurred, behind the padlock card that opens the paywall.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(nav: Navigation) {
    val store = LocalAppStore.current
    val state by collectAppState()
    val isPro by PurchaseService.isPro.collectAsStateWithLifecycle()
    val isSending by CoachService.isSending.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    remember(context) { CoachWords.init(context.assets) }

    var draft by rememberSaveable { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.isImeVisible
    // Suggestions built from the current plan: the first ones lead to a plan change the user can apply.
    val prompts = remember(state.plan, state.answers.coachId) { CoachLocalProposals.suggestedPrompts(state) }

    LaunchedEffect(isPro) {
        // Signed in with an empty conversation: bring back the one saved in the account.
        if (isPro && store.current.messages.isEmpty() && !CoachService.isSending.value) CoachService.restoreHistory(store)
    }
    // The coach's reply has arrived.
    var wasSending by remember { mutableStateOf(isSending) }
    LaunchedEffect(isSending) {
        if (wasSending && !isSending) haptics.play(HapticKind.Soft)
        wasSending = isSending
    }
    var scrolledOnce by remember { mutableStateOf(false) }
    LaunchedEffect(state.messages.size, isSending, inputFocused && imeVisible) {
        // Header, banner, the invitation or the messages, the typing dots, the bottom marker.
        val count = state.messages.size
        val last = 2 + (if (count == 0) 1 else count) + (if (isSending) 1 else 0)
        if (scrolledOnce) listState.animateScrollToItem(last) else listState.scrollToItem(last)
        scrolledOnce = true
    }

    fun send(text: String) {
        // Talking to the coach is part of Atala Pro (the screen is locked without it).
        if (!isPro || text.isBlank() || isSending) return
        haptics.tap()
        draft = ""
        CoachService.post(text, store)
    }

    Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Theme.screenPadding, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.coach.name,
                style = Theme.title(34),
                color = Theme.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (isPro) {
                ToneMenu(state.answers.tone) { tone -> store.updateAnswers { it.copy(tone = tone) } }
            }
        }
        ProLocked(
            icon = Icons.Filled.Forum,
            title = L("Unlock your coach"),
            message = L("Ask %@ anything, any time: your plan, a session, tired legs or a busy week. The answers are made for you.", state.coach.name),
            onUnlock = nav.onOpenPaywall,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(start = Theme.screenPadding, end = Theme.screenPadding, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "header") { CoachChatHeader(state.coach, state.runs.size) }
                    item(key = "banner") { Banner() }
                    if (state.messages.isEmpty()) {
                        item(key = "empty") {
                            if (isPro) CoachChatEmptyState(state.coach, prompts, ::send) else CoachChatSample(state.coach)
                        }
                    }
                    items(state.messages, key = { it.id }) { message -> CoachChatMessageRow(message, state.coach) }
                    if (isSending) item(key = "typing") { CoachChatTypingRow(state.coach) }
                    item(key = "bottom") { Spacer(Modifier.height(1.dp)) }
                }
                // Quick suggestions (once the conversation has started) above the input field.
                if (state.messages.isNotEmpty() && !isSending && draft.isEmpty()) {
                    CoachChatQuickPrompts(prompts, ::send)
                }
                InputBar(
                    draft = draft,
                    onDraft = { draft = it },
                    canSend = !isSending && draft.isNotBlank(),
                    onFocus = { inputFocused = it },
                    onSend = { send(draft) },
                )
            }
        }
    }
}

@Composable
private fun Banner() {
    val id = AtalaImages["illu_coach"] ?: return
    Image(
        painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(24.dp)),
    )
}

@Composable
private fun InputBar(draft: String, onDraft: (String) -> Unit, canSend: Boolean, onFocus: (Boolean) -> Unit, onSend: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { onDraft(it.take(2000)) },
            textStyle = Theme.font(15).copy(color = Theme.ink),
            cursorBrush = SolidColor(Theme.ink),
            maxLines = 4,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .onFocusChanged { onFocus(it.isFocused) }
                .semantics { contentDescription = L("Message to your coach") },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .background(Color.White, shape)
                        .border(1.dp, Color(0xFFCFCFCF), shape)
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (draft.isEmpty()) Text(L("Write to your coach"), style = Theme.font(15), color = Theme.faint)
                    inner()
                }
            },
        )
        Box(
            Modifier
                .size(48.dp)
                .alpha(if (canSend) 1f else 0.4f)
                .clip(CircleShape)
                .background(Theme.green, CircleShape)
                .clickable(enabled = canSend, role = Role.Button, onClick = onSend)
                .clearAndSetSemantics { contentDescription = L("Send") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/** Tone picker at the top of the conversation. */
@Composable
private fun ToneMenu(tone: CoachTone, onPick: (CoachTone) -> Unit) {
    val haptics = rememberHaptics()
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            L("Tone: %@", tone.title),
            style = Theme.font(15, FontWeight.Medium),
            color = Theme.ink,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .semantics { contentDescription = L("Coach tone: %@", tone.title) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color.White) {
            CoachTone.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title, style = Theme.font(16), color = Theme.ink) },
                    leadingIcon = {
                        if (option == tone) Icon(Icons.Filled.Check, null, tint = Theme.ink, modifier = Modifier.size(18.dp))
                        else Spacer(Modifier.size(18.dp))
                    },
                    onClick = {
                        if (option != tone) haptics.select()
                        onPick(option)
                        open = false
                    },
                )
            }
        }
    }
}
