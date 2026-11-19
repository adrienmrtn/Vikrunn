package com.atala.app.core.l10n

import android.content.res.AssetManager
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Translations. The English sentence is the key: `L("Continue")` gives it in the app's language,
 * and the English itself when a translation is missing. One file per language ships in the assets,
 * `strings_<code>.json` (an object, English sentence to translation), shared with the iPhone app.
 */
object L10n {
    private var assets: AssetManager? = null
    private var loaded: String? = null
    private var table: Map<String, String> = emptyMap()

    fun init(assets: AssetManager) {
        this.assets = assets
    }

    fun string(key: String): String {
        val language = AppLanguage.current
        if (language == "en") return key
        return synchronized(this) { table(language)[key] } ?: key
    }

    /**
     * The sentence for a count. Languages with several plural forms can add variants next to a
     * translation, under the same key followed by `|one`, `|two`, `|few` or `|many`
     * ("%@ weeks|few"); the plain key is the form used when there is no variant.
     */
    fun string(key: String, count: Int): String {
        val language = AppLanguage.current
        if (language == "en") return key
        val plain = string(key)
        val category = pluralCategory(count, language) ?: return plain
        return synchronized(this) { table(language)["$key|$category"] } ?: plain
    }

    private fun table(language: String): Map<String, String> {
        if (loaded != language) {
            table = load(language)
            loaded = language
        }
        return table
    }

    private fun load(language: String): Map<String, String> = runCatching {
        val text = assets!!.open("strings_$language.json").bufferedReader().use { it.readText() }
        Json.decodeFromString<Map<String, String>>(text)
    }.getOrDefault(emptyMap())

    /** The plural form a whole number takes, null for the general one. */
    fun pluralCategory(count: Int, language: String): String? {
        val n = abs(count)
        val tens = n % 10
        val hundreds = n % 100
        return when (language) {
            "ru", "hr" -> when {
                tens == 1 && hundreds != 11 -> "one"
                tens in 2..4 && hundreds !in 12..14 -> "few"
                else -> if (language == "ru") "many" else null
            }
            "pl" -> when {
                n == 1 -> "one"
                tens in 2..4 && hundreds !in 12..14 -> "few"
                else -> "many"
            }
            "cs", "sk" -> when {
                n == 1 -> "one"
                n in 2..4 -> "few"
                else -> null
            }
            "sl" -> when (hundreds) {
                1 -> "one"
                2 -> "two"
                3, 4 -> "few"
                else -> null
            }
            "ar" -> when {
                n == 1 -> "one"
                n == 2 -> "two"
                hundreds in 3..10 -> "few"
                hundreds in 11..99 -> "many"
                else -> null
            }
            "he" -> when (n) {
                1 -> "one"
                2 -> "two"
                else -> null
            }
            "ro" -> when {
                n == 1 -> "one"
                n == 0 || hundreds in 1..19 -> "few"
                else -> null
            }
            else -> if (n == 1) "one" else null
        }
    }

    /**
     * Puts the values in the sentence: each `%@` in turn, or `%1$@`, `%2$@` when a translation
     * needs them in another order.
     */
    fun fill(format: String, values: List<String>): String {
        var result = format
        values.forEachIndexed { index, value ->
            result = result.replace("%${index + 1}\$@", value)
        }
        for (value in values) {
            val range = result.indexOf("%@")
            if (range < 0) break
            result = result.replaceRange(range, range + 2, value)
        }
        return result
    }
}

/** The sentence in the app's language. */
fun L(key: String): String = L10n.string(key)

/**
 * The sentence in the app's language, with values in place of each `%@`.
 * When one of the values is a whole number, the sentence takes the plural form for it.
 */
fun L(key: String, vararg values: Any): String {
    val count = values.firstOrNull { it is Int } as Int?
    val format = if (count != null) L10n.string(key, count) else L10n.string(key)
    return L10n.fill(format, values.map { it.toString() })
}

/**
 * A translated title placed inside a sentence: lower case with the language's own rules
 * ("your tempo run", Turkish dotted letters), except in German, where nouns keep their capital.
 */
val String.midSentence: String
    get() = if (AppLanguage.current == "de") this else lowercase(AppLanguage.locale)
