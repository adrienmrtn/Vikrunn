package com.atala.app.ui.profile

import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.atala.app.core.account.AtalaLinks
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.L
import com.atala.app.core.model.AppSettings
import com.atala.app.core.model.VoiceSettings
import com.atala.app.core.purchases.PurchaseService
import com.atala.app.core.reminders.RunReminders
import com.atala.app.core.store.AppState
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.paywall.ProChip
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.launch

// Voice

/**
 * Voice announcements during a run. The coach's voice is part of Atala Pro: without it the switch
 * carries a padlock, stays off and opens the paywall.
 */
@Composable
internal fun ProfileVoiceSection(state: AppState, isPro: Boolean, onOpenPaywall: () -> Unit) {
    val store = LocalAppStore.current
    val haptics = rememberHaptics()
    val voice = state.settings.voice
    fun change(transform: (VoiceSettings) -> VoiceSettings) =
        store.updateSettings { settings: AppSettings -> settings.copy(voice = transform(settings.voice)) }
    FormSection(
        header = L("Voice during runs"),
        footer = if (isPro) L("Your coach speaks over your music and stays quiet the rest of the time.")
        else L("With Atala Pro, your coach talks you through every run, over your music."),
    ) {
        if (isPro) {
            FormToggleRow(L("Voice coach"), voice.enabled) { on -> change { it.copy(enabled = on) } }
            FormDivider()
            FormToggleRow(L("Step changes"), voice.announceSteps, enabled = voice.enabled) { on -> change { it.copy(announceSteps = on) } }
            FormDivider()
            FormToggleRow(L("Pace too fast or too slow"), voice.announcePace, enabled = voice.enabled) { on -> change { it.copy(announcePace = on) } }
            FormDivider()
            FormToggleRow(L("Every kilometre"), voice.announceEveryKm, enabled = voice.enabled) { on -> change { it.copy(announceEveryKm = on) } }
        } else {
            // A row that looks like the switch, always off: a tap opens the paywall instead.
            val label = L("Voice coach, needs Atala Pro")
            FormRow(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                },
                onClick = {
                    haptics.tap()
                    onOpenPaywall()
                },
            ) {
                Text(L("Voice coach"), style = Theme.font(17), color = Theme.ink)
                ProLockChip()
                Spacer(Modifier.weight(1f))
                AtalaSwitch(checked = false, onChange = null)
            }
        }
    }
}

// Subscription, reminders, language, legal pages

/** Subscription row: the paywall for someone on the free plan, the Play Store's own page for a subscriber. */
@Composable
internal fun ProfileSubscriptionSection(isPro: Boolean, onOpenPaywall: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var restoring by remember { mutableStateOf(false) }
    var nothingRestored by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }

    // First time: the system question (Android 13+). Afterwards the choice lives in the settings.
    val store = LocalAppStore.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) RunReminders.refresh(context, store.current) else context.open(RunReminders.settingsIntent(context))
    }

    FormSection(header = L("Subscription")) {
        FormValueRow(
            "Atala Pro",
            value = if (isPro) L("Manage") else L("Free plan · Unlock"),
            valueColor = if (isPro) Theme.muted else Theme.green,
            chevron = true,
            onClick = {
                if (isPro) context.open(Intent(Intent.ACTION_VIEW, AtalaLinks.playSubscriptions(context.packageName)))
                else onOpenPaywall()
            },
        ) { if (isPro) ProChip() }
        if (!isPro) {
            FormDivider()
            FormButtonRow(L("Restore purchases"), color = Theme.ink) {
                if (restoring) return@FormButtonRow
                haptics.tap()
                restoring = true
                scope.launch {
                    val restored = PurchaseService.restore()
                    restoring = false
                    if (!restored) nothingRestored = true
                }
            }
            if (nothingRestored) {
                AlertDialog(
                    onDismissRequest = { nothingRestored = false },
                    text = { Text(L("No active Atala Pro subscription was found for this Google account.")) },
                    confirmButton = { TextButton(onClick = { nothingRestored = false }) { Text(L("OK")) } },
                )
            }
        }
        FormDivider()
        FormButtonRow(L("Session reminders"), color = Theme.ink) {
            haptics.tap()
            val permissionMissing = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (permissionMissing) {
                // Refused for good, the system answers at once: the settings open instead.
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.open(RunReminders.settingsIntent(context))
            }
        }
        FormDivider()
        FormValueRow(
            L("Language"),
            value = AppLanguage.all.firstOrNull { it.code == AppLanguage.current }?.name ?: "",
            chevron = true,
            onClick = { showLanguages = true },
        )
        FormDivider()
        FormButtonRow(L("Terms of use"), color = Theme.ink) { context.open(Intent(Intent.ACTION_VIEW, Uri.parse(AtalaLinks.TERMS))) }
        FormDivider()
        FormButtonRow(L("Privacy policy"), color = Theme.ink) { context.open(Intent(Intent.ACTION_VIEW, Uri.parse(AtalaLinks.PRIVACY))) }
    }

    if (showLanguages) ProfileLanguageSheet(onDismiss = { showLanguages = false })
}

/** The list of languages, each with its flag, in a sheet; picking one rebuilds the app in it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileLanguageSheet(onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var selection by remember { mutableStateOf(AppLanguage.current) }
    fun close(then: () -> Unit = {}) {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Theme.screenPadding)) {
                Text(L("Language"), style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink, modifier = Modifier.align(Alignment.Center))
                Text(
                    L("Done"), style = Theme.font(17, FontWeight.SemiBold), color = Theme.green,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable {
                            haptics.tap()
                            close()
                        },
                )
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Theme.screenPadding, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppLanguage.all.forEach { language ->
                    LanguageRow(language.flag, language.name, selected = language.code == selection) {
                        haptics.select()
                        selection = language.code
                        // Closes the sheet, then rebuilds the app in the new language.
                        close { AppLanguage.choose(language.code) }
                    }
                }
            }
        }
    }
}

/** One language: flag, name in that language, tick. The tick and the tint ease in and out. */
@Composable
private fun LanguageRow(flag: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val background by animateColorAsState(if (selected) Theme.greenTint else Color.White, tween(220), label = "bg")
    val border by animateColorAsState(if (selected) Theme.green else Theme.controlBorder, tween(220), label = "border")
    // Names keep their own direction: the flag always comes first.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(background, shape)
                .border(1.5.dp, border, shape)
                .semantics {
                    this.selected = selected
                    contentDescription = name
                }
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(flag, style = Theme.font(26), modifier = Modifier.width(34.dp))
            Text(name, style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink, maxLines = 1, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(24.dp)
                    .background(if (selected) Theme.green else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (selected) Theme.green else Theme.controlBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// Help

/** Help: what to put in the message, then the support address. */
@Composable
internal fun ProfileHelpSection() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var copied by remember { mutableStateOf(false) }
    FormSection(header = L("Help")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L("Before you write, please include:"), style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
            listOf(
                L("Your phone model"),
                L("Your subscription"),
                L("Your country"),
                L("For a bug, attach a screenshot or a screen recording"),
            ).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = Theme.font(15), color = Theme.muted)
                    Text(line, style = Theme.font(15), color = Theme.body)
                }
            }
        }
        FormDivider()
        FormRow(onClick = {
            haptics.tap()
            try {
                context.startActivity(Intent(Intent.ACTION_SENDTO, AtalaLinks.supportMail).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // No mail app set up: copy the address instead.
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("e-mail", AtalaLinks.SUPPORT_EMAIL))
                copied = true
            }
        }) {
            Icon(Icons.Filled.Email, null, tint = Theme.green, modifier = Modifier.size(20.dp))
            Text(AtalaLinks.SUPPORT_EMAIL, style = Theme.font(17), color = Theme.ink, maxLines = 1, modifier = Modifier.weight(1f))
            Text(if (copied) L("Copied") else L("Write to us"), style = Theme.font(17), color = Theme.muted)
            FormChevron()
        }
    }
}

/** Starts an activity, silently doing nothing when no app can open it. */
internal fun Context.open(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
