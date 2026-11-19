package com.atala.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.GoalType
import com.atala.app.core.model.PlanPhase
import com.atala.app.core.model.PlanWeek
import com.atala.app.core.model.isOpenEnded
import com.atala.app.core.plan.PlanEngine
import com.atala.app.core.store.AppState
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Colours of a plan phase on the path. */
internal object OnboardingPhaseStyle {
    fun main(phase: PlanPhase): Color = when (phase) {
        PlanPhase.Base -> Theme.greenSoft
        PlanPhase.Build -> Theme.green
        PlanPhase.Specific -> Theme.orange
        PlanPhase.Taper -> Theme.yellow
    }

    fun dark(phase: PlanPhase): Color = when (phase) {
        PlanPhase.Base -> Color(0xFF2C6A58)
        PlanPhase.Build -> Color(0xFF123B30)
        PlanPhase.Specific -> Theme.orangeDeep
        PlanPhase.Taper -> Color(0xFFA98632)
    }

    fun chipText(phase: PlanPhase): Color = when (phase) {
        PlanPhase.Base, PlanPhase.Build -> Theme.green
        PlanPhase.Specific -> Theme.orangeDeep
        PlanPhase.Taper -> Theme.yellowDeep
    }

    fun chipBackground(phase: PlanPhase): Color = when (phase) {
        PlanPhase.Base, PlanPhase.Build -> Theme.greenTint
        PlanPhase.Specific -> Theme.orangeTint
        PlanPhase.Taper -> Theme.yellowTint
    }
}

/** Layout of the winding path: one row per week, centred, then the finish under it (in dp). */
private object PlanReadyPathLayout {
    const val WIDTH = 340f
    const val ROW_HEIGHT = 92f
    const val NODE_WIDTH = 64f
    const val FINISH_SIZE = 92f
    const val FINISH_TOP_GAP = 14f
    const val FINISH_ROW_HEIGHT = 190f

    /** Horizontal shift of each node from the centre line: the path swings left and right. */
    private val swings = listOf(0f, 46f, 66f, 46f, 0f, -46f, -66f, -46f)

    fun swing(index: Int): Float = swings[index % swings.size]
    fun leading(index: Int): Float = (WIDTH - NODE_WIDTH) / 2 + swing(index)
    fun nodeCenter(index: Int): Offset = Offset(WIDTH / 2 + swing(index), index * ROW_HEIGHT + ROW_HEIGHT / 2)
    fun finishCenter(count: Int): Offset = Offset(WIDTH / 2, count * ROW_HEIGHT + FINISH_TOP_GAP + FINISH_SIZE / 2)
}

private val PlanReadyLockedDark = Color(0xFFCFCFCB)

/**
 * Screen 26: a preview of the plan as a winding path. The weeks light up one after the other, down
 * to the goal; there is nothing to unlock here, one button moves on.
 */
@Composable
fun OnboardingPlanReadyScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    val nav = LocalOnboardingNavigator.current
    val haptics = rememberHaptics()
    val reduceMotion = rememberProjectionReduceMotion()
    val density = LocalDensity.current
    val weeks = state.plan?.weeks.orEmpty().take(PlanEngine.MAX_WEEKS)
    // Number of weeks lit so far.
    var revealed by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var pathTop by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (store.current.plan == null) store.generatePlan()
    }

    // Lights the weeks one by one, following them down the page, then the finish.
    LaunchedEffect(weeks.size) {
        if (finished || weeks.isEmpty()) return@LaunchedEffect
        val count = weeks.size
        if (reduceMotion) {
            revealed = count
            finished = true
            return@LaunchedEffect
        }
        delay(700)
        val rowPx = with(density) { PlanReadyPathLayout.ROW_HEIGHT.dp.toPx() }
        for (index in revealed until count) {
            haptics.play(HapticKind.Soft)
            revealed = index + 1
            // The first weeks are on screen already: only follow once they reach the lower half.
            if (index >= 3) {
                val target = (pathTop + index * rowPx + rowPx / 2 - viewport / 2f).toInt().coerceIn(0, scroll.maxValue)
                launch { scroll.animateScrollTo(target, tween(500, easing = ProjectionEaseInOut)) }
            }
            delay(380)
        }
        haptics.play(HapticKind.Success)
        finished = true
        launch {
            // Wait for the finish row's text to be laid out, then show it.
            delay(16)
            scroll.animateScrollTo(scroll.maxValue, tween(500, easing = ProjectionEaseInOut))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewport = it.height }
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlanReadyHeader(state, weeks.size)
            Spacer(Modifier.height(26.dp))
            PlanReadyPath(
                state, weeks, revealed, finished,
                Modifier.onGloballyPositioned { pathTop = it.positionInParent().y },
            )
            Spacer(Modifier.height(32.dp))
        }
        Column(Modifier.background(Color.White).navigationBarsPadding()) {
            HorizontalDivider(thickness = 1.dp, color = Theme.cardBorder)
            BottomBar {
                PrimaryButton(L("Continue"), onClick = { nav.next() })
            }
        }
    }
}

@Composable
private fun PlanReadyHeader(state: AppState, weekCount: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(L("YOUR PLAN IS READY"), style = Theme.font(12, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = Theme.green, textAlign = TextAlign.Center)
        Text(L("Here's how we'll work"), style = Theme.title(30), color = Theme.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(L("Every week you follow the path, until your goal."), style = Theme.font(17), color = Theme.body, textAlign = TextAlign.Center)
        Text(
            "${OnboardingCopy.weeks(weekCount)} · ${OnboardingCopy.sessions(OnboardingCopy.sessionsPerWeek(state.answers))}",
            style = Theme.font(13, FontWeight.SemiBold),
            color = Theme.green,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .background(Theme.greenTint, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** The path is decoration drawn in fixed positions: laid out left to right whatever the language. */
@Composable
private fun PlanReadyPath(state: AppState, weeks: List<PlanWeek>, revealed: Int, finished: Boolean, modifier: Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.width(PlanReadyPathLayout.WIDTH.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val count = weeks.size
                if (count == 0) return@Canvas
                fun point(offset: Offset) = Offset(offset.x.dp.toPx(), offset.y.dp.toPx())
                val path = Path()
                val start = point(PlanReadyPathLayout.nodeCenter(0))
                path.moveTo(start.x, start.y)
                for (index in 1..count) {
                    val previous = point(PlanReadyPathLayout.nodeCenter(index - 1))
                    val next = point(if (index < count) PlanReadyPathLayout.nodeCenter(index) else PlanReadyPathLayout.finishCenter(count))
                    val midY = (previous.y + next.y) / 2
                    path.cubicTo(previous.x, midY, next.x, midY, next.x, next.y)
                }
                drawPath(path, Theme.locked, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Column {
                weeks.forEachIndexed { index, week ->
                    PlanReadyWeekRow(index, week, lit = index < revealed)
                }
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    PlanReadyFinishRow(state, weeks.size, finished)
                }
            }
        }
    }
}

/** The node on the path, and a short label on the side that has more room. */
@Composable
private fun PlanReadyWeekRow(index: Int, week: PlanWeek, lit: Boolean) {
    val swing = PlanReadyPathLayout.swing(index)
    // Nodes on the right half get their label on the left, and the other way round.
    val labelOnLeft = swing > 0
    val labelWidth = 112f
    val gap = 14f
    val nodeLeading = PlanReadyPathLayout.leading(index)
    val labelX = if (labelOnLeft) nodeLeading - gap - labelWidth else nodeLeading + PlanReadyPathLayout.NODE_WIDTH + gap
    val scale by animateFloatAsState(if (lit) 1f else 0.86f, spring(dampingRatio = 0.6f, stiffness = 247f), label = "node")
    val labelAlpha by animateFloatAsState(if (lit) 1f else 0f, spring(dampingRatio = 1f, stiffness = 247f), label = "label")
    val km = Fmt.km(week.plannedKm, decimals = 0)
    val description = L("Week %@, %@, %@", index + 1, week.phase.title, km)
    Box(
        Modifier
            .width(PlanReadyPathLayout.WIDTH.dp)
            .height(PlanReadyPathLayout.ROW_HEIGHT.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        PlanReadyWeekNode(
            number = index + 1, phase = week.phase, unlocked = lit,
            modifier = Modifier
                .offset(x = nodeLeading.dp, y = ((PlanReadyPathLayout.ROW_HEIGHT - 64f) / 2).dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        Column(
            Modifier
                .offset(x = labelX.dp)
                .width(labelWidth.dp)
                .height(PlanReadyPathLayout.ROW_HEIGHT.dp)
                .graphicsLayer { alpha = labelAlpha },
            horizontalAlignment = if (labelOnLeft) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            val align = if (labelOnLeft) TextAlign.End else TextAlign.Start
            Text(week.phase.title, style = Theme.font(14, FontWeight.Bold), color = OnboardingPhaseStyle.chipText(week.phase), textAlign = align)
            Text(km, style = Theme.font(13), color = Theme.muted, textAlign = align)
        }
    }
}

/** One week on the path: coloured when unlocked, grey with a lock otherwise. */
@Composable
private fun PlanReadyWeekNode(number: Int, phase: PlanPhase, unlocked: Boolean, modifier: Modifier = Modifier) {
    val width: Dp = PlanReadyPathLayout.NODE_WIDTH.dp
    val dark by animateColorAsState(if (unlocked) OnboardingPhaseStyle.dark(phase) else PlanReadyLockedDark, tween(250), label = "node dark")
    val main by animateColorAsState(if (unlocked) OnboardingPhaseStyle.main(phase) else Theme.locked, tween(250), label = "node main")
    Box(modifier.width(width).height(64.dp)) {
        Box(Modifier.offset(y = 6.dp).width(width).height(58.dp).background(dark, CircleShape))
        Box(Modifier.width(width).height(58.dp).background(main, CircleShape), contentAlignment = Alignment.Center) {
            if (unlocked) {
                Text("$number", style = Theme.font(20, FontWeight.Bold), color = Color.White)
            } else {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Theme.faint, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun PlanReadyFinishRow(state: AppState, weekCount: Int, finished: Boolean) {
    val scale by animateFloatAsState(if (finished) 1.06f else 0.92f, spring(dampingRatio = 0.55f, stiffness = 158f), label = "finish")
    val textAlpha by animateFloatAsState(if (finished) 1f else 0f, spring(dampingRatio = 1f, stiffness = 158f), label = "finish text")
    val dark by animateColorAsState(if (finished) Theme.orangeDeep else PlanReadyLockedDark, tween(300), label = "finish dark")
    val main by animateColorAsState(if (finished) Theme.orange else Theme.locked, tween(300), label = "finish main")
    val flag by animateColorAsState(if (finished) Color.White else Theme.faint, tween(300), label = "flag")
    Column(
        Modifier
            .width(PlanReadyPathLayout.WIDTH.dp)
            .height(PlanReadyPathLayout.FINISH_ROW_HEIGHT.dp)
            .padding(top = PlanReadyPathLayout.FINISH_TOP_GAP.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(PlanReadyPathLayout.FINISH_SIZE.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.matchParentSize().offset(y = 7.dp).background(dark, CircleShape))
            Box(Modifier.matchParentSize().background(main, CircleShape))
            Icon(Icons.Filled.SportsScore, contentDescription = null, tint = flag, modifier = Modifier.size(38.dp))
        }
        Column(
            Modifier.graphicsLayer { alpha = textAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(planReadyFinishDetail(state), style = Theme.font(20, FontWeight.Bold), color = Theme.ink, textAlign = TextAlign.Center)
            Text(planReadyFinishTitle(state, weekCount), style = Theme.font(14), color = Theme.muted, textAlign = TextAlign.Center)
        }
    }
}

private fun planReadyFinishTitle(state: AppState, weekCount: Int): String {
    val race = state.plan?.raceDate
    if (!state.answers.goal.isOpenEnded && race != null) return Fmt.day(race, "EEEE d MMMM")
    return L("End of week %@", maxOf(1, weekCount))
}

private fun planReadyFinishDetail(state: AppState): String {
    val goal = state.answers.goal ?: GoalType.Regular
    if (goal.isOpenEnded) return L("Running is part of your week")
    val target = state.answers.targetSeconds
    if (target != null) return L("Your %@ in %@", OnboardingCopy.goalNoun(goal), Fmt.goalTime(target))
    return L("Ready for your %@", OnboardingCopy.goalNoun(goal))
}
