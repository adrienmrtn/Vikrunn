package com.atala.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme

/**
 * The list of languages, each with its flag, the chosen one ticked (`LanguageList`). Used in
 * onboarding and in the profile.
 */
@Composable
fun LanguageList(selection: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppLanguage.all.forEach { language ->
            LanguageRow(
                flag = language.flag,
                name = language.name,
                selected = language.code == selection,
                onClick = {
                    if (language.code != selection) {
                        haptics.select()
                        onSelect(language.code)
                    }
                },
            )
        }
    }
}

/** One language: flag, name in that language, tick. The tick and the tint ease in and out. */
@Composable
private fun LanguageRow(flag: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fill by animateColorAsState(if (selected) Theme.greenTint else Color.White, tween(220, easing = OnboardingEasing.easeInOut), label = "fill")
    val stroke by animateColorAsState(if (selected) Theme.green else Theme.controlBorder, tween(220, easing = OnboardingEasing.easeInOut), label = "stroke")
    // Names are written in their own language: the row reads left to right, as on the iPhone.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(fill, shape)
                .border(1.5.dp, stroke, shape)
                .semantics {
                    this.selected = selected
                    contentDescription = name
                    role = Role.RadioButton
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                flag,
                style = Theme.font(26).copy(lineHeight = 30.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(34.dp)
                    .clearAndSetSemantics {},
            )
            OnboardingFitText(
                name,
                Theme.font(17, FontWeight.SemiBold),
                Theme.ink,
                Modifier
                    .weight(1f)
                    .clearAndSetSemantics {},
                minScale = 0.8f,
            )
            LanguageTick(selected)
        }
    }
}

/** Both marks stay in place: the empty circle fades while the tick grows into it. */
@Composable
private fun LanguageTick(selected: Boolean) {
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        onboardingSpring(0.3, 0.7),
        label = "tick",
    )
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(22.dp)
                .graphicsLayer { alpha = (1f - progress).coerceIn(0f, 1f) },
        ) {
            val width = 2.dp.toPx()
            drawCircle(Theme.controlBorder, radius = (size.minDimension - width) / 2, style = Stroke(width))
        }
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Theme.green,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    val scale = 0.6f + 0.4f * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = progress.coerceIn(0f, 1f)
                },
        )
    }
}

/**
 * Right after the welcome (step `Language`): checks the language before anything is asked.
 *
 * The choice stays on this screen until Continue: `AppLanguage.choose` rebuilds the whole tree at
 * once (AtalaRoot cross-fades on the language code) and the rebuilt flow restores itself on the
 * saved step. So a new language is applied after `nav.saveNext()`: one cross-fade, from this
 * screen straight to the next one.
 */
@Composable
fun OnboardingLanguageScreen() {
    val nav = LocalOnboardingNavigator.current
    var selection by remember { mutableStateOf(AppLanguage.current) }
    OnboardingScaffold(
        title = L("First, let's make sure you have the right language"),
        buttonTitle = L("Continue"),
        onContinue = { submit(nav, selection) },
    ) {
        LanguageList(selection = selection, onSelect = { selection = it })
    }
}

private fun submit(nav: OnboardingNavigator, code: String) {
    if (code != AppLanguage.current && nav.saveNext()) {
        AppLanguage.choose(code)
    } else {
        // Same language: it is kept as the user's choice, nothing is rebuilt.
        AppLanguage.chosen = code
        nav.next()
    }
}
