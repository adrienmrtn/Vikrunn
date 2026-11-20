package com.atala.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.atala.app.core.l10n.L
import com.atala.app.ui.theme.Theme

/** One answer of a [ChoiceDialog]. */
class DialogChoice(val title: String, val destructive: Boolean = false, val action: () -> Unit)

/** The red of the iPhone's destructive buttons. */
private val destructiveRed = Color(0xFFD7332B)

/**
 * The iPhone's confirmation dialog (`.confirmationDialog` with a visible title): a title, a
 * message, the choices stacked, and Cancel (or [cancelTitle]) last, apart. Any choice closes it
 * ([onDismiss] first, then its action). Used by Today, Plan, the run and the guided sessions.
 */
@Composable
fun ChoiceDialog(
    title: String,
    message: String?,
    choices: List<DialogChoice>,
    onDismiss: () -> Unit,
    cancelTitle: String = L("Cancel"),
) {
    val haptics = rememberHaptics()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(24.dp)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink, textAlign = TextAlign.Center)
                if (message != null) {
                    Text(message, style = Theme.font(13).copy(lineHeight = 17.6.sp), color = Theme.muted, textAlign = TextAlign.Center)
                }
            }
            choices.forEach { choice ->
                HorizontalDivider(color = Theme.hairline)
                DialogRow(choice.title, if (choice.destructive) destructiveRed else Theme.ink, FontWeight.Normal) {
                    onDismiss()
                    choice.action()
                }
            }
            HorizontalDivider(color = Theme.hairline)
            DialogRow(cancelTitle, Theme.ink, FontWeight.SemiBold) {
                haptics.tap()
                onDismiss()
            }
        }
    }
}

@Composable
private fun DialogRow(title: String, color: Color, weight: FontWeight, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = Theme.font(16, weight), color = color, textAlign = TextAlign.Center)
    }
}
