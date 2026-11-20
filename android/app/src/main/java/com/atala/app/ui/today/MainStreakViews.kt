package com.atala.app.ui.today

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atala.app.R
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Streak
import com.atala.app.core.model.StreakEngine
import com.atala.app.ui.components.card
import com.atala.app.ui.theme.Theme

/** Small flame chip: "3-week streak". Greyed while this week has nothing done yet (`MainStreakChip`). */
@Composable
internal fun MainStreakChip(streak: Streak) {
    val active = streak.activeThisWeek
    val description = L("%@ streak", StreakEngine.weeks(streak.current))
    Row(
        Modifier
            .background(if (active) Theme.orangeTint else Theme.surface, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LocalFireDepartment, null, tint = if (active) Theme.orange else Theme.faint, modifier = Modifier.size(13.dp))
        Text(
            L("%@-week streak", streak.current),
            style = Theme.font(12, FontWeight.SemiBold),
            color = if (active) Theme.orangeDeep else Theme.muted,
            maxLines = 1,
        )
    }
}

/** Streak card: current streak, best streak and what to do next (`MainStreakCard`). */
@Composable
internal fun MainStreakCard(streak: Streak, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .card(radius = 22.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(if (streak.current > 0) Theme.orangeTint else Theme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.LocalFireDepartment, null,
                    tint = if (streak.current > 0) Theme.orange else Theme.faint,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (streak.current > 0) L("%@ in a row", StreakEngine.weeks(streak.current)) else L("No streak yet"),
                    style = Theme.font(22, FontWeight.Bold), color = Theme.ink,
                )
                Text(
                    if (streak.best > 0) L("Your best: %@", StreakEngine.weeks(streak.best)) else L("Every streak starts with one session"),
                    style = Theme.font(13), color = Theme.muted,
                )
            }
        }
        Text(StreakEngine.nudge(streak), style = Theme.font(14), color = Theme.body)
    }
}

/** The Atala logo, tinted forest green (`BrandLogo(style: .full)`). */
@Composable
internal fun BrandLogo(height: Dp = 22.dp, mark: Boolean = false) {
    Image(
        painterResource(if (mark) R.drawable.logo_atala_mark else R.drawable.logo_atala),
        contentDescription = "Atala",
        colorFilter = ColorFilter.tint(Theme.forest),
        modifier = Modifier.height(height),
    )
}
