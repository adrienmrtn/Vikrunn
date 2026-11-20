package com.atala.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.atala.app.core.review.ReviewPromptState
import com.atala.app.ui.components.findActivity
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import kotlinx.coroutines.delay

/**
 * Asks for a Google Play rating the first time the main app has been on screen for a moment
 * (ReviewPrompt.swift, In-App Review here). Part of the main screen only, so never during the
 * onboarding; [isActive] is false while the launch splash is still showing. Never over a sheet, a
 * pushed screen or a paywall ([canPresent]): the next launch asks instead. Google Play decides
 * whether its card really appears.
 */
@Composable
internal fun ReviewPrompt(isActive: Boolean, canPresent: Boolean) {
    val context = LocalContext.current
    val free by rememberUpdatedState(canPresent)
    LaunchedEffect(isActive) {
        if (!isActive || ReviewPromptState.wasAsked) return@LaunchedEffect
        delay(ReviewPromptState.DELAY_MS)
        val activity = context.findActivity()
        if (!free || activity == null) {
            ReviewPromptState.noteSkipped()
            return@LaunchedEffect
        }
        ReviewPromptState.noteAsked()
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.launchReview(activity, manager.requestReview())
        }
    }
}
