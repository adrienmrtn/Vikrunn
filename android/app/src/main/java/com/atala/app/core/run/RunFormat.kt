package com.atala.app.core.run

import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.l10n.midSentence
import com.atala.app.core.model.StepKind
import com.atala.app.core.model.WorkoutStep
import java.util.Locale

/** Wording shared by the run screens and the voice coach (the text part of RunFormat.swift). */
object RunFormat {
    fun isFast(kind: StepKind?): Boolean = kind == StepKind.Work

    // Numbers

    /** "5.10 km" */
    fun distance(metres: Double): String = String.format(Locale.ROOT, "%.2f km", maxOf(0.0, metres) / 1000)

    /** "5.10" */
    fun kmValue(metres: Double): String = String.format(Locale.ROOT, "%.2f", maxOf(0.0, metres) / 1000)

    /** "15 min", "90 s", "800 m", "6 km" */
    fun amount(step: WorkoutStep): String {
        step.distanceM?.let { return shortDistance(it) }
        step.durationSec?.let { return shortDuration(it) }
        return ""
    }

    fun shortDistance(metres: Int): String {
        if (metres < 1000) return "$metres m"
        if (metres % 1000 == 0) return "${metres / 1000} km"
        return String.format(Locale.ROOT, "%.1f km", metres / 1000.0)
    }

    fun shortDuration(seconds: Int): String {
        if (seconds < 120 && seconds % 60 != 0) return L("%@ s", seconds)
        if (seconds % 60 == 0) return L("%@ min", seconds / 60)
        return Fmt.duration(seconds)
    }

    /** "15 minutes", "800 metres", "6.5 kilometres": for the "of …" line and the voice. */
    fun spokenAmount(step: WorkoutStep): String {
        step.distanceM?.let { return spokenDistance(it) }
        step.durationSec?.let { return spokenDuration(it) }
        return ""
    }

    fun spokenDistance(metres: Int): String {
        if (metres < 1000) return L("%@ metres", metres)
        if (metres == 1000) return L("1 kilometre")
        if (metres % 1000 == 0) return L("%@ kilometres", metres / 1000)
        return L("%@ kilometres", String.format(Locale.ROOT, "%.1f", metres / 1000.0))
    }

    fun spokenDuration(seconds: Int): String {
        val total = maxOf(0, seconds)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        val parts = mutableListOf<String>()
        if (hours > 0) parts += if (hours == 1) L("1 hour") else L("%@ hours", hours)
        if (minutes > 0) parts += if (minutes == 1) L("1 minute") else L("%@ minutes", minutes)
        if (secs > 0 || parts.isEmpty()) parts += if (secs == 1) L("1 second") else L("%@ seconds", secs)
        return parts.joinToString(" ")
    }

    /** Middle of the target range, in seconds per kilometre. */
    fun targetPace(step: WorkoutStep): Int? {
        val fast = step.paceFast
        val slow = step.paceSlow
        if (fast != null && slow != null) return (fast + slow) / 2
        return fast ?: slow
    }

    // Steps

    /** (3, 6) for the third fast step out of six. Null when the step is not a repetition. */
    fun repetition(index: Int, steps: List<WorkoutStep>): Pair<Int, Int>? {
        if (index !in steps.indices || steps[index].kind != StepKind.Work) return null
        val total = steps.count { it.kind == StepKind.Work }
        if (total <= 1) return null
        val number = steps.subList(0, index + 1).count { it.kind == StepKind.Work }
        return number to total
    }

    /** "Warm-up" or "Fast · 3 of 6". */
    fun pill(index: Int, steps: List<WorkoutStep>): String {
        if (index !in steps.indices) return L("Free run")
        val title = steps[index].kind.title
        repetition(index, steps)?.let { (number, total) -> return L("%@ · %@ of %@", title, number, total) }
        return title
    }

    /** "Next: 800 m fast", "Next: recovery 400 m", "Last step". */
    fun next(index: Int, steps: List<WorkoutStep>): String {
        val nextIndex = index + 1
        if (nextIndex !in steps.indices) return L("Last step")
        val step = steps[nextIndex]
        val quantity = amount(step)
        return when (step.kind) {
            StepKind.Work -> L("Next: %@ fast", quantity)
            else -> L("Next: %@ %@", step.kind.title.midSentence, quantity)
        }
    }

    /** Relative width of a step in the session bar. */
    fun weight(step: WorkoutStep): Float = maxOf(60, step.estimatedSeconds(fallbackPace = 360)).toFloat()

    /** Short text shown in the coach bubble when a step starts. */
    fun stepCue(index: Int, steps: List<WorkoutStep>): String {
        if (index !in steps.indices) return ""
        val step = steps[index]
        val pace = targetPace(step)
        if (step.kind == StepKind.Work && pace != null) {
            return L("%@, %@. Target %@/km.", step.kind.title, amount(step), Fmt.pace(pace))
        } else if (step.kind != StepKind.Work && step.label.isNotEmpty()) {
            return L("%@, %@. %@.", step.kind.title, amount(step), step.label)
        }
        return L("%@, %@.", step.kind.title, amount(step))
    }

    /** The same announcement, written to be read aloud. */
    fun stepSpeech(index: Int, steps: List<WorkoutStep>): String {
        if (index !in steps.indices) return ""
        val step = steps[index]
        val title = step.kind.title
        val quantity = spokenAmount(step)
        val pace = targetPace(step)
        val rep = repetition(index, steps)
        if (step.kind == StepKind.Work && pace != null) {
            if (rep != null) {
                return L("%@, %@ of %@, %@, target %@ per kilometre.", title, rep.first, rep.second, quantity, Fmt.pace(pace))
            }
            return L("%@, %@, target %@ per kilometre.", title, quantity, Fmt.pace(pace))
        } else if (step.kind != StepKind.Work && step.label.isNotEmpty()) {
            return L("%@, %@. %@.", title, quantity, step.label)
        }
        if (rep != null) return L("%@, %@ of %@, %@.", title, rep.first, rep.second, quantity)
        return L("%@, %@.", title, quantity)
    }
}
