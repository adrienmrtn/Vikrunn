package com.atala.app.core.l10n

import android.content.Context
import android.content.SharedPreferences
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** The language Atala speaks: the one picked in the app, otherwise the phone's, otherwise English. */
object AppLanguage {
    data class Language(val code: String, val name: String, val flag: String)

    /** Every language the app and the coach's recorded voice exist in, with its own name. */
    val all: List<Language> = listOf(
        Language("en", "English", "🇬🇧"), Language("fr", "Français", "🇫🇷"), Language("es", "Español", "🇪🇸"),
        Language("de", "Deutsch", "🇩🇪"), Language("it", "Italiano", "🇮🇹"),
        Language("pt-BR", "Português (Brasil)", "🇧🇷"), Language("pt-PT", "Português (Portugal)", "🇵🇹"),
        Language("nl", "Nederlands", "🇳🇱"), Language("sv", "Svenska", "🇸🇪"), Language("da", "Dansk", "🇩🇰"),
        Language("no", "Norsk", "🇳🇴"), Language("fi", "Suomi", "🇫🇮"), Language("pl", "Polski", "🇵🇱"),
        Language("cs", "Čeština", "🇨🇿"), Language("sk", "Slovenčina", "🇸🇰"), Language("hu", "Magyar", "🇭🇺"),
        Language("ro", "Română", "🇷🇴"), Language("hr", "Hrvatski", "🇭🇷"), Language("sl", "Slovenščina", "🇸🇮"),
        Language("el", "Ελληνικά", "🇬🇷"), Language("tr", "Türkçe", "🇹🇷"), Language("ru", "Русский", "🇷🇺"),
        Language("ar", "العربية", "🇸🇦"), Language("he", "עברית", "🇮🇱"), Language("ja", "日本語", "🇯🇵"),
    )
    val supported: List<String> = all.map { it.code }

    private const val KEY = "atala.language"
    private var prefs: SharedPreferences? = null
    private val _code = MutableStateFlow("en")

    /** The language in use. Observed by the root of the interface, which is rebuilt when it changes. */
    val code: StateFlow<String> = _code.asStateFlow()

    /** Read very often (every translated string), so it is kept in memory. */
    val current: String get() = _code.value

    fun init(context: Context) {
        prefs = context.getSharedPreferences("atala", Context.MODE_PRIVATE)
        refresh()
    }

    /** The language picked in the app, null while the phone's language decides. */
    var chosen: String?
        get() = prefs?.getString(KEY, null)?.takeIf { it in supported }
        set(value) {
            prefs?.edit()?.apply {
                if (value != null && value in supported) putString(KEY, value) else remove(KEY)
            }?.apply()
            refresh()
        }

    /** Picks a language and redraws the whole app in it. */
    fun choose(code: String) {
        chosen = code
    }

    /** Works the language out again: at launch, after a choice, and when the phone's languages change. */
    fun refresh() {
        val locales = LocaleList.getDefault()
        val tags = (0 until locales.size()).map { locales[it].toLanguageTag() }
        _code.value = chosen ?: resolve(tags)
    }

    /** For dates, numbers and the synthesized voice. */
    val locale: Locale get() = Locale.forLanguageTag(localeIdentifier.replace('_', '-'))

    val localeIdentifier: String
        get() = when (val code = current) {
            "en" -> "en_US"
            "pt-BR" -> "pt_BR"
            "pt-PT" -> "pt_PT"
            "no" -> "nb_NO"
            else -> code
        }

    /** The language's English name, for instructions sent to the coach service. */
    val englishName: String
        get() = mapOf(
            "en" to "English", "fr" to "French", "es" to "Spanish", "de" to "German", "it" to "Italian",
            "pt-BR" to "Brazilian Portuguese", "pt-PT" to "European Portuguese", "nl" to "Dutch", "sv" to "Swedish",
            "da" to "Danish", "no" to "Norwegian", "fi" to "Finnish", "pl" to "Polish", "cs" to "Czech", "sk" to "Slovak",
            "hu" to "Hungarian", "ro" to "Romanian", "hr" to "Croatian", "sl" to "Slovenian", "el" to "Greek",
            "tr" to "Turkish", "ru" to "Russian", "ar" to "Arabic", "he" to "Hebrew", "ja" to "Japanese",
        )[current] ?: "English"

    val isRightToLeft: Boolean get() = current == "ar" || current == "he"

    /** First supported language in a list of language tags ("fr-FR", "pt-PT", "nb-NO"). */
    fun resolve(preferred: List<String>): String {
        for (tag in preferred) {
            val parts = tag.split("-")
            val code = parts.firstOrNull()?.lowercase()?.takeIf { it.isNotEmpty() } ?: continue
            if (code == "pt") return if ("PT" in parts) "pt-PT" else "pt-BR"
            if (code == "nb" || code == "nn") return "no"
            if (code == "iw") return "he"
            if (code in supported) return code
        }
        return "en"
    }
}
