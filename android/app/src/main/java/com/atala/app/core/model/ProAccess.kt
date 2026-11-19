package com.atala.app.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What is free and what needs Atala Pro (a subscription, its free trial included).
 *
 * Free, for good: the whole plan and its sessions, runs with GPS, the run summary, guided strength
 * and stretching, streaks.
 * Pro: talking to the coach, the Progress tab, the coach's voice, the detailed run figures.
 */
object ProAccess {
    private val _isPro = MutableStateFlow(false)

    /** Kept up to date by the purchase service (RevenueCat), the only one that calls [setPro]. */
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    fun setPro(value: Boolean) {
        _isPro.value = value
    }

    /** The coach's voice is part of Atala Pro: without it the cues are shown but not spoken. */
    fun voice(settings: VoiceSettings): VoiceSettings =
        if (isPro.value) settings else settings.copy(enabled = false)

    /** Talking to the coach is part of Atala Pro. */
    fun canMessageCoach(): Boolean = isPro.value
}
