package com.atala.app.testing

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.L10n
import com.atala.app.core.model.ProAccess
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Duration

/** Shared set-up of the JVM tests (Robolectric keeps the app's singletons between tests). */
object TestSupport {
    val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * Back to a free English user. Every pack of the coach's voice is marked complete, so
     * `VoicePack.prepare` never downloads anything during a test (the clips then come from the assets).
     */
    fun reset(language: String? = null) {
        val context = context
        L10n.init(context.assets)
        AppLanguage.init(context)
        AppLanguage.chosen = language
        ProAccess.setPro(false)
        blockVoiceDownloads(context)
    }

    fun blockVoiceDownloads(context: Context) {
        for (language in AppLanguage.supported) {
            for (voice in listOf("f", "m")) {
                val folder = File(context.filesDir, "CoachVoice/v1/$language/$voice")
                folder.mkdirs()
                File(folder, ".complete").createNewFile()
            }
        }
    }

    /** Moves the main looper (and SystemClock) on by [seconds], running what falls due. */
    fun idle(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }

    fun idleMillis(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }
}
