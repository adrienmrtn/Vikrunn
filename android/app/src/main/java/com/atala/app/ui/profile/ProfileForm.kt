package com.atala.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme

/*
 * The grouped form of the iPhone's Profile (SwiftUI `Form`): white rounded sections on a light
 * background, a small header above and a footnote under each, rows separated by hairlines.
 */

internal val formBackground = Color(0xFFF2F2F7)
private val sectionShape = RoundedCornerShape(12.dp)

@Composable
internal fun FormSection(
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        if (header != null) {
            Text(
                header.uppercase(com.atala.app.core.l10n.AppLanguage.locale),
                style = Theme.font(13), color = Theme.muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
            )
        }
        Column(Modifier.fillMaxWidth().background(Color.White, sectionShape), content = content)
        if (footer != null && footer.isNotEmpty()) {
            Text(
                footer, style = Theme.font(13), color = Theme.muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/** Hairline between two rows of a section, inset like the iPhone's. */
@Composable
internal fun FormDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = Color(0xFFD8D8DC))
}

/** A row of a section: 44 dp at least, 16 dp side padding. */
@Composable
internal fun FormRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Title on the start side, value (muted) and an optional chevron on the end side. */
@Composable
internal fun FormValueRow(
    title: String,
    value: String? = null,
    valueColor: Color = Theme.muted,
    chevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val haptics = rememberHaptics()
    FormRow(onClick = onClick?.let { action -> { haptics.tap(); action() } }, enabled = enabled) {
        Text(title, style = Theme.font(17), color = Theme.ink)
        trailing()
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(value, style = Theme.font(17), color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (chevron) FormChevron()
    }
}

@Composable
internal fun FormChevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Theme.faint,
        modifier = Modifier.size(20.dp),
    )
}

/** A tinted button row ("Sync now", "Sign out"...), with a spinner while busy. */
@Composable
internal fun FormButtonRow(
    title: String,
    color: Color = Theme.green,
    enabled: Boolean = true,
    loading: Boolean = false,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    FormRow(onClick = onClick, enabled = enabled) {
        Text(title, style = Theme.font(17, if (bold) FontWeight.SemiBold else FontWeight.Normal), color = color, modifier = Modifier.weight(1f))
        if (loading) CircularProgressIndicator(color = Theme.muted, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun FormToggleRow(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    FormRow(enabled = enabled, onClick = { haptics.select(); onChange(!checked) }) {
        Text(title, style = Theme.font(17), color = Theme.ink, modifier = Modifier.weight(1f))
        AtalaSwitch(checked = checked, enabled = enabled, onChange = { haptics.select(); onChange(it) })
    }
}

@Composable
internal fun AtalaSwitch(checked: Boolean, enabled: Boolean = true, onChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Theme.green,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFE9E9EA),
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedTrackColor = Theme.green.copy(alpha = 0.4f),
            disabledUncheckedTrackColor = Color(0xFFE9E9EA),
            disabledUncheckedThumbColor = Color.White,
            disabledCheckedThumbColor = Color.White,
            disabledUncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Small padlock chip "Atala Pro" (yellow), next to a feature that needs the subscription. */
@Composable
internal fun ProLockChip() {
    Row(
        Modifier
            .background(Theme.yellowTint, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, null, tint = Theme.yellowDeep, modifier = Modifier.size(11.dp))
        Text("Atala Pro", style = Theme.font(11, FontWeight.Bold), color = Theme.yellowDeep, maxLines = 1)
    }
}

/** Plain text inside a section (notes, explanations). */
@Composable
internal fun FormNote(text: String, color: Color = Theme.muted, size: Int = 13) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, style = Theme.font(size), color = color)
    }
}

@Composable
internal fun RowScope.FormSpacer() = Spacer(Modifier.width(0.dp).weight(1f))
