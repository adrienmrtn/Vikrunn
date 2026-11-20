package com.atala.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atala.app.core.l10n.L
import com.atala.app.core.sync.SyncActivity
import com.atala.app.core.sync.SyncService
import com.atala.app.core.sync.SyncSignInOutcome
import com.atala.app.core.sync.resolveChoice
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.launch

/**
 * App-wide account states (AuthSyncPrompts in AuthRestoreOverlay.swift): the "Restoring your plan…"
 * overlay while the account is read after sign-in, and the choice between the phone's plan and the
 * account's when both exist. Placed once, above everything, by the root of the interface.
 */
@Composable
fun AuthSyncPrompts() {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val activity by SyncService.activity.collectAsStateWithLifecycle()
    val choicePending by SyncService.choicePending.collectAsStateWithLifecycle()
    var resolving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun resolve(keepPhone: Boolean) {
        if (resolving) return
        resolving = true
        scope.launch {
            val outcome = SyncService.resolveChoice(keepPhone, store)
            resolving = false
            if (outcome is SyncSignInOutcome.Failed) {
                haptics.play(HapticKind.Error)
                errorMessage = outcome.failure.userMessage
            }
        }
    }

    AnimatedVisibility(
        visible = activity == SyncActivity.Restoring || resolving,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        AuthRestoreOverlay()
    }

    if (choicePending && !resolving && errorMessage == null) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Color.White,
            title = { Text(L("Which plan do you want to keep?"), style = Theme.font(18, FontWeight.SemiBold), color = Theme.ink) },
            text = {
                Text(
                    L("Your account already has a plan. The one you keep replaces the other. Your runs from both are kept."),
                    style = Theme.font(15), color = Theme.body,
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { resolve(keepPhone = false) }) {
                        Text(L("Use my saved plan"), color = Theme.green, style = Theme.font(15, FontWeight.SemiBold))
                    }
                    TextButton(onClick = { resolve(keepPhone = true) }) {
                        Text(L("Use this phone's plan"), color = Theme.green, style = Theme.font(15, FontWeight.SemiBold))
                    }
                }
            },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            containerColor = Color.White,
            title = { Text(L("We couldn't finish signing you in"), style = Theme.font(18, FontWeight.SemiBold), color = Theme.ink) },
            text = { Text(message, style = Theme.font(15), color = Theme.body) },
            confirmButton = {
                // The question stays pending: closing the error shows it again.
                TextButton(onClick = { errorMessage = null }) {
                    Text(L("Try again"), color = Theme.green, style = Theme.font(15, FontWeight.SemiBold))
                }
            },
            dismissButton = {
                // Without a connection this could loop: the question comes back at the next launch.
                TextButton(onClick = {
                    errorMessage = null
                    SyncService.postponeChoice()
                }) {
                    Text(L("Not now"), color = Theme.ink, style = Theme.font(15))
                }
            },
        )
    }
}

/** Small "Restoring your plan…" state shown over the whole app while the account is read. */
@Composable
private fun AuthRestoreOverlay() {
    val label = L("Restoring your plan…")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.92f))
            // Swallows the touches: nothing under it can be used meanwhile.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = Theme.green, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            Text(label, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
            Text(
                L("Looking for what you saved in your account."),
                style = Theme.font(14).copy(textAlign = TextAlign.Center), color = Theme.muted,
            )
        }
    }
}
