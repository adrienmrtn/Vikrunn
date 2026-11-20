package com.atala.app.ui.run

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atala.app.core.l10n.L
import com.atala.app.core.model.RunRecord
import com.atala.app.ui.components.BottomBar
import com.atala.app.ui.components.PrimaryButton
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Share your run" (RunShareSheet.swift): the picture in its two looks, swiped or picked, then the system share sheet. */
@Composable
fun RunShareSheet(run: RunRecord, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val styles = RunShareStyle.entries
    val pager = rememberPagerState { styles.size }
    val scope = rememberCoroutineScope()
    var images by remember(run.id) { mutableStateOf<Map<RunShareStyle, Bitmap>>(emptyMap()) }
    var rendered by remember(run.id) { mutableStateOf(false) }
    LaunchedEffect(run.id) {
        images = withContext(Dispatchers.Default) {
            styles.mapNotNull { style -> RunShareRenderer.render(context, run, style)?.let { style to it } }.toMap()
        }
        rendered = true
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Theme.surface).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
                RunTextButton(L("Close"), size = 17, modifier = Modifier.align(Alignment.CenterStart)) { onClose() }
                Text(L("Share your run"), style = Theme.font(17, FontWeight.SemiBold), color = Theme.ink, modifier = Modifier.align(Alignment.Center))
            }
            Column(
                Modifier.weight(1f).padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        SharePreview(images[styles[page]], rendered)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    styles.forEachIndexed { index, option ->
                        val selected = pager.currentPage == index
                        Row(
                            Modifier
                                .height(44.dp)
                                .background(Color.White, CircleShape)
                                .border(if (selected) 1.5.dp else 1.dp, if (selected) Theme.ink else Theme.cardBorder, CircleShape)
                                .clip(CircleShape)
                                .semantics { this.selected = selected }
                                .clickable(role = Role.Tab) {
                                    haptics.select()
                                    scope.launch { pager.animateScrollToPage(index) }
                                }
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(16.dp).background(option.background, CircleShape).border(1.dp, Theme.controlBorder, CircleShape))
                            Text(option.title, style = Theme.font(15, FontWeight.SemiBold), color = Theme.ink)
                        }
                    }
                }
                Text(L("Swipe to change the look."), style = Theme.font(13), color = Theme.muted)
            }
            BottomBar {
                PrimaryButton(L("Share"), onClick = { RunShareRenderer.share(context, run, images[styles[pager.currentPage]]) })
            }
        }
    }
}

/** One look of the picture, as it will be sent. */
@Composable
private fun SharePreview(image: Bitmap?, failed: Boolean) {
    val shape = RoundedCornerShape(22.dp)
    when {
        image != null -> Image(
            image.asImageBitmap(),
            contentDescription = L("Picture of your run"),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .aspectRatio(0.8f)
                .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.14f))
                .clip(shape)
                .border(1.dp, Theme.cardBorder, shape),
        )
        failed -> Text(
            L("The picture could not be prepared. Your run will be shared as text."),
            style = Theme.font(15), color = Theme.muted, textAlign = TextAlign.Center,
        )
        else -> CircularProgressIndicator(color = Theme.green)
    }
}
