package com.atala.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.ui.theme.Theme

/**
 * Stand-in body of a screen that is not ported yet: its title and one button per way out, so the
 * navigation can be tried end to end. Delete this file once no screen uses it.
 */
@Composable
fun ScreenPlaceholder(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Theme.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = L("Back"), tint = Theme.ink)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Text(title, style = Theme.title(28), color = Theme.ink)
        Text("Placeholder", style = Theme.font(13), color = Theme.faint)
        actions.forEach { (label, action) -> SecondaryButton(label, action) }
    }
}
