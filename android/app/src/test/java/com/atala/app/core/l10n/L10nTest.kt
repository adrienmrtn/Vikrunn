package com.atala.app.core.l10n

import android.app.Application
import android.icu.text.PluralRules
import android.icu.util.ULocale
import com.atala.app.testing.TestSupport
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The translations shared with the iPhone (`strings_<code>.json`), `L()`, plurals and right-to-left languages. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class L10nTest {
    @Before
    fun setUp() = TestSupport.reset()

    @After
    fun tearDown() {
        AppLanguage.chosen = null
    }

    private val placeholder = Regex("%(\\d+\\$)?@")

    private fun table(code: String): Map<String, String> {
        val text = TestSupport.context.assets.open("strings_$code.json").bufferedReader().use { it.readText() }
        return Json.decodeFromString<Map<String, String>>(text)
    }

    @Test
    fun everyTranslationLoadsWithMatchingPlaceholders() {
        val problems = mutableListOf<String>()
        for (code in AppLanguage.supported - "en") {
            val table = table(code)
            assertTrue("$code: ${table.size} entries", table.size > 1200)
            for ((key, value) in table) {
                val base = key.substringBefore('|')
                if (placeholder.findAll(base).count() != placeholder.findAll(value).count()) problems += "$code: $key -> $value"
                if ('|' in key) {
                    val category = key.substringAfter('|')
                    if (base !in table) problems += "$code: variant without its plain form: $key"
                    if (category !in listOf("one", "two", "few", "many")) problems += "$code: unknown plural form $key"
                }
                if (value.isBlank()) problems += "$code: empty translation for $key"
            }
            // L() reads the same table once the language is chosen.
            AppLanguage.choose(code)
            assertEquals(code, AppLanguage.current)
            assertEquals(table.getValue("Continue"), L("Continue"))
            assertNotEquals("$code: Continue", "Continue", L("Continue"))
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        AppLanguage.choose("en")
        assertEquals("Continue", L("Continue"))
        assertEquals("A key nobody translated", L("A key nobody translated"))
    }

    @Test
    fun placeholdersAreFilledInOrderOrByPosition() {
        assertEquals("5 min", L("%@ min", 5))
        assertEquals("Hey Léa,", L("Hey %@,", "Léa"))
        assertEquals("b then a", L10n.fill("%2\$@ then %1\$@", listOf("a", "b")))
        assertEquals("a, b, %@", L10n.fill("%@, %@, %@", listOf("a", "b")))
        AppLanguage.choose("fr")
        assertEquals("Salut Léa,", L("Hey %@,", "Léa"))
        assertEquals("Continuer", L("Continue"))
        assertEquals("3 semaines", L("%@ weeks", 3))
        assertEquals("1 semaine", L("1 week"))
        AppLanguage.choose("ja")
        assertEquals("%@種目 · %@分".replaceFirst("%@", "4").replaceFirst("%@", "12"), L("%@ exercises · %@ min", 4, 12))
    }

    @Test
    fun pluralFormsForRussianPolishAndArabic() {
        AppLanguage.choose("ru")
        val ru = table("ru")
        fun ru(n: Int, form: String?) = L10n.fill(ru[if (form == null) "%@ weeks" else "%@ weeks|$form"] ?: ru.getValue("%@ weeks"), listOf("$n"))
        assertEquals("1 неделя", L("%@ weeks", 1))
        assertEquals("2 недели", L("%@ weeks", 2))
        assertEquals("5 недель", L("%@ weeks", 5))
        assertEquals("11 недель", L("%@ weeks", 11))
        assertEquals("21 неделя", L("%@ weeks", 21))
        assertEquals("24 недели", L("%@ weeks", 24))
        assertEquals("112 недель", L("%@ weeks", 112))
        for (n in 0..130) {
            assertEquals("ru $n", ru(n, L10n.pluralCategory(n, "ru")), L("%@ weeks", n))
        }

        AppLanguage.choose("pl")
        assertEquals("2 tygodnie", L("%@ weeks", 2))
        assertEquals("5 tygodni", L("%@ weeks", 5))
        assertEquals("12 tygodni", L("%@ weeks", 12))
        assertEquals("22 tygodnie", L("%@ weeks", 22))
        // The first Int picks the form, whatever its place in the sentence.
        val pl = table("pl")
        assertEquals(
            L10n.fill(pl.getValue("%@ days ticked, %@ sessions a week|few"), listOf("3", "3")),
            L("%@ days ticked, %@ sessions a week", 3, 3),
        )

        AppLanguage.choose("ar")
        assertEquals("أسبوعين (2)", L("%@ weeks", 2))
        assertEquals("3 أسابيع", L("%@ weeks", 3))
        assertEquals("10 أسابيع", L("%@ weeks", 10))
        assertEquals("11 أسبوعا", L("%@ weeks", 11))
        assertEquals("100 أسبوع", L("%@ weeks", 100))
        // Digits stay Western in Arabic, as on the iPhone.
        assertEquals("5:30", Fmt.pace(330))
        assertEquals("1:02:03", Fmt.duration(3723))
    }

    @Test
    fun pluralRulesAgreeWithCldr() {
        val mismatches = mutableListOf<String>()
        for (code in listOf("ru", "pl", "ar", "cs", "sk", "sl", "hr", "he", "ro")) {
            val rules = PluralRules.forLocale(ULocale.forLanguageTag(code))
            for (n in 1..250) {
                val cldr = rules.select(n.toDouble()).takeIf { it != "other" && it != "zero" }
                // Czech and Slovak "many" is for fractions only.
                val app = L10n.pluralCategory(n, code)
                if (cldr != app) mismatches += "$code $n: app $app, CLDR $cldr"
            }
        }
        assertTrue(mismatches.take(30).joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun rightToLeftLanguagesAndLanguageResolution() {
        AppLanguage.choose("ar")
        assertTrue(AppLanguage.isRightToLeft)
        assertEquals("ar", AppLanguage.locale.language)
        AppLanguage.choose("he")
        assertTrue(AppLanguage.isRightToLeft)
        assertEquals("he", AppLanguage.locale.language)
        for (code in AppLanguage.supported - setOf("ar", "he")) {
            AppLanguage.choose(code)
            assertFalse(code, AppLanguage.isRightToLeft)
        }
        assertEquals("he", AppLanguage.resolve(listOf("iw-IL")))
        assertEquals("he", AppLanguage.resolve(listOf("he-IL")))
        assertEquals("no", AppLanguage.resolve(listOf("nb-NO")))
        assertEquals("pt-PT", AppLanguage.resolve(listOf("pt-PT")))
        assertEquals("pt-BR", AppLanguage.resolve(listOf("pt")))
        assertEquals("fr", AppLanguage.resolve(listOf("zh-CN", "fr-CA")))
        assertEquals("en", AppLanguage.resolve(listOf("zh-CN")))
        assertEquals("nb", AppLanguage.run { choose("no"); locale.language })
        // A title inside a sentence: lower case with the language's rules, German nouns keep their capital.
        AppLanguage.choose("tr")
        assertEquals("ıslak", "ISLAK".midSentence)
        AppLanguage.choose("de")
        assertEquals("Tempolauf", "Tempolauf".midSentence)
        // An unknown code is not kept.
        AppLanguage.choose("xx")
        assertEquals("en", AppLanguage.current)
    }

    @Test
    fun weekStripLettersTellTheDaysApart() {
        AppLanguage.choose("fr")
        assertEquals(listOf("L", "M", "M", "J", "V", "S", "D"), com.atala.app.core.model.Weekday.narrow)
        AppLanguage.choose("en")
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), com.atala.app.core.model.Weekday.narrow)
        for (code in AppLanguage.supported) {
            AppLanguage.choose(code)
            val letters = com.atala.app.core.model.Weekday.narrow
            assertEquals(code, 7, letters.size)
            assertTrue("$code: $letters", letters.all { it.isNotBlank() && it.length <= 3 })
            // At least five different letters (two T and two S in English), never one letter for all days.
            assertTrue("$code: $letters", letters.toSet().size >= 4)
        }
        // Arabic and Hebrew day names all start with the same letter: the narrow forms differ.
        AppLanguage.choose("ar")
        assertEquals(7, com.atala.app.core.model.Weekday.narrow.toSet().size)
        AppLanguage.choose("he")
        assertEquals(7, com.atala.app.core.model.Weekday.narrow.toSet().size)
    }

    /**
     * Every `L("…")` of the Kotlin code is a key of the French table, so it is translated. The keys
     * left out of strings_fr.json are those whose French is the English (the translation files drop
     * identical entries): each one is listed here, and is translated in other languages.
     */
    @Test
    fun everyKeyOfTheKotlinCodeIsTranslatedInFrench() {
        val sameInFrench = setOf(
            "%@ %@, %@", "%@ h %@", "%@ min", "%@ minutes", "%@ s", "%@ × %@ s", "%@, %@", "%@, %@.", "%@, %@. %@.",
            "1 minute", "Coach", "Date", "Direct", "Distance", "Effort", "Muscles", "OK", "Pause", "Plan", "Tempo", "h", "min", "s",
        )
        val brand = setOf("Atala Pro")
        val call = Regex("""\bL\(\s*"((?:[^"\\]|\\.)*)"""")
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("sources found from ${File(".").absolutePath}", sources.size > 100)
        val keys = sources.flatMap { file -> call.findAll(file.readText()).map { it.groupValues[1] } }
            .map { it.replace("\\\"", "\"").replace("\\$", "$").replace("\\n", "\n").replace("\\\\", "\\") }
            .toSet()
        assertTrue(keys.size > 1000)
        val french = table("fr")
        val others = (AppLanguage.supported - setOf("en", "fr")).map(::table)
        val missing = keys.filter { it !in french && it !in sameInFrench && it !in brand && it.any(Char::isLetter) }
        assertTrue("Kotlin keys missing from strings_fr.json:\n" + missing.sorted().joinToString("\n"), missing.isEmpty())
        // The identical ones are real keys: other languages translate them.
        sameInFrench.forEach { key -> assertTrue(key, others.any { key in it }) }
        // And no key uses a string template: it would never match a translation.
        assertTrue(keys.none { Regex("(?<!\\\\)\\$[a-zA-Z{]").containsMatchIn(it) })
    }
}
