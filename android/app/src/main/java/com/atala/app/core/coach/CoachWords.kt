package com.atala.app.core.coach

import android.content.res.AssetManager
import com.atala.app.core.l10n.AppLanguage
import kotlinx.serialization.json.Json

/**
 * The words the coach built on the phone recognises in a message, in the app's language
 * (CoachWords.swift). They add to the English lists in the code. One file per language ships in
 * the assets, `coach_words_<code>.json`: an object of lists, all in lower case.
 */
object CoachWords {
    private var assets: AssetManager? = null
    private var loaded: String? = null
    private var table: Map<String, List<String>> = emptyMap()

    /**
     * True while reading a message that is known to be in English (a suggested prompt mapped back
     * to its English text): the English lists then apply whatever the app's language.
     */
    @Volatile
    var readsEnglish = false

    fun init(assets: AssetManager) {
        this.assets = assets
    }

    /** The list with this name, empty in English or when the language has none. */
    fun list(name: String): List<String> {
        val language = AppLanguage.current
        if (language == "en" || readsEnglish) return emptyList()
        return synchronized(this) {
            if (loaded != language) {
                table = load(language)
                loaded = language
            }
            table[name].orEmpty()
        }
    }

    /**
     * The words to look for: the app's language when it has a list, English otherwise. English
     * words are left out in other languages, where they collide with ordinary words.
     */
    fun pick(english: List<String>, name: String): List<String> = list(name).ifEmpty { english }

    /** An English-only list, used while the app is in English. */
    fun english(words: List<String>): List<String> =
        if (AppLanguage.current == "en" || readsEnglish) words else emptyList()

    private fun load(language: String): Map<String, List<String>> = runCatching {
        val text = assets!!.open("coach_words_$language.json").bufferedReader().use { it.readText() }
        Json.decodeFromString<Map<String, List<String>>>(text)
    }.getOrDefault(emptyMap())
}
