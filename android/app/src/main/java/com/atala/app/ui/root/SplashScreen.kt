package com.atala.app.ui.root

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atala.app.R
import com.atala.app.core.l10n.L
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.delay

/**
 * Launch splash (`AtalaSplashView`), about 2.6 s over the app on every cold launch: the logo mark
 * settles with a spring, opens into the full logo, the tagline rises, then everything fades away.
 * It never waits for anything: the app underneath is already there.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var settled by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    var taglineShown by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        settled = true
        delay(480)
        opened = true
        delay(220)
        taglineShown = true
        delay(1600) // long enough to read the tagline
        leaving = true
        delay(320)
        finish()
    }

    // SwiftUI spring(response:dampingFraction:) as a Compose spring: stiffness = (2π / response)².
    val markScale by animateFloatAsState(
        when {
            opened -> 0.7f
            settled -> 1f
            else -> 0.72f
        },
        spring(dampingRatio = if (opened) 0.82f else 0.6f, stiffness = if (opened) 247f else 224f), label = "mark",
    )
    val fullScale by animateFloatAsState(if (opened) 1f else 0.86f, spring(0.82f, 247f), label = "full")
    val logoSwap by animateFloatAsState(if (opened) 1f else 0f, spring(1f, 247f), label = "swap")
    val taglineAlpha by animateFloatAsState(if (taglineShown) 1f else 0f, tween(400), label = "tagline")
    val taglineOffset by animateDpAsState(if (taglineShown) 0.dp else 12.dp, tween(400), label = "rise")
    val leave by animateFloatAsState(if (leaving) 1f else 0f, tween(350), label = "leave")
    val tagline = L("Your Running Coach")
    val forest = ColorFilter.tint(Theme.forest)

    Box(
        Modifier
            .fillMaxSize()
            .alpha(1f - leave)
            .background(Theme.cream)
            // Swallows touches meant for the app underneath while the splash is on.
            .clickable(interactionSource = null, indication = null, enabled = !leaving) {}
            .clearAndSetSemantics { contentDescription = "Atala. $tagline" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .scale(1f + 0.08f * leave),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                Image(
                    painterResource(R.drawable.logo_atala_mark), null, colorFilter = forest,
                    modifier = Modifier.height(76.dp).scale(markScale).alpha(1f - logoSwap),
                )
                Image(
                    painterResource(R.drawable.logo_atala), null, colorFilter = forest,
                    modifier = Modifier.height(46.dp).scale(fullScale).alpha(logoSwap),
                )
            }
            Text(
                tagline,
                style = Theme.font(17, FontWeight.Medium),
                color = Theme.forest.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(y = taglineOffset).alpha(taglineAlpha),
            )
        }
    }
}
