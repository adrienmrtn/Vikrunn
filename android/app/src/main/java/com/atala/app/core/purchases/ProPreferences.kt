package com.atala.app.core.purchases

import android.content.Context
import android.content.SharedPreferences

/**
 * Small settings of Atala Pro kept on this phone only (`UserDefaults` on the iPhone): the
 * placeholder subscription, the last known trial state, the gift schedule, the welcome flag.
 * Works in memory until [init] is called (previews), so callers never have to check.
 */
internal object ProPreferences {
    private var prefs: SharedPreferences? = null
    private val memory = mutableMapOf<String, Any>()

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("atala.pro", Context.MODE_PRIVATE)
    }

    fun bool(key: String): Boolean = prefs?.getBoolean(key, false) ?: (memory[key] as? Boolean ?: false)

    fun setBool(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply() ?: memory.put(key, value)
    }

    fun int(key: String): Int = prefs?.getInt(key, 0) ?: (memory[key] as? Int ?: 0)

    fun setInt(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply() ?: memory.put(key, value)
    }

    /** Seconds since 1970, 0 when never set. */
    fun seconds(key: String): Long = prefs?.getLong(key, 0L) ?: (memory[key] as? Long ?: 0L)

    fun setSeconds(key: String, value: Long) {
        prefs?.edit()?.putLong(key, value)?.apply() ?: memory.put(key, value)
    }

    fun contains(key: String): Boolean = prefs?.contains(key) ?: memory.containsKey(key)
}

/** Remembers whether the "Welcome to Atala Pro" moment was already played for this subscription. */
object ProWelcomeState {
    private const val KEY = "atala.pro.welcomed"

    var wasPlayed: Boolean
        get() = ProPreferences.bool(KEY)
        set(value) = ProPreferences.setBool(KEY, value)
}

/**
 * The example prices shown while the store has no products (`OnboardingPaywallOffer` on the
 * iPhone): the Google Play products do not exist yet, so the paywall and the gift show these, with
 * a notice that nothing is charged, and their buttons stand in for a purchase.
 */
object PlaceholderOffer {
    const val TRIAL_DAYS = 3
    /** The yearly plan, per month and in full. */
    const val MONTHLY_PRICE = "€4.17"
    const val YEARLY_PRICE = "€49.99"
    /** The monthly plan. */
    const val MONTH_PRICE = "€9.99"
    const val YEARLY_SAVING = 58
    /** The welcome gift: yearly at half price, per week and in full. */
    const val GIFT_WEEKLY_PRICE = "€0.48"
    const val GIFT_YEARLY_PRICE = "€24.99"
}
