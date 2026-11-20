package com.atala.app.core.voice

import android.content.Context
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.model.Coach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * The coach's recorded voice in the app's language (Atala/Services/VoicePack.swift). One pack per
 * language and voice is kept on Supabase (public bucket `coach-voice`); the app downloads the one it
 * needs, once, into its files directory and plays from the phone afterwards. Until a pack is complete
 * the coach falls back to the English clips shipped in the assets, then to the phone's synthesized voice.
 */
object VoicePack {
    private const val VERSION = "v1"
    private const val BASE = "https://kjmfbogagvknyzprppdu.supabase.co/storage/v1/object/public/coach-voice/"
    /** Empty file written in a pack's folder once every clip is there. */
    private const val MARKER = ".complete"

    private val ready: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val running: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The two recorded voices: "m" for the men, "f" for the women. */
    fun voiceKey(coachId: Int): String = if (coachId in Coach.maleIds) "m" else "f"

    /** Downloads what is missing from the pack of this coach, in the background. Safe to call often. */
    fun prepare(context: Context, coachId: Int) {
        val app = context.applicationContext
        val voice = voiceKey(coachId)
        val language = AppLanguage.current
        val key = "$language/$voice"
        if (isReady(app, language, voice)) return
        if (!running.add(key)) return
        val folder = folder(app, language, voice)
        val ids = VoiceClip.allIds
        scope.launch {
            try {
                if (fetch(ids, URL(BASE + VERSION + "/" + key + "/"), folder)) ready.add(key)
            } finally {
                running.remove(key)
            }
        }
    }

    /** A downloaded clip, null while the pack of this language and voice is not complete. */
    fun localFile(context: Context, id: String, coachId: Int): File? {
        val voice = voiceKey(coachId)
        val language = AppLanguage.current
        if (!isReady(context, language, voice)) return null
        return File(folder(context, language, voice), "$id.mp3").takeIf { it.exists() }
    }

    private fun isReady(context: Context, language: String, voice: String): Boolean {
        val key = "$language/$voice"
        if (key in ready) return true
        if (!File(folder(context, language, voice), MARKER).exists()) return false
        ready.add(key)
        return true
    }

    private fun folder(context: Context, language: String, voice: String): File =
        File(context.applicationContext.filesDir, "CoachVoice/$VERSION/$language/$voice")

    /** True when every clip of the pack is on the phone at the end. */
    private suspend fun fetch(ids: List<String>, base: URL, folder: File): Boolean {
        if (!folder.exists() && !folder.mkdirs()) return false
        val missing = ids.filter { !File(folder, "$it.mp3").exists() }
        // A few downloads at a time: the clips are small, the pack is about a megabyte.
        val gate = Semaphore(6)
        val results = coroutineScope {
            missing.map { id -> async(Dispatchers.IO) { gate.withPermit { fetchOne(id, base, folder) } } }.awaitAll()
        }
        if (results.any { !it }) return false
        return runCatching { File(folder, MARKER).createNewFile() || File(folder, MARKER).exists() }.getOrDefault(false)
    }

    private fun fetchOne(id: String, base: URL, folder: File): Boolean {
        val name = "$id.mp3"
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(base, name).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            if (connection.responseCode != 200) return false
            val data = connection.inputStream.use { it.readBytes() }
            if (data.size <= 500) return false
            val temp = File(folder, "$name.part")
            temp.writeBytes(data)
            temp.renameTo(File(folder, name))
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
