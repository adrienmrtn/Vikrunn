package com.atala.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The activity behind a composable's context: needed by Google Play (purchases, rating request),
 * the window (system bars) and the run flow (moved to the background).
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
