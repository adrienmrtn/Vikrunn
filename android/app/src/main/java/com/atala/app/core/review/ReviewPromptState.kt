package com.atala.app.core.review

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.delay

/**
 * When the Google Play rating is asked for: once, a moment after the user has arrived in the app
 * (ReviewPrompt.swift). Also what the full-screen offers (gift, Pro welcome) read to stay behind it,
 * so the order on that launch is always the same: the rating request first, the offers afterwards.
 */
object ReviewPromptState {
    private const val KEY = "atala.review.asked"
    /** How long the main screen stays on before the request. */
    const val DELAY_MS = 2_500L
    /** Time left to the request before a full-screen offer may show on the same launch. */
    private const val OFFER_HOLD_MS = 8_000L

    private var prefs: SharedPreferences? = null
    /** Without preferences (previews): kept in memory. */
    @Volatile private var askedInMemory = false

    /** When the request was made (elapsed realtime), on this launch only. */
    @Volatile private var requestedAt: Long? = null

    /** True once this launch has decided: asked, or not asked this time. */
    @Volatile var settled = false
        private set

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("atala.review", Context.MODE_PRIVATE)
    }

    /** True for good once the rating was asked for on this phone. */
    val wasAsked: Boolean
        get() = prefs?.getBoolean(KEY, false) ?: askedInMemory

    fun noteAsked() {
        prefs?.edit()?.putBoolean(KEY, true)?.apply() ?: run { askedInMemory = true }
        requestedAt = SystemClock.elapsedRealtime()
        settled = true
    }

    /** Nothing was asked on this launch (something else was on screen): the next launch tries again. */
    fun noteSkipped() {
        settled = true
    }

    /**
     * Called by the full-screen offers before they show. Returns at once when the rating was asked
     * on an earlier launch; otherwise waits for this launch's request (10 seconds at most), then
     * leaves it [OFFER_HOLD_MS].
     */
    suspend fun waitBeforeOffer() {
        var polls = 0
        while (!wasAsked && !settled && polls < 20) {
            delay(500)
            polls += 1
        }
        val asked = requestedAt ?: return
        val left = OFFER_HOLD_MS - (SystemClock.elapsedRealtime() - asked)
        if (left > 0) delay(left)
    }
}
