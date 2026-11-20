package com.atala.app.ui.run

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Coach
import com.atala.app.core.model.Feeling
import com.atala.app.core.run.RunFormat
import com.atala.app.ui.components.SelectionMark
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * "Session complete" right after the run (RunFeelingScreen.swift): the shared celebration, the
 * figures of the run and "How was it?". The answer is optional.
 */
@Composable
fun RunFeelingScreen(distanceM: Double, durationSec: Int, coach: Coach, onDone: (Feeling?) -> Unit) {
    var feeling by rememberSaveable { mutableStateOf<Feeling?>(null) }
    val averagePace = if (distanceM > 0) (durationSec / (distanceM / 1000)).toInt() else 0
    val stats = listOf(
        SessionCompleteStat(distanceM, L("Distance")) { RunFormat.distance(it) },
        SessionCompleteStat(durationSec.toDouble(), L("Time")) { Fmt.duration(it.roundToInt()) },
        SessionCompleteStat(averagePace.toDouble(), L("Average pace")) { "${Fmt.pace(it.roundToInt())}/km" },
    )
    SessionCompleteScreen(
        dataLine = L("%@ in %@", RunFormat.distance(distanceM), Fmt.duration(durationSec)),
        stats = stats,
        coach = coach,
        coachLine = if (feeling == Feeling.Pain) L("Thank you for telling me. Take it easy for the rest of the day.")
        else L("Well done, that one is in the bank. How was it?"),
        buttonTitle = L("See my summary"),
        onPrimary = { onDone(feeling) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Feeling.entries.forEach { option ->
                FeelingRow(
                    feeling = option,
                    subtitle = if (option == Feeling.Pain) L("I'd rather tell %@", coach.name) else option.subtitle,
                    selected = feeling == option,
                ) { feeling = if (feeling == option) null else option }
            }
            Text(
                L("Your answer helps tune your next sessions."),
                style = Theme.font(13),
                color = Theme.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun FeelingRow(feeling: Feeling, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .card(selected)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton) {
                haptics.select()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeelingBadge(feeling)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(feeling.title, style = Theme.font(16, FontWeight.SemiBold), color = Theme.ink)
            Text(subtitle, style = Theme.font(13), color = Theme.muted)
        }
        SelectionMark(selected)
    }
}
