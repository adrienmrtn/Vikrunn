package com.atala.app.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.atala.app.AtalaApplication
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.VoiceSettings
import com.atala.app.core.run.RunFormat
import com.atala.app.core.run.RunTracker
import com.atala.app.core.run.RunTrackerEvent
import com.atala.app.core.run.RunTrackerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Speaks the coach's cues and keeps the last one for the coach bubble (Atala/Services/VoiceCoach.swift).
 * A coach with recorded clips (see [VoiceClip]) speaks with their own voice; the phone's synthesized
 * voice ([TextToSpeech], in the app's language) says everything else. Other apps' music is lowered
 * (transient audio focus with ducking) while the coach talks, then comes back. Never two voices at once:
 * a new cue cuts the one being said. Main thread only.
 */
class VoiceCoach(context: Context? = null) {
    private val _lastCue = MutableStateFlow("")
    /** Text of the last cue, shown in the coach bubble of the live screen. */
    val lastCue: StateFlow<String> = _lastCue.asStateFlow()

    /** The coach whose recorded voice is used. Null: the coach chosen in the app. */
    var coachId: Int? = null

    private val context: Context? = context?.applicationContext ?: VoiceContextProvider.appContext
    private val main = Handler(Looper.getMainLooper())

    // Phone voice.
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsLanguage: String? = null
    private val waitingForTts = mutableListOf<Pair<String, String>>()
    private val speaking = mutableSetOf<String>()

    // Recorded clips: the one playing, the ones waiting behind it, and the last one of each line.
    private var player: MediaPlayer? = null
    private var clipQueue = ArrayDeque<ClipSource>()
    private var clipsPlaying = false
    private val lastClipNames = ArrayDeque<String>()

    // Audio focus.
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var focusRequest: AudioFocusRequest? = null

    // Pace remarks.
    private var lastDriftCue = Long.MIN_VALUE / 2
    private var wasOffTarget = false
    /** Seconds between two pace remarks, at least. */
    private val driftInterval = 30
    /** Seconds per kilometre allowed outside the target range before the coach speaks. */
    private val driftTolerance = 8

    init {
        this.context?.let { VoicePack.prepare(it, resolvedCoachId()) }
    }

    private fun resolvedCoachId(): Int =
        coachId ?: (context as? AtalaApplication)?.store?.current?.coach?.id ?: 1

    // Cues

    /** Shows a cue in the coach bubble without speaking. */
    fun show(text: String) {
        _lastCue.value = text
    }

    /** Shows a cue and reads it aloud with the phone's voice when the voice is on. */
    fun say(speech: String, display: String, settings: VoiceSettings) {
        _lastCue.value = display
        if (!settings.enabled || speech.isBlank()) return
        // Two voices at once would be unintelligible: the newer cue wins.
        stopClips()
        speak(speech)
    }

    /** Cuts whatever is being said, then speaks. For short timed cues that must not queue up. */
    fun sayNow(speech: String, display: String, settings: VoiceSettings) {
        stopSpeech()
        say(speech, display, settings)
    }

    /**
     * Shows a cue and says it with the coach's recorded voice: the clips play one after the other.
     * When one of them has no recording on the phone, [speech] is synthesized instead.
     * [interrupt] cuts whatever is being said (short timed cues of the guided sessions).
     */
    fun say(clips: List<VoiceClip>, speech: String, display: String, settings: VoiceSettings, interrupt: Boolean = false) {
        _lastCue.value = display
        if (!settings.enabled) return
        val context = context
        val coach = resolvedCoachId()
        context?.let { VoicePack.prepare(it, coach) }
        val ids = mutableListOf<String>()
        val sources = mutableListOf<ClipSource>()
        if (context != null) {
            for (clip in clips) {
                val found = clip.source(context, coach, lastClipNames.lastOrNull { clip.matches(it) })
                if (found == null) {
                    ids.clear()
                    break
                }
                ids += found.first
                sources += found.second
            }
        }
        if (ids.isEmpty()) {
            if (interrupt) sayNow(speech, display, settings) else say(speech, display, settings)
            return
        }
        ids.forEach { lastClipNames.addLast(it) }
        while (lastClipNames.size > 12) lastClipNames.removeFirst()
        play(sources)
    }

    /** Stops talking right away (end of the run, flow closed). */
    fun stop() {
        waitingForTts.clear()
        stopSpeech()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        clipQueue.clear()
        clipsPlaying = false
        releaseFocus()
    }

    /** Stops talking and frees the phone's voice engine (the screen that owned this coach is gone). */
    fun release() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        ttsLanguage = null
    }

    // Phone voice

    private fun speak(text: String) {
        val context = context ?: return
        val id = UUID.randomUUID().toString()
        activateAudio()
        speaking += id
        val engine = tts
        if (engine == null) {
            waitingForTts += id to text
            tts = TextToSpeech(context) { status -> main.post { ttsInitialized(status) } }
            return
        }
        if (!ttsReady) {
            waitingForTts += id to text
            return
        }
        utter(engine, id, text)
    }

    private fun ttsInitialized(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            // No phone voice: the cue stays on screen only.
            waitingForTts.forEach { speaking -= it.first }
            waitingForTts.clear()
            releaseIfIdle()
            return
        }
        ttsReady = true
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                main.post { utteranceEnded(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { utteranceEnded(utteranceId) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                main.post { utteranceEnded(utteranceId) }
            }
        })
        val pending = waitingForTts.toList()
        waitingForTts.clear()
        pending.forEach { (id, text) -> utter(engine, id, text) }
    }

    private fun utter(engine: TextToSpeech, id: String, text: String) {
        val language = AppLanguage.current
        if (ttsLanguage != language) {
            ttsLanguage = language
            val locale = AppLanguage.locale
            engine.setLanguage(locale)
            bestVoice(engine)?.let { engine.voice = it }
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        if (result != TextToSpeech.SUCCESS) utteranceEnded(id)
    }

    /** The most natural voice installed for the app's language that works offline. */
    private fun bestVoice(engine: TextToSpeech): Voice? = runCatching {
        val locale = AppLanguage.locale
        val candidates = engine.voices.orEmpty().filter { voice ->
            voice.locale.language == locale.language && !voice.isNetworkConnectionRequired &&
                !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
        val regional = candidates.filter { it.locale.country == locale.country }
        (regional.ifEmpty { candidates }).maxByOrNull { it.quality }
    }.getOrNull()

    private fun stopSpeech() {
        waitingForTts.forEach { speaking -= it.first }
        waitingForTts.clear()
        if (speaking.isNotEmpty()) {
            speaking.clear()
            runCatching { tts?.stop() }
        }
    }

    private fun utteranceEnded(id: String?) {
        if (id != null) speaking -= id
        releaseIfIdle()
    }

    // Recorded clips

    /** Plays the files one after the other. A set of clips counts as one cue for the audio focus. */
    private fun play(sources: List<ClipSource>) {
        stopSpeech()
        stopClips()
        activateAudio()
        clipQueue = ArrayDeque(sources)
        clipsPlaying = true
        playNextClip()
    }

    private fun playNextClip() {
        player?.release()
        player = null
        while (clipQueue.isNotEmpty()) {
            val source = clipQueue.removeFirst()
            val next = createPlayer(source) ?: continue
            player = next
            return
        }
        // Nothing left (or nothing playable): the cue is over.
        if (clipsPlaying) {
            clipsPlaying = false
            releaseIfIdle()
        }
    }

    private fun createPlayer(source: ClipSource): MediaPlayer? {
        val context = context ?: return null
        val media = MediaPlayer()
        return try {
            media.setAudioAttributes(attributes)
            when (source) {
                is ClipSource.Downloaded -> media.setDataSource(source.file.absolutePath)
                is ClipSource.Bundled -> context.assets.openFd(source.assetPath).use {
                    media.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
            media.setOnCompletionListener { finished -> clipEnded(finished) }
            media.setOnErrorListener { finished, _, _ -> clipEnded(finished); true }
            media.prepare()
            media.start()
            media
        } catch (_: Exception) {
            media.release()
            null
        }
    }

    /** A late report from a clip that was cut must not advance the cue that replaced it. */
    private fun clipEnded(finished: MediaPlayer) {
        if (!clipsPlaying || player !== finished) return
        playNextClip()
    }

    /** Cuts the recorded clips without releasing the audio focus (something else is about to speak). */
    private fun stopClips() {
        if (!clipsPlaying) return
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        clipQueue.clear()
        clipsPlaying = false
    }

    // Audio focus

    private fun activateAudio() {
        if (focusRequest != null) return
        val manager = context?.getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        // Speaking still works without the focus; music just will not duck.
        runCatching { manager.requestAudioFocus(request) }
        focusRequest = request
    }

    private fun releaseFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { context?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request) }
    }

    private fun releaseIfIdle() {
        if (speaking.isEmpty() && waitingForTts.isEmpty() && !clipsPlaying) releaseFocus()
    }

    // Tracker events

    fun handle(event: RunTrackerEvent, tracker: RunTracker, settings: VoiceSettings) {
        when (event) {
            is RunTrackerEvent.StepStarted -> {
                wasOffTarget = false
                lastDriftCue = SystemClock.elapsedRealtime() - (driftInterval - 15) * 1000L
                if (!settings.announceSteps) return
                val index = event.index
                val speech = RunFormat.stepSpeech(index, tracker.steps)
                val display = RunFormat.stepCue(index, tracker.steps)
                val clip = tracker.steps.getOrNull(index)?.let { VoiceClip.opening(it.kind) }
                if (clip != null) say(listOf(clip), speech, display, settings) else say(speech, display, settings)
            }
            RunTrackerEvent.SessionComplete -> {
                wasOffTarget = false
                if (!settings.announceSteps) return
                say(
                    listOf(VoiceClip.Complete),
                    speech = L("Session complete. Well done. Stop whenever you are ready."),
                    display = L("Session complete. Well done. Stop whenever you're ready."),
                    settings = settings,
                )
            }
            is RunTrackerEvent.Kilometre -> {
                if (!settings.announceEveryKm) return
                announceKilometre(event.count, tracker, settings)
            }
            RunTrackerEvent.PaceTick -> {
                if (!settings.announcePace) return
                checkPace(tracker, settings)
            }
        }
    }

    private fun announceKilometre(count: Int, tracker: RunTracker, settings: VoiceSettings) {
        val snapshot = tracker.snapshot.value
        val pace = snapshot.averagePace
        val spokenTime = RunFormat.spokenDuration(snapshot.elapsed)
        var speech = if (count == 1) L("%@ kilometre. Time, %@.", count, spokenTime) else L("%@ kilometres. Time, %@.", count, spokenTime)
        var display = L("%@ km in %@.", count, Fmt.duration(snapshot.elapsed))
        if (pace > 0) {
            speech += " " + L("Average pace, %@ per kilometre.", Fmt.pace(pace))
            display += " " + L("Average %@ /km.", Fmt.pace(pace))
        }
        if (count in 1..VoiceClip.LAST_KILOMETRE) {
            // The recorded voice gives the kilometre and a word of encouragement; the time and the
            // pace stay on screen. The synthesized sentence is the fallback.
            say(listOf(VoiceClip.Km(count), VoiceClip.Cheer), speech, display, settings)
        } else {
            say(speech, display, settings)
        }
    }

    /** During fast steps, tells the runner when the pace drifts out of the target range. */
    private fun checkPace(tracker: RunTracker, settings: VoiceSettings) {
        val snapshot = tracker.snapshot.value
        val step = tracker.currentStep ?: return
        val fast = step.paceFast ?: return
        val slow = step.paceSlow ?: return
        if (snapshot.state != RunTrackerState.Running || step.kind != StepKind.Work ||
            snapshot.stepElapsed < 20 || snapshot.currentPace <= 0
        ) return
        val pace = snapshot.currentPace
        val tooFast = pace < fast - driftTolerance
        val tooSlow = pace > slow + driftTolerance
        if (tooFast || tooSlow) {
            val clock = SystemClock.elapsedRealtime()
            if (clock - lastDriftCue < driftInterval * 1000L) return
            lastDriftCue = clock
            wasOffTarget = true
            val target = Fmt.pace((fast + slow) / 2)
            if (tooFast) {
                say(
                    listOf(VoiceClip.Fast),
                    speech = L("A little too fast. Ease off towards %@ per kilometre.", target),
                    display = L("A little too fast. Ease off towards %@ /km.", target),
                    settings = settings,
                )
            } else {
                say(
                    listOf(VoiceClip.Slow),
                    speech = L("A little too slow. Pick it up towards %@ per kilometre.", target),
                    display = L("A little too slow. Pick it up towards %@ /km.", target),
                    settings = settings,
                )
            }
        } else if (wasOffTarget) {
            wasOffTarget = false
            show(L("Back on target. Hold this pace."))
        }
    }
}
