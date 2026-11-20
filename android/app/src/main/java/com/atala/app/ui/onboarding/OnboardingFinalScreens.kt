package com.atala.app.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.atala.app.R
import com.atala.app.core.l10n.L
import com.atala.app.ui.components.HapticKind
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.components.rememberReduceMotion
import com.atala.app.ui.root.LocalAppStore
import com.atala.app.ui.root.collectAppState
import com.atala.app.ui.theme.Theme

private fun Context.finalActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.finalHas(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Atala's logo mark on its forest tile. */
@Composable
private fun FinalLogoMark(height: Int) {
    Image(
        painterResource(R.drawable.logo_atala_mark), contentDescription = null,
        colorFilter = ColorFilter.tint(Theme.cream),
        modifier = Modifier.height(height.dp),
    )
}

/** Atala on one side, the service it connects to on the other, and dots travelling between them. */
@Composable
private fun FinalLinkGraphic(icon: ImageVector, tint: Color, linked: Boolean) {
    val reduceMotion = rememberReduceMotion()
    val tile = 84.dp
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Theme.surface, RoundedCornerShape(28.dp))
            .padding(vertical = 26.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(tile)
                .shadow(12.dp, shape, ambientColor = Theme.forest.copy(alpha = 0.25f), spotColor = Theme.forest.copy(alpha = 0.25f))
                .background(Theme.forest, shape),
            contentAlignment = Alignment.Center,
        ) { FinalLogoMark(40) }
        AnimatedContent(
            linked,
            transitionSpec = { (scaleIn(spring(dampingRatio = 0.7f, stiffness = 195f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "link",
        ) { isLinked ->
            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                if (isLinked) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.green, modifier = Modifier.size(34.dp))
                } else {
                    // Every other dot is lit, and they swap on each pulse.
                    val pulse = if (reduceMotion) {
                        0f
                    } else {
                        val transition = rememberInfiniteTransition(label = "dots")
                        val value by transition.animateFloat(
                            0f, 1f, infiniteRepeatable(tween(900, easing = OnboardingEasing.easeInOut), RepeatMode.Reverse), label = "pulse",
                        )
                        value
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        repeat(4) { index ->
                            val lit = if (index % 2 == 0) pulse else 1f - pulse
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .graphicsLayer { alpha = 0.25f + 0.75f * lit }
                                    .background(Theme.green, CircleShape),
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .size(tile)
                .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                .background(Color.White, shape)
                .border(1.dp, Theme.cardBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
        }
    }
}

/** One short benefit with a green check. */
@Composable
private fun FinalBenefitRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Theme.green, modifier = Modifier.size(22.dp))
        Text(text, style = Theme.font(16), color = Theme.ink, modifier = Modifier.weight(1f))
    }
}

/**
 * Screen 32: the location permission (ACCESS_FINE_LOCATION, asked with ACCESS_COARSE_LOCATION as
 * Android 12+ requires). No background location: runs are tracked by a foreground service.
 */
@Composable
fun OnboardingPermissionsScreen() {
    val context = LocalContext.current
    val state by collectAppState()
    val haptics = rememberHaptics()
    var allowed by remember { mutableStateOf(context.finalHas(Manifest.permission.ACCESS_FINE_LOCATION)) }
    // The user said no to the system question.
    var refused by rememberSaveable { mutableStateOf(false) }
    // False once Android no longer shows the question (refused twice, or "don't ask again"): Settings only.
    var canAskAgain by rememberSaveable { mutableStateOf(true) }
    // True once the user has tapped the button: the answer that follows deserves a haptic.
    var asked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = context.finalHas(Manifest.permission.ACCESS_FINE_LOCATION)
        refused = !allowed
        canAskAgain = context.finalActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
        } ?: false
        if (allowed && asked) haptics.play(HapticKind.Success)
    }
    // Back from the system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = context.finalHas(Manifest.permission.ACCESS_FINE_LOCATION)
        if (now && !allowed && asked) haptics.play(HapticKind.Success)
        allowed = now
        if (now) refused = false
    }

    val opensSettings = refused && !canAskAgain
    val buttonTitle = when {
        allowed -> L("Location allowed")
        opensSettings -> L("Open Settings")
        else -> L("Allow location")
    }
    val footnote = if (refused && !allowed) {
        L("Location is off for Atala. Turn it on in Settings, or log your runs by hand.")
    } else {
        L("Only while you're running with the app. You can say no and log your runs by hand.")
    }

    OnboardingScaffold(title = L("Let Atala follow your runs")) {
        FinalLinkGraphic(Icons.Filled.NearMe, Theme.orange, linked = allowed)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FinalBenefitRow(L("Your distance and pace, live"))
            FinalBenefitRow(L("%@'s voice when you're too fast or too slow", state.coach.name))
            FinalBenefitRow(L("Your route on a map after every run"))
        }
        FinalCapsuleButton(
            title = buttonTitle,
            icon = if (allowed) Icons.Filled.Check else Icons.Filled.NearMe,
            done = allowed,
            background = Theme.orange,
            onClick = {
                haptics.confirm()
                asked = true
                if (opensSettings) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } else {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
        )
        Text(footnote, style = Theme.font(13).copy(lineHeight = 18.sp), color = Theme.muted)
    }
}

/** Full-width 52 dp capsule: coloured until done, then green tint with a tick and no action. */
@Composable
private fun FinalCapsuleButton(title: String, icon: ImageVector, done: Boolean, background: Color, onClick: () -> Unit, enabled: Boolean = true) {
    val fill by animateColorAsState(if (done) Theme.greenTint else background, tween(300), label = "button fill")
    val content by animateColorAsState(if (done) Theme.green else Color.White, tween(300), label = "button text")
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .clickable(enabled = enabled && !done, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(title, style = Theme.font(16, FontWeight.SemiBold), color = content)
    }
}

private class FinalSampleNotification(val id: Int, val time: String, val title: String, val body: String)


@Composable
private fun FinalNotificationCard(sample: FinalSampleNotification, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .background(Color.White, shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).background(Theme.forest, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            FinalLogoMark(20)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sample.title,
                    style = Theme.font(15, FontWeight.SemiBold),
                    color = Theme.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                )
                Text(sample.time, style = Theme.font(12), color = Theme.muted)
            }
            Text(sample.body, style = Theme.font(14), color = Theme.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Step `HealthConnect` (Apple Health on the iPhone). Android has no Apple Health: the navigator skips
 * this step, so it is never shown. Should a saved step ever land here, the onboarding simply ends,
 * as the iPhone's "Start" button does.
 */
@Composable
fun OnboardingHealthConnectScreen() {
    val store = LocalAppStore.current
    LaunchedEffect(Unit) { store.finishOnboarding() }
    Box(Modifier.fillMaxSize().background(Color.White))
}
