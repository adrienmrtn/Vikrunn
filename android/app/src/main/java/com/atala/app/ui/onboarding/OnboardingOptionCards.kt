package com.atala.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.SelectionMark
import com.atala.app.ui.components.card
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

// Controls shared by the questionnaire screens (OnboardingComponents.swift): plain option rows,
// two-way choice buttons, a segmented picker, chips, the big time card with its wheels.

/** Light grey of the segmented picker and the wheel band (0xF1F1EF on the iPhone). */
val OnboardingControlFill = Color(0xFFF1F1EF)

/** Simple 58 dp row with a radio mark (gender, long run day) (`OnboardingPlainOptionRow`). */
@Composable
fun OnboardingPlainOptionRow(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(16.dp)
    val border by animateColorAsState(if (selected) Theme.ink else Theme.controlBorder, tween(200), label = "row border")
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color.White, shape)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton) {
                haptics.select()
                onClick()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = Theme.font(16, if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = Theme.ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        SelectionMark(selected)
    }
}

/** One of two side-by-side choices ("Just finish" / "A precise time"): 52 dp, outlined when on. */
@Composable
fun RowScope.OnboardingChoiceButton(title: String, isOn: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(16.dp)
    val border by animateColorAsState(if (isOn) Theme.ink else Theme.controlBorder, tween(200), label = "choice border")
    Box(
        Modifier
            .weight(1f)
            .height(52.dp)
            .background(Color.White, shape)
            .border(if (isOn) 1.5.dp else 1.dp, border, shape)
            .semantics { selected = isOn }
            .clickable(role = Role.RadioButton) {
                haptics.select()
                onClick()
            }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        OnboardingFitText(title, Theme.font(15, if (isOn) FontWeight.SemiBold else FontWeight.Medium), Theme.ink, minScale = 0.75f)
    }
}

/**
 * Segmented picker on a light grey track; the white pill slides to the chosen segment (distance
 * of the recent result).
 */
@Composable
fun OnboardingSegmentedPicker(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(OnboardingControlFill, RoundedCornerShape(16.dp))
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(segment * selectedIndex.coerceAtLeast(0), tween(220, easing = OnboardingEasing.easeInOut), label = "segment")
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset(x = offset)
                    .width(segment)
                    .height(44.dp)
                    .shadow(1.5.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
                    .background(Color.White, RoundedCornerShape(12.dp)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .semantics { this.selected = selected }
                        .clickable(interactionSource = null, indication = null, role = Role.Tab) {
                            haptics.select()
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, style = Theme.font(15, if (selected) FontWeight.SemiBold else FontWeight.Medium), color = Theme.ink, maxLines = 1)
                }
            }
        }
    }
}

/** Small capsule label (the "why" recalled on the coach's reply). */
@Composable
fun OnboardingChip(text: String, modifier: Modifier = Modifier, background: Color = Color.White, color: Color = Theme.ink) {
    Text(
        text,
        style = Theme.font(13, FontWeight.SemiBold),
        color = color,
        modifier = modifier
            .background(background, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Text that shrinks (down to [minScale]) rather than being cut, like `minimumScaleFactor` on the
 * iPhone. Drawn once its size is settled, so it never flickers.
 */
@Composable
fun OnboardingFitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minScale: Float = 0.6f,
    textAlign: TextAlign? = null,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    Text(
        text,
        style = style.copy(
            fontSize = style.fontSize * scale,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
        ),
        color = color,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Clip,
        textAlign = textAlign,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && scale > minScale) {
                scale = max(minScale, scale * 0.92f)
            } else {
                ready = true
            }
        },
        modifier = modifier.drawWithContent { if (ready) drawContent() },
    )
}

private val androidx.compose.ui.unit.TextUnit.isSpecified: Boolean get() = this != androidx.compose.ui.unit.TextUnit.Unspecified

// MARK: Time card

/**
 * Big time with minus and plus buttons (hold to repeat) (`OnboardingTimeCard`). Tapping the time
 * opens the wheels.
 */
@Composable
fun OnboardingTimeCard(
    seconds: Int,
    caption: String,
    stepSeconds: Int,
    onChange: (Int) -> Unit,
    onTapTime: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val haptics = rememberHaptics()
    val timeColor by animateColorAsState(if (dimmed) Theme.faint else Theme.ink, tween(250), label = "time colour")
    Row(
        modifier
            .fillMaxWidth()
            .card(radius = 24.dp)
            .padding(horizontal = 16.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepeatRoundButton(icon = Icons.Filled.Remove, label = L("Less"), current = seconds, delta = -stepSeconds, onChange = onChange)
        Column(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = L("Opens the time picker"),
                ) {
                    haptics.tap()
                    onTapTime()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            OnboardingFitText(
                Fmt.goalTime(seconds),
                Theme.font(48, FontWeight.Bold).copy(fontFeatureSettings = "tnum"),
                timeColor,
            )
            Text(caption, style = Theme.font(13), color = Theme.muted, textAlign = TextAlign.Center)
        }
        RepeatRoundButton(icon = Icons.Filled.Add, label = L("More"), current = seconds, delta = stepSeconds, onChange = onChange)
    }
}

/** White round button that steps once on a tap and keeps stepping while held (`buttonRepeatBehavior`). */
@Composable
private fun RepeatRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    current: Int,
    delta: Int,
    onChange: (Int) -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val value by rememberUpdatedState(current)
    val change by rememberUpdatedState(onChange)
    var pressed by remember { mutableStateOf(false) }
    val step = {
        haptics.select()
        change(value + delta)
    }
    Box(
        Modifier
            .size(52.dp)
            .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
            .background(Color.White, CircleShape)
            .border(1.dp, Theme.controlBorder, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { step(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    step()
                    val repeat = scope.launch {
                        delay(450)
                        while (true) {
                            step()
                            delay(90)
                        }
                    }
                    waitForUpOrCancellation()
                    repeat.cancel()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Theme.ink, modifier = Modifier.size(22.dp))
    }
}

// MARK: Wheels

/**
 * A wheel of [count] values, the chosen one in the middle band (`Picker` with `.wheel` style):
 * it snaps on a value and ticks at each one it passes.
 */
@Composable
fun OnboardingWheelPicker(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 34.dp,
    visibleItems: Int = 5,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, (count - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(state)
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val centred by remember(state, itemPx) {
        derivedStateOf {
            val index = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            index.coerceIn(0, (count - 1).coerceAtLeast(0))
        }
    }
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(state) {
        var last = centred
        snapshotFlow { centred }.collect { index ->
            if (index != last) {
                last = index
                haptics.select()
                select(index)
            }
        }
    }
    Box(modifier.height(itemHeight * visibleItems)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(itemHeight)
                .background(OnboardingControlFill, RoundedCornerShape(9.dp)),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItems / 2)),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count) { index ->
                Box(
                    Modifier
                        .fillParentMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            val info = state.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                            if (item != null) {
                                val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val distance = (item.offset + item.size / 2f - centre) / itemPx
                                val far = abs(distance).coerceAtMost(3f)
                                alpha = (1f - 0.3f * far).coerceAtLeast(0.12f)
                                rotationX = (-distance * 18f).coerceIn(-60f, 60f)
                                val scale = 1f - 0.05f * far
                                scaleX = scale
                                scaleY = scale
                            }
                        }
                        .clickable(interactionSource = null, indication = null) {
                            scope.launch { state.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(index), style = Theme.font(20).copy(fontFeatureSettings = "tnum"), color = Theme.ink, maxLines = 1)
                }
            }
        }
    }
}

/** Hours, minutes and seconds wheels shown in a small sheet (`OnboardingTimeWheelSheet`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingTimeWheelSheet(
    title: String,
    showsHours: Boolean,
    initialSeconds: Int,
    onDone: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val total = max(0, initialSeconds)
    var hours by remember { mutableIntStateOf(if (showsHours) minOf(3, total / 3600) else 0) }
    var minutes by remember { mutableIntStateOf(if (showsHours) (total % 3600) / 60 else minOf(179, total / 60)) }
    var secs by remember { mutableIntStateOf(total % 60) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Color.White,
        contentColor = Theme.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                if (showsHours) {
                    OnboardingWheelPicker(4, hours, { hours = it }, { "$it h" }, Modifier.weight(1f))
                }
                OnboardingWheelPicker(
                    if (showsHours) 60 else 180,
                    minutes,
                    { minutes = it },
                    { L("%@ min", it) },
                    Modifier.weight(1f),
                )
                OnboardingWheelPicker(60, secs, { secs = it }, { "$it s" }, Modifier.weight(1f))
            }
            PrimaryButton(
                L("Done"),
                onClick = {
                    onDone(hours * 3600 + minutes * 60 + secs)
                    scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
                },
                modifier = Modifier.padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 8.dp, bottom = 12.dp),
            )
        }
    }
}

/** Presses a little, like the iPhone's plain buttons. */
@Composable
fun rememberPressAlpha(interaction: MutableInteractionSource, pressedAlpha: Float = 0.7f): Float {
    val pressed by interaction.collectIsPressedAsState()
    return if (pressed) pressedAlpha else 1f
}
