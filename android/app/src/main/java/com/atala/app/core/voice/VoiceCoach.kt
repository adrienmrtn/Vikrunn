package com.atala.app.core.voice

import com.atala.app.core.model.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A recorded line of the coach, same identifiers as the iPhone (Atala/Services/VoiceClips.swift)
 * and as the files on Supabase (`coach-voice/v1/<lang>/<f|m>/<id>.mp3`).
 */
sealed interface VoiceClip {
    data object Start : VoiceClip
    data object Warmup : VoiceClip
    data object Work : VoiceClip
    data object Recovery : VoiceClip
    data object Cooldown : VoiceClip
    data object Steady : VoiceClip
    data class Km(val count: Int) : VoiceClip
    data object Fast : VoiceClip
    data object Slow : VoiceClip
    data object Complete : VoiceClip
    data object Cheer : VoiceClip
    data object First : VoiceClip
    data object Next : VoiceClip
    data class Exercise(val id: String) : VoiceClip
    data object Left : VoiceClip
    data object Right : VoiceClip
    data object SwitchSides : VoiceClip
    data object Rest : VoiceClip
    data object Go : VoiceClip
    data class SetNumber(val number: Int) : VoiceClip
    data class Hold(val seconds: Int) : VoiceClip
    data class Reps(val count: Int) : VoiceClip
    data object Halfway : VoiceClip
    data class Count(val number: Int) : VoiceClip
    data object Done : VoiceClip
}

/**
 * Speaks the coach's cues (recorded clips in the app's language, the phone's voice otherwise) and
 * keeps the last cue for the coach bubble. The run owner builds it; this placeholder only shows text.
 */
class VoiceCoach {
    private val _lastCue = MutableStateFlow("")
    val lastCue: StateFlow<String> = _lastCue.asStateFlow()

    fun show(text: String) {
        _lastCue.value = text
    }

    /** Says [clips] in the coach's recorded voice, or [speech] with the phone's voice when one is missing. */
    fun say(clips: List<VoiceClip>, speech: String, display: String, settings: VoiceSettings, interrupt: Boolean = false) {
        _lastCue.value = display
    }

    /** Says [speech] with the phone's voice. */
    fun say(speech: String, display: String, settings: VoiceSettings) {
        _lastCue.value = display
    }

    fun stop() {}
}

/** Downloads the coach's recorded voice for the app's language. The run owner builds it. */
object VoicePack {
    fun prepare(context: android.content.Context, coachId: Int) {}
}
