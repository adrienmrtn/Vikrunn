package com.atala.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atala.app.core.account.AuthError
import com.atala.app.core.account.AuthService
import com.atala.app.core.account.signInWithGoogle
import com.atala.app.core.l10n.L
import com.atala.app.core.model.Account
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AuthSheetMode { Options, Email, Code }

private enum class AuthSheetBusy { Google, SendingCode, VerifyingCode }

/**
 * The sign-in choices as a bottom sheet (SignInOptionsSheet.swift): Google, e-mail one-time code
 * (code field, resend) and "Continue without an account". Sign in with Apple is iPhone only.
 * [onSignedIn] receives the account (a guest included); the caller then runs `AuthSignInFlow.complete`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInOptionsSheet(onSignedIn: (Account) -> Unit, onDismiss: () -> Unit) {
    var busy by remember { mutableStateOf<AuthSheetBusy?>(null) }
    val isBusy by rememberUpdatedState(busy != null)
    // No dismissal while a request is running.
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !isBusy },
    )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheet,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        SignInOptionsContent(
            busy = busy,
            setBusy = { busy = it },
            onSignedIn = { account ->
                scope.launch { sheet.hide() }.invokeOnCompletion { onSignedIn(account) }
            },
        )
    }
}

@Composable
private fun SignInOptionsContent(
    busy: AuthSheetBusy?,
    setBusy: (AuthSheetBusy?) -> Unit,
    onSignedIn: (Account) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(AuthSheetMode.Options) }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf<String?>(null) }
    // The code already sent for checking, so the same one is never submitted twice.
    var lastSubmitted by remember { mutableStateOf<String?>(null) }

    fun show(error: Throwable) {
        if (error is AuthError) {
            if (error == AuthError.Cancelled) return
            errorText = error.userMessage
        } else {
            errorText = L("Something went wrong. Please try again.")
        }
        haptics.play(HapticKind.Error)
    }

    fun clearMessages() {
        errorText = null
        infoText = null
    }

    fun goBack() {
        haptics.tap()
        clearMessages()
        if (mode == AuthSheetMode.Code) {
            code = ""
            mode = AuthSheetMode.Email
        } else {
            focus.clearFocus()
            mode = AuthSheetMode.Options
        }
    }

    fun signInWithGoogle() {
        if (busy != null) return
        setBusy(AuthSheetBusy.Google)
        clearMessages()
        scope.launch {
            try {
                val account = AuthService.signInWithGoogle(context)
                setBusy(null)
                haptics.play(HapticKind.Success)
                onSignedIn(account)
            } catch (error: Throwable) {
                setBusy(null)
                show(error)
            }
        }
    }

    fun sendCode() {
        if (busy != null) return
        if (!AuthService.isValidEmail(AuthService.normalized(email))) {
            show(AuthError.InvalidEmail)
            return
        }
        setBusy(AuthSheetBusy.SendingCode)
        clearMessages()
        scope.launch {
            try {
                AuthService.sendEmailCode(email)
                setBusy(null)
                code = ""
                lastSubmitted = null
                mode = AuthSheetMode.Code
            } catch (error: Throwable) {
                setBusy(null)
                show(error)
            }
        }
    }

    fun resendCode() {
        if (busy != null) return
        lastSubmitted = null
        haptics.tap()
        setBusy(AuthSheetBusy.SendingCode)
        clearMessages()
        scope.launch {
            try {
                AuthService.sendEmailCode(email)
                setBusy(null)
                code = ""
                infoText = L("A new code is on its way.")
            } catch (error: Throwable) {
                setBusy(null)
                show(error)
            }
        }
    }

    fun verifyCode() {
        if (busy != null || code.length < 6 || code == lastSubmitted) return
        lastSubmitted = code
        setBusy(AuthSheetBusy.VerifyingCode)
        clearMessages()
        scope.launch {
            try {
                val account = AuthService.verifyEmailCode(email, code)
                setBusy(null)
                focus.clearFocus()
                haptics.play(HapticKind.Success)
                onSignedIn(account)
            } catch (error: Throwable) {
                setBusy(null)
                show(error)
            }
        }
    }

    BackHandler(enabled = mode != AuthSheetMode.Options && busy == null) { goBack() }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = Theme.screenPadding, end = Theme.screenPadding, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mode != AuthSheetMode.Options) {
                Row(
                    Modifier
                        .height(44.dp)
                        .clickable(enabled = busy == null) { goBack() },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Theme.ink, modifier = Modifier.size(22.dp))
                    Text(L("Back"), style = Theme.font(15, FontWeight.Medium), color = Theme.ink)
                }
            }
            Text(
                when (mode) {
                    AuthSheetMode.Options -> L("Save your plan")
                    AuthSheetMode.Email -> L("Continue with e-mail")
                    AuthSheetMode.Code -> L("Enter your code")
                },
                style = Theme.font(22, FontWeight.Bold), color = Theme.ink,
            )
            Text(
                when (mode) {
                    AuthSheetMode.Options -> L("Choose how to sign in. It takes ten seconds.")
                    AuthSheetMode.Email -> L("We'll send you a 6-digit code. No password to remember.")
                    AuthSheetMode.Code -> L("We sent a 6-digit code to %@.", AuthService.normalized(email))
                },
                style = Theme.font(14), color = Theme.muted,
            )
        }

        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "mode",
        ) { current ->
            when (current) {
                AuthSheetMode.Options -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuthProviderButton(
                        L("Continue with Google"), onClick = { signInWithGoogle() },
                        loading = busy == AuthSheetBusy.Google, enabled = busy == null,
                    ) { AuthGoogleMark() }
                    AuthProviderButton(
                        L("Continue with e-mail"),
                        onClick = {
                            clearMessages()
                            mode = AuthSheetMode.Email
                        },
                        enabled = busy == null,
                    ) { Icon(Icons.Outlined.Email, null, tint = Theme.ink, modifier = Modifier.size(20.dp)) }
                    UnderlinedLink(L("Continue without an account"), enabled = busy == null) {
                        if (busy == null) {
                            haptics.tap()
                            onSignedIn(AuthService.continueAsGuest())
                        }
                    }
                }
                AuthSheetMode.Email -> EmailSection(
                    email = email,
                    onEmail = { email = it },
                    sending = busy == AuthSheetBusy.SendingCode,
                    onSend = { sendCode() },
                )
                AuthSheetMode.Code -> CodeSection(
                    code = code,
                    onCode = { code = it.filter(Char::isDigit).take(10) },
                    verifying = busy == AuthSheetBusy.VerifyingCode,
                    sending = busy == AuthSheetBusy.SendingCode,
                    busy = busy != null,
                    onVerify = { verifyCode() },
                    onResend = { resendCode() },
                )
            }
        }

        // Submit by itself once the typing stops: the code may be longer than 6 digits.
        LaunchedEffect(code, mode) {
            if (mode != AuthSheetMode.Code || code.length < 6) return@LaunchedEffect
            delay(900)
            verifyCode()
        }

        errorText?.let { text ->
            Text(
                text, style = Theme.font(13, FontWeight.Medium), color = Theme.orangeDeep,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Theme.orangeTint, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
        } ?: infoText?.let { text ->
            Text(text, style = Theme.font(13), color = Theme.green, modifier = Modifier.fillMaxWidth())
        }

        Text(
            termsText(L("By continuing, you accept the [terms of use](https://vikstudios.com/atala/terms) and the [privacy policy](https://vikstudios.com/atala).")),
            style = Theme.font(12).copy(textAlign = TextAlign.Center),
            color = Theme.muted,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmailSection(email: String, onEmail: (String) -> Unit, sending: Boolean, onSend: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    val label = L("E-mail address")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthFieldBox {
            if (email.isEmpty()) Text(L("you@example.com"), style = Theme.font(16), color = Theme.faint)
            BasicTextField(
                value = email,
                onValueChange = onEmail,
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = Theme.ink),
                cursorBrush = SolidColor(Theme.green),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Send, autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
                    .semantics { contentDescription = label },
            )
        }
        AuthSubmitButton(
            L("Send me a code"), onClick = onSend,
            enabled = AuthService.isValidEmail(AuthService.normalized(email)), loading = sending,
        )
    }
}

@Composable
private fun CodeSection(
    code: String,
    onCode: (String) -> Unit,
    verifying: Boolean,
    sending: Boolean,
    busy: Boolean,
    onVerify: () -> Unit,
    onResend: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    val label = L("6-digit code")
    val style = TextStyle(
        fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace,
        color = Theme.ink, textAlign = TextAlign.Center,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthFieldBox {
            if (code.isEmpty()) Text("000000", style = style.copy(color = Theme.faint), modifier = Modifier.fillMaxWidth())
            BasicTextField(
                value = code,
                onValueChange = onCode,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Theme.green),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onVerify() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester)
                    .semantics { contentDescription = label },
            )
        }
        AuthSubmitButton(L("Confirm"), onClick = onVerify, enabled = code.length >= 6, loading = verifying)
        UnderlinedLink(if (sending) L("Sending…") else L("Send a new code"), enabled = !busy, onClick = onResend)
    }
}

@Composable
private fun UnderlinedLink(title: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        title,
        style = Theme.font(14, FontWeight.Medium).copy(textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center),
        color = Theme.muted,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** The terms sentence with its two `[text](url)` links, as the iPhone renders its Markdown. */
private fun termsText(source: String): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    var index = 0
    for (match in pattern.findAll(source)) {
        append(source.substring(index, match.range.first))
        val (text, url) = match.destructured
        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = Theme.ink, textDecoration = TextDecoration.Underline)))) {
            append(text)
        }
        index = match.range.last + 1
    }
    append(source.substring(index))
}
