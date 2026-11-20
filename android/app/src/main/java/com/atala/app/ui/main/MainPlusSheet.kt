package com.atala.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.launch

/** The "+" of the tab bar: a free run or a past run (`MainPlusSheet`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPlusSheet(onDismiss: () -> Unit, onFreeRun: () -> Unit, onPastRun: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The chosen flow opens once the sheet is gone.
    fun pick(action: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color.White) {
        Column(
            Modifier.padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(L("Add a run"), style = Theme.font(20, FontWeight.Bold), color = Theme.ink, modifier = Modifier.padding(bottom = 2.dp))
            PlusRow(L("Start a free run"), L("Outside the plan, tracked with GPS"), Icons.AutoMirrored.Filled.DirectionsRun) { pick(onFreeRun) }
            PlusRow(L("Add a past run"), L("Treadmill or a run you did without the app"), Icons.Outlined.EditNote) { pick(onPastRun) }
        }
    }
}

@Composable
private fun PlusRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .card()
            .clickable {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
            Text(subtitle, style = Theme.font(13), color = Theme.muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Theme.faint, modifier = Modifier.size(20.dp))
    }
}
