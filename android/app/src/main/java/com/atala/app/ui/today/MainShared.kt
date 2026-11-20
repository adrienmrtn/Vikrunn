package com.atala.app.ui.today

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.PlanCalendar
import com.atala.app.core.model.PlanWeek
import com.atala.app.core.model.RunRecord
import com.atala.app.core.model.Workout
import com.atala.app.core.model.WorkoutKind
import com.atala.app.core.model.WorkoutStatus
import com.atala.app.core.store.AppState
import com.atala.app.core.store.AppStore
import com.atala.app.ui.components.IconTile
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Pieces of Atala/Features/Main (MainShared.swift, MainStagedAppear.swift, MainRunMatching.swift and
 * the "where the runner stands" part of MainPlanRenewal.swift) used by Today, Plan and the session
 * detail. Internal to the app module; the Plan and Guided packages import them from here.
 */

// MARK: - Motion

/** True when the system turned animations off (Settings > Accessibility > Remove animations). */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** The screens of the shell that open with a staged entrance. */
internal enum class MainStagedScreen { Today, Plan }

/** Screens whose staged entrance already played (`MainChrome.stagedScreens`). */
private object MainStagedMemory {
    val played = mutableSetOf<MainStagedScreen>()
}

/** The single flag driving the staged entrance of the blocks below it. Visible by default. */
private val LocalMainStagedRevealed = compositionLocalOf { true }

/** Plays the staged entrance of a screen once per run of the app shell (`mainStagedEntrance`). */
@Composable
internal fun MainStagedEntrance(screen: MainStagedScreen, content: @Composable () -> Unit) {
    var revealed by remember { mutableStateOf(screen in MainStagedMemory.played) }
    LaunchedEffect(screen) {
        if (!revealed) {
            // Let the screen settle (launch, or the fade from the onboarding) before the first block moves.
            delay(120)
            MainStagedMemory.played += screen
            revealed = true
        }
    }
    CompositionLocalProvider(LocalMainStagedRevealed provides revealed, content = content)
}

/** One block of a staged entrance: fades and rises in, 80 ms after the block before it. */
internal fun Modifier.mainStagedAppear(index: Int): Modifier = composed {
    val revealed = LocalMainStagedRevealed.current
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(revealed) {
        if (revealed && progress.value < 1f) {
            if (reduceMotion) {
                // A plain short fade, all blocks together, nothing moves.
                progress.animateTo(1f, tween(200))
            } else {
                delay(index * 80L)
                // spring(response: 0.5, dampingFraction: 0.86)
                progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 158f))
            }
        }
    }
    val rise = with(LocalDensity.current) { 16.dp.toPx() }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = if (reduceMotion) 0f else (1f - progress.value) * rise
    }
}

// MARK: - Layout helpers

/** Pulls the element up over the one before it (a negative top padding). */
internal fun Modifier.overlapTop(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val offset = amount.roundToPx()
    layout(placeable.width, (placeable.height - offset).coerceAtLeast(0)) {
        placeable.place(0, -offset)
    }
}

/** Line height of the iPhone's `.lineSpacing(extra)` for a given font size. */
internal fun lineHeight(size: Int, extra: Int) = (size * 1.2f + extra).sp

/**
 * Text that shrinks down to [minScale] of its size rather than being cut (`minimumScaleFactor`).
 * It stays invisible for the frames it takes to find its size.
 */
@Composable
internal fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minScale: Float = 0.7f,
    textAlign: TextAlign? = null,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    Text(
        text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        color = color,
        style = style.copy(
            fontSize = style.fontSize * scale,
            letterSpacing = if (style.letterSpacing.isSp) style.letterSpacing * scale else style.letterSpacing,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && scale > minScale) {
                scale = maxOf(minScale, scale * 0.92f)
            } else {
                ready = true
            }
        },
    )
}

// MARK: - Glass panel

/** Very light glass panel laid over an illustration (`mainGlass(radius:)`). */
internal fun Modifier.mainGlass(radius: Dp = 22.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val shadowColor = Theme.forest.copy(alpha = 0.10f)
    return this
        .shadow(10.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
        .background(Color.White.copy(alpha = 0.94f), shape)
        .border(1.dp, Color.White.copy(alpha = 0.9f), shape)
}

// MARK: - Small shared views

/** Rounded illustration used at the top of the main screens. */
@Composable
internal fun MainHeroImage(name: String, height: Dp = 170.dp, alignment: Alignment = Alignment.Center) {
    val id = AtalaImages[name] ?: return
    Image(
        painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = alignment,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(24.dp)),
    )
}

/** One value with its caption, used in rows of three. */
@Composable
internal fun MainStat(value: String, label: String, modifier: Modifier = Modifier, labelColor: Color = Theme.muted) {
    Column(
        modifier.clearAndSetSemantics { contentDescription = "$value, $label" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FitText(value, Theme.font(17, FontWeight.SemiBold), Theme.ink, minScale = 0.7f)
        Text(label, style = Theme.font(12), color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Distance, duration and target pace of a session (or duration, exercises and effort). */
@Composable
internal fun MainWorkoutStats(workout: Workout, labelColor: Color = Color(0xFF5A5A5A)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        if (workout.kind.isRun) {
            val pace = workout.targetPace?.let { "${Fmt.pace(it)}/km" } ?: L("Free")
            MainStat(Fmt.km(workout.distanceKm), L("Distance"), Modifier.weight(1f), labelColor)
            MainStat(Fmt.minutes(workout.durationMin), L("Duration"), Modifier.weight(1f), labelColor)
            MainStat(pace, L("Target pace"), Modifier.weight(1f), labelColor)
        } else {
            val exercises = workout.blocks.sumOf { it.steps.size * maxOf(1, it.repeats) }
            MainStat(Fmt.minutes(workout.durationMin), L("Duration"), Modifier.weight(1f), labelColor)
            MainStat("$exercises", L("Exercises"), Modifier.weight(1f), labelColor)
            MainStat(L("%@ of 10", workout.effort), L("Effort"), Modifier.weight(1f), labelColor)
        }
    }
}

@Composable
internal fun MainStatusBadge(status: WorkoutStatus) {
    when (status) {
        WorkoutStatus.Done -> StatusBadge(L("Done"), Icons.Filled.Check, Theme.green, Theme.greenTint)
        WorkoutStatus.Missed -> StatusBadge(L("Missed"), Icons.Filled.Close, Theme.orangeDeep, Theme.orangeTint)
        WorkoutStatus.Planned -> Unit
    }
}

@Composable
private fun StatusBadge(title: String, icon: ImageVector, tint: Color, background: Color) {
    Row(
        Modifier
            .background(background, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = title },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(10.dp))
        Text(title, style = Theme.font(12, FontWeight.SemiBold), color = tint, maxLines = 1)
    }
}

/** Thin progress bar (6 dp) as in the mockups. [fraction] is 0...1. */
@Composable
internal fun MainProgressBar(fraction: Double, modifier: Modifier = Modifier, tint: Color = Theme.orange, label: String = L("Progress")) {
    val clamped = fraction.coerceIn(0.0, 1.0).toFloat()
    val percent = L("%@ percent", (clamped * 100).roundToInt())
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Theme.ink.copy(alpha = 0.12f))
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = percent
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(tint, CircleShape),
        )
    }
}

@Composable
internal fun MainSectionTitle(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = Theme.font(18, FontWeight.Bold), color = Theme.ink, modifier = Modifier.alignByBaseline())
        Box(Modifier.weight(1f).width(8.dp))
        if (trailing != null) {
            Text(trailing, style = Theme.font(13), color = Theme.muted, modifier = Modifier.alignByBaseline())
        }
    }
}

/** Empty state with an optional action (no plan yet, session not found). */
@Composable
internal fun MainEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionTitle: String? = null,
    action: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, size = 48.dp)
        Text(title, style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink, textAlign = TextAlign.Center)
        Text(message, style = Theme.font(14), color = Theme.muted, textAlign = TextAlign.Center)
        if (actionTitle != null && action != null) {
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptics.tap()
                        action()
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionTitle, style = Theme.font(15, FontWeight.SemiBold), color = Theme.green)
            }
        }
    }
}

// MARK: - Model helpers

/** Dot colour of the session kind (soft green easy, deep green long, orange hard, yellow strength). */
internal val Workout.mainDotColor: Color
    get() = when (kind) {
        WorkoutKind.Easy, WorkoutKind.Free -> Theme.greenSoft
        WorkoutKind.Long -> Theme.green
        WorkoutKind.Intervals, WorkoutKind.Tempo, WorkoutKind.RacePace, WorkoutKind.Race -> Theme.orange
        WorkoutKind.Strength, WorkoutKind.Stretching -> Theme.yellow
    }

/** "9.6 km" for runs, "20 min" for strength and stretching. */
internal val Workout.mainAmountText: String
    get() = if (kind.isRun) Fmt.km(distanceKm) else Fmt.minutes(durationMin)

/** The icon of a session kind (`WorkoutKind.symbol` on the iPhone). */
internal val WorkoutKind.icon: ImageVector
    get() = when (this) {
        WorkoutKind.Easy -> Icons.AutoMirrored.Filled.DirectionsRun
        WorkoutKind.Intervals -> Icons.Filled.Bolt
        WorkoutKind.Tempo -> Icons.Filled.Speed
        WorkoutKind.Long -> Icons.Filled.Route
        WorkoutKind.RacePace -> Icons.Filled.Flag
        WorkoutKind.Strength -> Icons.Filled.FitnessCenter
        WorkoutKind.Stretching -> Icons.Filled.SelfImprovement
        WorkoutKind.Race -> Icons.Filled.SportsScore
        WorkoutKind.Free -> Icons.Filled.Add
    }

/** "6" or "6.4", the number of a distance without its unit. */
internal fun kmNumber(km: Double): String =
    if (abs(km - km.roundToInt()) < 0.05) "${km.roundToInt()}" else String.format(Locale.ROOT, "%.1f", km)

/** Kilometres actually run during a plan week (all saved runs of that week). */
internal fun AppState.mainDoneKm(week: PlanWeek): Double {
    val start = PlanCalendar.startOfDay(week.startDate)
    val end = PlanCalendar.addDays(start, 7)
    return runs.filter { it.date >= start && it.date < end }.sumOf { it.distanceKm }
}

/** The plan week that contains today. Unlike `currentWeek`, it never falls back to week 1. */
internal val AppState.mainThisWeek: PlanWeek?
    get() {
        val plan = plan ?: return null
        val monday = PlanCalendar.mondayOfWeek(Instant.now())
        return plan.weeks.firstOrNull { PlanCalendar.isSameDay(it.startDate, monday) }
    }

/** This week, or the first week before the plan starts, or the last one once it is over. */
internal val AppState.mainPositionWeek: PlanWeek?
    get() {
        val plan = plan ?: return null
        mainThisWeek?.let { return it }
        val first = plan.weeks.firstOrNull() ?: return null
        val last = plan.weeks.last()
        return if (Instant.now() < first.startDate) first else last
    }

/** True when nothing is left to do: every session from today on is done (or there is none). */
internal val AppState.mainPlanEnded: Boolean
    get() {
        val plan = plan ?: return false
        val today = PlanCalendar.startOfDay(Instant.now())
        return plan.allWorkouts.none { it.status == WorkoutStatus.Planned && it.date >= today }
    }

/** 1-based position of this week in the plan. Null before the plan starts and once it is over. */
internal val AppState.mainCurrentWeekNumber: Int?
    get() {
        val plan = plan ?: return null
        val week = mainThisWeek ?: return null
        val index = plan.weeks.indexOfFirst { it.id == week.id }
        return if (index >= 0) index + 1 else null
    }

// MARK: - Counting a run for a session (MainRunMatching.swift)

/** The run session planned on the day of this run and not done yet, when the run belongs to no session. */
internal fun AppState.mainSessionToCount(run: RunRecord): Workout? {
    if (run.workoutId != null && workout(run.workoutId) != null) return null
    return workouts(run.date).firstOrNull { it.kind.isRun && it.status != WorkoutStatus.Done }
}

/** A run of today that belongs to no session, with the session it could count for. */
internal val AppState.mainUncountedRunToday: Pair<RunRecord, Workout>?
    get() {
        for (record in runs) {
            if (!PlanCalendar.isToday(record.date)) continue
            mainSessionToCount(record)?.let { return record to it }
        }
        return null
    }

/** Attaches the run to the session and marks the session as done. */
internal fun AppStore.mainCount(runId: UUID, workoutId: UUID) {
    val state = current
    val record = state.run(runId) ?: return
    val session = state.workout(workoutId) ?: return
    var updated = record.copy(workoutId = session.id)
    if (updated.title.isEmpty() || updated.title == "Run" || updated.title == "Free run") {
        updated = updated.copy(title = session.title)
    }
    addRun(updated)
}

