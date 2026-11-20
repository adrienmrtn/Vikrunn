package com.atala.app.core.l10n

import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Number and date formatting shared by every screen (`Fmt` in Theme.swift). */
object Fmt {
    /** "4:25" from seconds per kilometre. */
    fun pace(secPerKm: Int): String {
        if (secPerKm <= 0) return "--:--"
        return format("%d:%02d", secPerKm / 60, secPerKm % 60)
    }

    /** "54:48" or "1:45:00". */
    fun duration(seconds: Int): String {
        val s = maxOf(0, seconds)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) format("%d:%02d:%02d", h, m, sec) else format("%d:%02d", m, sec)
    }

    /** "1:45:00" style used for goals. */
    fun goalTime(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        if (h > 0) return if (s == 0) format("%d:%02d:00", h, m) else format("%d:%02d:%02d", h, m, s)
        return format("%d:%02d", m, s)
    }

    fun km(km: Double, decimals: Int = 1): String {
        if (abs(km - km.roundToInt()) < 0.05) return "${km.roundToInt()} km"
        return format("%.${decimals}f km", km)
    }

    fun minutes(min: Int): String =
        if (min >= 60) L("%@ h %@", min / 60, format("%02d", min % 60)) else L("%@ min", min)

    /** A date in the app's language: same parts, in the order and with the punctuation of the language ("12. mai", "5月12日"). */
    fun day(date: Instant, format: String = "EEEE d MMMM"): String {
        val locale = AppLanguage.locale
        val formatter = if (AppLanguage.current == "en") {
            SimpleDateFormat(format, locale)
        } else {
            DateFormat.getInstanceForSkeleton(format, locale)
        }
        return formatter.format(Date.from(date))
    }

    // Digits stay Western whatever the language, as with String(format:) on the iPhone.
    private fun format(pattern: String, vararg args: Any): String = String.format(Locale.ROOT, pattern, *args)
}
