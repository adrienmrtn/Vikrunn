package com.atala.app.core.voice

import android.app.Application
import com.atala.app.core.account.SupabaseConfig
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.model.Coach
import com.atala.app.core.model.ProAccess
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.VoiceSettings
import com.atala.app.testing.TestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URL

/** The coach's recorded voice (ids, packs on the server, files on the phone) and what Atala Pro unlocks. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VoiceAndProTest {
    @Before
    fun setUp() = TestSupport.reset()

    @After
    fun tearDown() {
        ProAccess.setPro(false)
        AppLanguage.chosen = null
    }

    /** The bucket's listing (see the header of the resource file). */
    private val server: Pair<List<String>, List<String>> by lazy {
        val lines = javaClass.classLoader!!.getResource("voice_server_files.txt")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val languages = lines.first().removePrefix("languages: ").split(" ")
        languages to lines.drop(1)
    }

    @Test
    fun clipIdsAreTheFilesOnTheServer() {
        val ids = VoiceClip.allIds
        assertEquals(95, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        val (languages, files) = server
        assertEquals(files.sorted(), ids.map { "$it.mp3" }.sorted())
        // One pack per language of the app.
        assertEquals(AppLanguage.supported.sorted(), languages.sorted())
    }

    @Test
    fun packUrlsFollowTheBucketLayout() {
        fun constant(name: String) = VoicePack::class.java.getDeclaredField(name).apply { isAccessible = true }.get(null) as String
        val base = constant("BASE")
        val version = constant("VERSION")
        assertEquals("${SupabaseConfig.URL_STRING}/storage/v1/object/public/coach-voice/", base)
        assertEquals("v1", version)
        // As VoicePack.prepare and fetchOne build it: the pack's folder, then the file.
        fun url(language: String, voice: String, id: String) = URL(URL("$base$version/$language/$voice/"), "$id.mp3")
        assertEquals(
            "https://kjmfbogagvknyzprppdu.supabase.co/storage/v1/object/public/coach-voice/v1/pt-BR/m/km_12.mp3",
            url("pt-BR", "m", "km_12").toString(),
        )
        val (languages, files) = server
        for (language in AppLanguage.supported) {
            for (voice in listOf("f", "m")) {
                for (id in VoiceClip.allIds) {
                    val path = url(language, voice, id).path.removePrefix("/storage/v1/object/public/coach-voice/")
                    assertEquals("v1/$language/$voice/$id.mp3", path)
                    assertTrue(language in languages && "$id.mp3" in files)
                }
            }
        }
        assertEquals("m", VoicePack.voiceKey(2))
        assertEquals("m", VoicePack.voiceKey(4))
        assertEquals("m", VoicePack.voiceKey(6))
        assertEquals("f", VoicePack.voiceKey(1))
        assertEquals("f", VoicePack.voiceKey(3))
        assertEquals("f", VoicePack.voiceKey(5))
        assertEquals(Coach.maleIds, (1..6).filter { VoicePack.voiceKey(it) == "m" }.toSet())
    }

    @Test
    fun downloadedPackComesFirstThenTheEnglishClipsOfTheApp() {
        val context = TestSupport.context
        AppLanguage.choose("fr")
        // A complete French pack for the men's voice, with one clip on the phone.
        val folder = File(context.filesDir, "CoachVoice/v1/fr/m")
        File(folder, "km_3.mp3").writeBytes(ByteArray(2000))
        assertEquals(File(folder, "km_3.mp3"), VoicePack.localFile(context, "km_3", coachId = 2))
        assertNull(VoicePack.localFile(context, "km_4", coachId = 2))
        assertNull(VoicePack.localFile(context, "km_3", coachId = 1))
        val downloaded = VoiceClip.Km(3).source(context, coachId = 2, last = null)
        assertEquals("km_3" to ClipSource.Downloaded(File(folder, "km_3.mp3")), downloaded)
        // Not downloaded: the English recording shipped in the app.
        assertEquals("km_4" to ClipSource.Bundled("coach_voice/m/km_4.mp3"), VoiceClip.Km(4).source(context, coachId = 2, last = null))
        assertEquals("km_4" to ClipSource.Bundled("coach_voice/f/km_4.mp3"), VoiceClip.Km(4).source(context, coachId = 3, last = null))
        // Never recorded: null, the phone's voice says it.
        assertNull(VoiceClip.Km(22).source(context, coachId = 2, last = null))
        assertNull(VoiceClip.Exercise("burpees").source(context, coachId = 2, last = null))
    }

    @Test
    fun bundledRunClipsExistForBothVoices() {
        val assets = TestSupport.context.assets
        val ids = VoiceClip.allIds.toSet()
        val run = mutableListOf<VoiceClip>(
            VoiceClip.Start, VoiceClip.Warmup, VoiceClip.Work, VoiceClip.Recovery, VoiceClip.Cooldown, VoiceClip.Steady,
            VoiceClip.Fast, VoiceClip.Slow, VoiceClip.Complete, VoiceClip.Cheer,
        ) + (1..VoiceClip.LAST_KILOMETRE).map { VoiceClip.Km(it) }
        for (voice in listOf("f", "m")) {
            val files = assets.list("coach_voice/$voice")!!.toList()
            assertTrue(files.all { it.removeSuffix(".mp3") in ids })
            for (clip in run) {
                val expected = if (clip.variants == 0) listOf(clip.stem) else (1..clip.variants).map { "${clip.stem}_$it" }
                expected.forEach { assertTrue("$voice/$it", "$it.mp3" in files) }
            }
        }
    }

    @Test
    fun variantsAreNeverRepeatedTwiceInARow() {
        var last: String? = null
        repeat(300) {
            val id = VoiceClip.Cheer.pickId(last)
            assertNotEquals(last, id)
            assertTrue(VoiceClip.Cheer.matches(id))
            last = id
        }
        assertEquals("km_1", VoiceClip.Km(1).pickId("km_1"))
        assertFalse(VoiceClip.Km(1).matches("km_11"))
        assertTrue(VoiceClip.Work.matches("work_2"))
        assertFalse(VoiceClip.Work.matches("warmup_2"))
        assertEquals(VoiceClip.Work, VoiceClip.opening(StepKind.Work))
        assertNull(VoiceClip.opening(StepKind.Exercise))
    }

    @Test
    fun proAccessGates() {
        val settings = VoiceSettings()
        // Free: the cues are shown, not spoken; no messages to the coach.
        ProAccess.setPro(false)
        assertFalse(ProAccess.voice(settings).enabled)
        assertEquals(settings.copy(enabled = false), ProAccess.voice(settings))
        assertFalse(ProAccess.canMessageCoach())
        // Pro: the runner's own settings apply.
        ProAccess.setPro(true)
        assertEquals(settings, ProAccess.voice(settings))
        assertEquals(settings.copy(enabled = false), ProAccess.voice(settings.copy(enabled = false)))
        assertTrue(ProAccess.canMessageCoach())
        // The voice coach says nothing for a free user, and shows the cue.
        val coach = VoiceCoach(TestSupport.context)
        ProAccess.setPro(false)
        coach.say(listOf(VoiceClip.Start), "Off you go.", "Off you go.", ProAccess.voice(settings))
        assertEquals("Off you go.", coach.lastCue.value)
        coach.release()
        assertNotNull(coach)
    }
}
