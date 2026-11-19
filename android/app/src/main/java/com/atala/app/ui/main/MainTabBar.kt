package com.atala.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme

/** The four tabs, in the order of the bar (the "+" sits between Plan and Coach). */
enum class MainTab(val route: Any, private val icon: ImageVector, private val selectedIcon: ImageVector) {
    Today(TodayRoute, Icons.Outlined.Home, Icons.Filled.Home),
    Plan(PlanRoute, Icons.Outlined.CalendarToday, Icons.Outlined.CalendarToday),
    Coach(CoachRoute, Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    Progress(ProgressRoute, Icons.Outlined.BarChart, Icons.Filled.BarChart);

    val title: String
        get() = when (this) {
            Today -> L("Today")
            Plan -> L("Plan")
            Coach -> L("Coach")
            Progress -> L("Progress")
        }

    fun icon(selected: Boolean) = if (selected) selectedIcon else icon
}

@Composable
fun MainTabBar(selection: MainTab, onSelect: (MainTab) -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = Theme.hairline)
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TabItem(MainTab.Today, selection, onSelect)
            TabItem(MainTab.Plan, selection, onSelect)
            PlusButton(onPlus)
            TabItem(MainTab.Coach, selection, onSelect)
            TabItem(MainTab.Progress, selection, onSelect)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(tab: MainTab, selection: MainTab, onSelect: (MainTab) -> Unit) {
    val haptics = rememberHaptics()
    val selected = tab == selection
    val color = if (selected) Theme.ink else Theme.muted
    Column(
        Modifier
            .weight(1f)
            .height(48.dp)
            .clickable(interactionSource = null, indication = null, role = Role.Tab) {
                if (!selected) haptics.select()
                onSelect(tab)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(tab.icon(selected), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(tab.title, style = Theme.font(11, if (selected) FontWeight.SemiBold else FontWeight.Normal), color = color, maxLines = 1)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PlusButton(onPlus: () -> Unit) {
    val haptics = rememberHaptics()
    val label = L("Add a run")
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(y = (-6).dp)
                .size(52.dp)
                .shadow(6.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.18f))
                .background(Theme.ink, CircleShape)
                .clickable(role = Role.Button) {
                    haptics.play(HapticKind.Confirm)
                    onPlus()
                }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
