package com.atala.app.core.voice

import android.content.Context
import com.atala.app.core.model.StepKind
import java.io.File

/**
 * A recorded line of the coach, same identifiers as the iPhone (Atala/Services/VoiceClips.swift)
 * and as the files on Supabase (`coach-voice/v1/<lang>/<f|m>/<id>.mp3`). Each line has one file per
 * language and voice: the downloaded pack first (see [VoicePack]), then the English run clips shipped
 * in the assets (`coach_voice/<f|m>/<id>.mp3`).
 */
sealed interface VoiceClip {
    // Runs.
    data object Start : VoiceClip
    data object Warmup : VoiceClip
    data object Work : VoiceClip
    data object Recovery : VoiceClip
    data object Cooldown : VoiceClip
    data object Steady : VoiceClip
    /** "Three kilometres." Recorded from 1 to [LAST_KILOMETRE]. */
    data class Km(val count: Int) : VoiceClip
    data object Fast : VoiceClip
    data object Slow : VoiceClip
    data object Complete : VoiceClip
    data object Cheer : VoiceClip

    // Guided strength and stretching sessions.
    data object First : VoiceClip
    data object Next : VoiceClip
    /** The name of a catalog exercise, by its identifier ("calves", "squat"). */
    data class Exercise(val id: String) : VoiceClip
    data object Left : VoiceClip
    data object Right : VoiceClip
    data object SwitchSides : VoiceClip
    data object Rest : VoiceClip
    data object Go : VoiceClip
    /** "Set two." From 2 to 6. */
    data class SetNumber(val number: Int) : VoiceClip
    /** "Hold for thirty seconds." For the durations in [HOLDS]. */
    data class Hold(val seconds: Int) : VoiceClip
    /** "Twelve reps." For the counts in [REP_COUNTS]. */
    data class Reps(val count: Int) : VoiceClip
    data object Halfway : VoiceClip
    /** "Three." From 3 to 1. */
    data class Count(val number: Int) : VoiceClip
    data object Done : VoiceClip

    companion object {
        const val LAST_KILOMETRE = 21
        val EXERCISE_IDS = listOf("calves", "hamstrings", "quads", "hip_flexors", "glutes", "squat", "lunge", "glute_bridge", "plank")
        val SETS = 2..6
        val HOLDS = listOf(20, 30, 40, 45, 60)
        val REP_COUNTS = listOf(8, 10, 12, 15, 20)
        val COUNTS = 1..3

        /** Every identifier of a pack, for the download (95 files). */
        val allIds: List<String>
            get() {
                val lines = mutableListOf<VoiceClip>(
                    Start, Warmup, Work, Recovery, Cooldown, Steady, Fast, Slow, Complete, Cheer,
                    First, Next, Left, Right, SwitchSides, Rest, Go, Halfway, Done,
                )
                lines += (1..LAST_KILOMETRE).map { Km(it) }
                lines += EXERCISE_IDS.map { Exercise(it) }
                lines += SETS.map { SetNumber(it) }
                lines += HOLDS.map { Hold(it) }
                lines += REP_COUNTS.map { Reps(it) }
                lines += COUNTS.map { Count(it) }
                return lines.flatMap { line ->
                    if (line.variants == 0) listOf(line.stem) else (1..line.variants).map { "${line.stem}_$it" }
                }
            }

        /** The clip that opens a step of this kind, null when the step has no recorded line. */
        fun opening(kind: StepKind): VoiceClip? = when (kind) {
            StepKind.Warmup -> Warmup
            StepKind.Work -> Work
            StepKind.Recovery -> Recovery
            StepKind.Cooldown -> Cooldown
            StepKind.Steady -> Steady
            StepKind.Exercise -> null
        }
    }
}

/** Start of the identifier; the variant number follows when there are several recordings. */
internal val VoiceClip.stem: String
    get() = when (this) {
        VoiceClip.Start -> "start"
        VoiceClip.Warmup -> "warmup"
        VoiceClip.Work -> "work"
        VoiceClip.Recovery -> "recovery"
        VoiceClip.Cooldown -> "cooldown"
        VoiceClip.Steady -> "steady"
        is VoiceClip.Km -> "km_$count"
        VoiceClip.Fast -> "fast"
        VoiceClip.Slow -> "slow"
        VoiceClip.Complete -> "complete"
        VoiceClip.Cheer -> "cheer"
        VoiceClip.First -> "g_first"
        VoiceClip.Next -> "g_next"
        is VoiceClip.Exercise -> "ex_$id"
        VoiceClip.Left -> "g_left"
        VoiceClip.Right -> "g_right"
        VoiceClip.SwitchSides -> "g_switch"
        VoiceClip.Rest -> "g_rest"
        VoiceClip.Go -> "g_go"
        is VoiceClip.SetNumber -> "g_set_$number"
        is VoiceClip.Hold -> "hold_$seconds"
        is VoiceClip.Reps -> "reps_$count"
        VoiceClip.Halfway -> "g_halfway"
        is VoiceClip.Count -> "count_$number"
        VoiceClip.Done -> "g_done"
    }

/** How many recordings exist for this line (the coach picks one at random). Zero: a single one. */
internal val VoiceClip.variants: Int
    get() = when (this) {
        VoiceClip.Start, VoiceClip.Work, VoiceClip.Fast, VoiceClip.Slow, VoiceClip.Complete -> 3
        VoiceClip.Warmup, VoiceClip.Recovery, VoiceClip.Cooldown, VoiceClip.Steady -> 2
        VoiceClip.Cheer -> 15
        else -> 0
    }

/** False for a number or an exercise that was never recorded. */
internal val VoiceClip.isRecorded: Boolean
    get() = when (this) {
        is VoiceClip.Km -> count in 1..VoiceClip.LAST_KILOMETRE
        is VoiceClip.Exercise -> id in VoiceClip.EXERCISE_IDS
        is VoiceClip.SetNumber -> number in VoiceClip.SETS
        is VoiceClip.Hold -> seconds in VoiceClip.HOLDS
        is VoiceClip.Reps -> count in VoiceClip.REP_COUNTS
        is VoiceClip.Count -> number in VoiceClip.COUNTS
        else -> true
    }

/** True when [id] is a recording of this line (whatever the variant). */
internal fun VoiceClip.matches(id: String): Boolean =
    if (variants == 0) id == stem else id.startsWith(stem + "_")

/**
 * Identifier of one recording. [last] is the one played last for this line, so the same recording
 * does not come twice in a row when there are several.
 */
internal fun VoiceClip.pickId(last: String?): String {
    if (variants <= 0) return stem
    var pick = (1..variants).random()
    if (variants > 1 && "${stem}_$pick" == last) pick = pick % variants + 1
    return "${stem}_$pick"
}

/** Where a clip is on the phone: a downloaded file, or a file of the assets. */
internal sealed interface ClipSource {
    data class Downloaded(val file: File) : ClipSource
    data class Bundled(val assetPath: String) : ClipSource
}

/** The file to play for a coach, null when there is no recording of the line on the phone. */
internal fun VoiceClip.source(context: Context, coachId: Int, last: String?): Pair<String, ClipSource>? {
    if (!isRecorded) return null
    val id = pickId(last)
    VoicePack.localFile(context, id, coachId)?.let { return id to ClipSource.Downloaded(it) }
    // The English clips in the assets: the woman's voice under "f", the man's under "m".
    val path = "coach_voice/${VoicePack.voiceKey(coachId)}/$id.mp3"
    val exists = runCatching { context.assets.openFd(path).close() }.isSuccess
    return if (exists) id to ClipSource.Bundled(path) else null
}
