package com.atala.app.core.model

import android.icu.text.DateFormatSymbols
import com.atala.app.core.l10n.AppLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Gregorian calendar with weeks starting on Monday, in the phone's time zone (`Calendar.plan`).
 * Plan dates are instants at the start of a local day, as on the iPhone.
 */
object PlanCalendar {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun local(date: Instant): ZonedDateTime = date.atZone(zone)

    fun startOfDay(date: Instant): Instant = local(date).toLocalDate().atStartOfDay(zone).toInstant()

    /** 1 = Monday ... 7 = Sunday. */
    fun mondayIndex(date: Instant): Int = local(date).dayOfWeek.value

    fun mondayOfWeek(date: Instant): Instant {
        val day = startOfDay(date)
        return addDays(day, -(mondayIndex(day) - 1))
    }

    /** Same local time, `days` later (or earlier): a day across a clock change is still one day. */
    fun addDays(date: Instant, days: Int): Instant = local(date).plusDays(days.toLong()).toInstant()

    /** Whole days from one date to the other, like `dateComponents([.day], from:to:)`. */
    fun daysBetween(from: Instant, to: Instant): Int = ChronoUnit.DAYS.between(local(from), local(to)).toInt()

    fun isSameDay(a: Instant, b: Instant): Boolean = local(a).toLocalDate() == local(b).toLocalDate()

    fun isToday(date: Instant): Boolean = isSameDay(date, Instant.now())
}

object Weekday {
    /** English names, for text that is matched or sent to the server rather than shown. */
    val englishShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val englishLong = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** Names in the app's language, Monday first. Capitalised: these stand alone, on chips and in headers. */
    val short: List<String>
        get() = mondayFirst(DateFormatSymbols.ABBREVIATED, englishShort).map { name ->
            name.replaceFirstChar { it.uppercase() }
        }

    val long: List<String> get() = mondayFirst(DateFormatSymbols.WIDE, englishLong)

    fun short(index: Int): String = short[index.coerceIn(1, 7) - 1]
    fun long(index: Int): String = long[index.coerceIn(1, 7) - 1]
    fun englishLong(index: Int): String = englishLong[index.coerceIn(1, 7) - 1]

    /** The symbols list the days from Sunday, at indices 1 to 7. */
    private fun mondayFirst(width: Int, fallback: List<String>): List<String> {
        val symbols = DateFormatSymbols.getInstance(AppLanguage.locale)
            .getWeekdays(DateFormatSymbols.STANDALONE, width)
            .drop(1)
        if (symbols.size != 7) return fallback
        return symbols.drop(1) + symbols.first()
    }
}
