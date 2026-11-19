package com.atala.app.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme

/** Hidden but still laid out: swallows touches and stays out of accessibility. */
private fun Modifier.inert(): Modifier = clearAndSetSemantics {}.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

/** Empty room a screen leaves at its top for the flow's fixed header (status bar included). */
@Composable
fun OnboardingHeaderSpace() {
    Spacer(Modifier.statusBarsPadding().height(OnboardingHeaderHeight))
}

/**
 * Standard questionnaire screen (`OnboardingScaffold`): room for the flow's fixed header, title and
 * subtitle, scrolling content, bottom primary button and an optional secondary one.
 *
 * @param canContinue enables the primary button (an answer is required).
 * @param tinted grey background (`Theme.surface`) and buttons without the white bottom bar.
 * @param buttonsVisible false hides the buttons until a staged reveal has finished.
 * @param onContinue primary action; by default the flow moves to the next step.
 */
@Composable
fun OnboardingScaffold(
    title: String? = null,
    subtitle: String? = null,
    buttonTitle: String = L("Continue"),
    buttonHaptic: HapticKind? = HapticKind.Confirm,
    canContinue: Boolean = true,
    tinted: Boolean = false,
    buttonsVisible: Boolean = true,
    secondaryTitle: String? = null,
    onSecondary: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nav = LocalOnboardingNavigator.current
    val haptics = rememberHaptics()
    val buttonsAlpha by animateFloatAsState(if (buttonsVisible) 1f else 0f, tween(500), label = "buttons")
    val buttonsOffset by animateDpAsState(if (buttonsVisible) 0.dp else 10.dp, tween(500), label = "buttons offset")
    // A staged reveal has ended: a soft tick as the button arrives (not when it is there from the start).
    var wasHidden by remember { mutableStateOf(!buttonsVisible) }
    LaunchedEffect(buttonsVisible) {
        if (buttonsVisible && wasHidden) haptics.play(HapticKind.Soft)
        wasHidden = !buttonsVisible
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (tinted) Theme.surface else Color.White)
            .imePadding(),
    ) {
        OnboardingHeaderSpace()
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (title != null || subtitle != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (title != null) {
                        Text(title, style = Theme.title(28), color = Theme.ink, modifier = Modifier.semantics { heading() })
                    }
                    if (subtitle != null) {
                        Text(subtitle, style = Theme.font(15).copy(lineHeight = 21.sp), color = Theme.muted)
                    }
                }
            }
            content()
        }
        val buttons: @Composable ColumnScope.() -> Unit = {
            PrimaryButton(buttonTitle, onClick = { onContinue?.invoke() ?: nav.next() }, enabled = canContinue, haptic = buttonHaptic)
            if (secondaryTitle != null && onSecondary != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(role = Role.Button) {
                            haptics.tap()
                            onSecondary()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(secondaryTitle, style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                }
            }
        }
        val bottom = Modifier
            .offset(y = buttonsOffset)
            .alpha(buttonsAlpha)
            .then(if (buttonsVisible) Modifier else Modifier.inert())
        if (tinted) {
            Column(
                bottom
                    .navigationBarsPadding()
                    .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = buttons,
            )
        } else {
            BottomBar(bottom.background(Color.White).navigationBarsPadding(), content = buttons)
        }
    }
}
