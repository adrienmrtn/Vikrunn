package com.atala.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.l10n.L
import com.atala.app.ui.auth.SignInOptionsSheet
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.AtalaImages
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay

private data class WelcomeSlide(
    val id: Int,
    /** Illustration shown until the photo is in the catalog. */
    val illustration: String,
    /** Photo of the slide, used as soon as it exists in the catalog. */
    val photo: String,
    val title: String,
    val text: String,
) {
    val usesPhoto: Boolean get() = AtalaImages[photo] != null

    companion object {
        val all: List<WelcomeSlide>
            get() = listOf(
                WelcomeSlide(0, "welcome_1", "welcome_photo_1", L("Your Running Coach"),
                    L("Atala is the coach behind runners who show up, from a first 5K to a half marathon.")),
                WelcomeSlide(1, "welcome_2", "welcome_photo_2", L("A plan that fits your life"),
                    L("Pick the days you can run. Your plan is built around them.")),
                WelcomeSlide(2, "welcome_3", "welcome_photo_3", L("A coach who tells you why"),
                    L("Every session has its reason, every question its answer.")),
            )
    }
}

/** Screen 1 (step `Welcome`): three swipeable slides, then "Get started" or "I already have an account". */
@Composable
fun OnboardingWelcomeScreen() {
    val store = LocalAppStore.current
    val nav = LocalOnboardingNavigator.current
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    val slides = remember { WelcomeSlide.all }
    val pager = rememberPagerState { slides.size }
    var showSignIn by remember { mutableStateOf(false) }
    // The page the automatic slideshow last showed. Once the user swipes elsewhere, it stops.
    var autoPage by remember { mutableIntStateOf(0) }

    // The photo reaches the top edge, under a status bar with light icons over its dark veil.
    OnboardingSystemBars(active = isCurrentOnboardingStep(), lightIcons = slides.first().usesPhoto)

    // The slides move on by themselves every few seconds, up to the last one, until the user swipes.
    LaunchedEffect(Unit) {
        if (reduceMotion || slides.size < 2) return@LaunchedEffect
        while (true) {
            delay(3600)
            if (showSignIn) continue
            val current = pager.currentPage
            // The user moved to another slide by hand: leave them in charge.
            if (current != autoPage || pager.isScrollInProgress) return@LaunchedEffect
            val next = current + 1
            if (next >= slides.size) return@LaunchedEffect
            autoPage = next
            pager.animateScrollToPage(next, animationSpec = tween(700, easing = OnboardingEasing.easeInOut))
        }
    }
    // A swipe by hand ticks like the iPhone's carousel.
    LaunchedEffect(pager) {
        var last = pager.currentPage
        androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect { page ->
            if (page != last && page != autoPage) haptics.select()
            last = page
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        val imageHeight = maxOf(220.dp, maxHeight * 0.52f)
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight + 150.dp),
                verticalAlignment = Alignment.Top,
            ) { index ->
                WelcomeSlideView(slides[index], imageHeight)
            }
            WelcomeDots(count = slides.size, page = pager.currentPage, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(start = Theme.screenPadding, end = Theme.screenPadding, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PrimaryButton(L("Get started"), onClick = { nav.next() })
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(role = Role.Button) {
                            haptics.tap()
                            showSignIn = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(L("I already have an account"), style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                }
            }
        }
        // The logo stays still above the moving slides.
        OnboardingBrandLogo(
            26.dp,
            Theme.cream,
            Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, top = 22.dp)
                .clearAndSetSemantics { contentDescription = "Atala" },
        )
    }

    if (showSignIn) {
        SignInOptionsSheet(
            onSignedIn = { account ->
                showSignIn = false
                // Returning user: when the account brought a finished onboarding back, the root
                // switches to the app. A new account simply carries on with the questions.
                if (store.current.account == null) store.setAccount(account)
                if (!store.current.hasCompletedOnboarding) nav.go(OnboardingStep.FirstName)
            },
            onDismiss = { showSignIn = false },
        )
    }
}

@Composable
private fun WelcomeSlideView(slide: WelcomeSlide, imageHeight: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .clearAndSetSemantics {},
        ) {
            val id = AtalaImages[if (slide.usesPhoto) slide.photo else slide.illustration]
            if (id != null) {
                Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // Photos only: a soft dark veil at the top keeps the cream logo legible.
            if (slide.usesPhoto) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(minOf(190.dp, imageHeight * 0.5f))
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Black.copy(alpha = 0f)))),
                )
            }
        }
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Always whole: longer languages take two lines or shrink a little, never "…".
            OnboardingFitText(
                slide.title,
                Theme.title(28),
                Theme.ink,
                Modifier.semantics { heading() },
                maxLines = 2,
                minScale = 0.6f,
            )
            Text(slide.text, style = Theme.font(16).copy(lineHeight = 22.sp), color = Color(0xFF5A5A5A))
        }
    }
}

@Composable
private fun WelcomeDots(count: Int, page: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = L("Slide %@ of %@", page + 1, count) },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            val selected = index == page
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, tween(250, easing = OnboardingEasing.easeInOut), label = "dot")
            val color by animateColorAsState(if (selected) Theme.ink else Color(0xFFD0D0D0), tween(250), label = "dot colour")
            Box(
                Modifier
                    .width(width)
                    .height(8.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

/** Screen 2 (step `FirstName`), written to `answers.firstName`. */
@Composable
fun OnboardingFirstNameScreen() {
    val store = LocalAppStore.current
    val nav = LocalOnboardingNavigator.current
    val state by collectAppState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf(state.answers.firstName) }
    var focused by remember { mutableStateOf(false) }
    val trimmed = text.trim()
    val border by animateColorAsState(if (focused) Theme.ink else Theme.controlBorder, tween(200), label = "field border")

    val submit = {
        if (trimmed.isNotEmpty()) {
            store.updateAnswers { it.copy(firstName = trimmed) }
            focusManager.clearFocus()
            keyboard?.hide()
            nav.next()
        }
    }
    LaunchedEffect(Unit) {
        if (trimmed.isEmpty()) {
            // After the screen has settled, so the keyboard does not fight the transition.
            delay(350)
            runCatching { focus.requestFocus() }
        }
    }
    OnboardingScaffold(
        title = L("What's your first name?"),
        canContinue = trimmed.isNotEmpty(),
        onContinue = submit,
    ) {
        val shape = RoundedCornerShape(16.dp)
        BasicTextField(
            value = text,
            onValueChange = { value ->
                text = value
                store.updateAnswers { it.copy(firstName = value) }
            },
            singleLine = true,
            textStyle = Theme.font(18, FontWeight.SemiBold).copy(color = Theme.ink),
            cursorBrush = SolidColor(Theme.ink),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White, shape)
                        .border(1.5.dp, border, shape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(L("First name"), style = Theme.font(18, FontWeight.SemiBold), color = Theme.faint)
                    }
                    field()
                }
            },
        )
    }
}
