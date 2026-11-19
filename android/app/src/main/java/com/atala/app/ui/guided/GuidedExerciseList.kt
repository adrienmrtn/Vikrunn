package com.atala.app.ui.guided

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atala.app.core.exercise.ExerciseCatalog
import com.atala.app.core.exercise.ExerciseInfo
import com.atala.app.core.exercise.GuidedExerciseKind
import com.atala.app.core.exercise.GuidedPrescription
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import com.atala.app.ui.today.lineHeight
import kotlinx.coroutines.launch

/** The exercises of a session: picture, name, muscles and the amount. A tap opens the instructions. */
@Composable
internal fun GuidedExerciseList(exercises: List<GuidedPrescription>) {
    val haptics = rememberHaptics()
    var shown by remember { mutableStateOf<ExerciseInfo?>(null) }
    val hint = L("Shows how to do it")
    Column {
        exercises.forEach { exercise ->
            GuidedExerciseRow(exercise, Modifier.clickable(onClickLabel = hint, role = Role.Button) {
                haptics.tap()
                shown = exercise.info
            })
        }
    }
    shown?.let { info -> GuidedHowToSheet(info, onDismiss = { shown = null }) }
}

@Composable
private fun GuidedExerciseRow(exercise: GuidedPrescription, modifier: Modifier) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuidedExerciseThumb(exercise.info, 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.info.name, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
                Text(exercise.info.muscles, style = Theme.font(13), color = Theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(exercise.amountText, style = Theme.font(14, FontWeight.SemiBold), color = Theme.ink, textAlign = TextAlign.End)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Theme.faint, modifier = Modifier.size(16.dp))
        }
        HorizontalDivider(color = Theme.hairline)
    }
}

/** "How to": both positions, the steps, what to keep in mind, the usual mistake and the muscles (`GuidedHowToSheet`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuidedHowToSheet(info: ExerciseInfo, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color.White) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(info.kind.title, style = Theme.font(13, FontWeight.SemiBold), color = Theme.orangeDeep)
                    Text(info.name, style = Theme.title(28), color = Theme.ink)
                    Text(info.summary, style = Theme.font(15), color = Theme.muted)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Theme.surface, CircleShape)
                        .clickable(role = Role.Button) {
                            haptics.tap()
                            scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, L("Close"), tint = Theme.ink, modifier = Modifier.size(18.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FramePanel(info, info.imageStart, L("Start"), Modifier.weight(1f))
                FramePanel(info, info.imageEnd, if (info.kind == GuidedExerciseKind.Stretch) L("Stretch") else L("Effort"), Modifier.weight(1f))
            }
            Section(L("How to do it")) {
                info.steps.forEachIndexed { index, step -> Line("${index + 1}", step) }
            }
            Section(L("Keep in mind")) {
                info.cues.forEach { Line(null, it) }
            }
            Section(L("Common mistake")) {
                Text(info.mistake, style = Theme.font(15), color = Theme.body)
            }
            Section(L("Muscles")) {
                Text(info.muscles, style = Theme.font(15), color = Theme.body)
            }
            Text(
                ExerciseCatalog.safetyLine,
                style = Theme.font(13), color = Theme.muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Theme.surface, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun FramePanel(info: ExerciseInfo, name: String, caption: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Theme.surface, RoundedCornerShape(20.dp))
                .padding(10.dp),
        ) {
            GuidedExerciseImage(name, info.kind.icon)
        }
        Text(caption, style = Theme.font(12, FontWeight.SemiBold), color = Theme.muted)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Theme.font(13, FontWeight.SemiBold), color = Theme.ink)
        content()
    }
}

@Composable
private fun Line(marker: String?, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (marker != null) {
                Box(Modifier.size(22.dp).background(Theme.greenTint, CircleShape), contentAlignment = Alignment.Center) {
                    Text(marker, style = Theme.font(12, FontWeight.Bold), color = Theme.green)
                }
            } else {
                Box(Modifier.size(6.dp).background(Theme.greenSoft, CircleShape))
            }
        }
        Text(text, style = Theme.font(15).copy(lineHeight = lineHeight(15, 2)), color = Theme.body, modifier = Modifier.weight(1f))
    }
}
