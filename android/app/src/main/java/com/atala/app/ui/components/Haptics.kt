package com.atala.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** The feedback a touch gives, named as in Haptics.swift. */
enum class HapticKind {
    /** Ordinary buttons, navigation, chips, rows. */
    Tap,
    /** Selection changed: option cards, toggles, pickers, tabs, carousel. */
    Select,
    /** Primary call-to-action, pause and resume. */
    Confirm,
    /** Rare, for the moments that count (start, commit, unlock). */
    Strong,
    /** Something arrived by itself (coach reply, text finished). */
    Soft,
    /** A sharp marker (lap, next step). */
    Rigid,
    Success,
    Warning,
    Error,
}

class Haptics(private val view: View) {
    fun play(kind: HapticKind) {
        view.performHapticFeedback(constant(kind))
    }

    fun tap() = play(HapticKind.Tap)
    fun select() = play(HapticKind.Select)
    fun confirm() = play(HapticKind.Confirm)

    private fun constant(kind: HapticKind): Int {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        return when (kind) {
            HapticKind.Tap, HapticKind.Soft -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticKind.Select -> HapticFeedbackConstants.CLOCK_TICK
            HapticKind.Rigid -> HapticFeedbackConstants.CONTEXT_CLICK
            HapticKind.Confirm, HapticKind.Success ->
                if (modern) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            HapticKind.Strong -> HapticFeedbackConstants.LONG_PRESS
            HapticKind.Warning, HapticKind.Error ->
                if (modern) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
