package com.atala.app.core.exercise

import com.atala.app.core.l10n.L

enum class GuidedExerciseKind {
    Stretch, Strength;

    val title: String get() = if (this == Stretch) L("Stretching") else L("Strength")
}

/** How the two frames of an exercise alternate (seconds). */
data class GuidedTempo(val startHold: Double, val fade: Double, val deepHold: Double)

/** Everything the app knows about one exercise: defaults, wording and images. */
data class ExerciseInfo(
    val id: String,
    /** English name ("Hamstrings", "Squats"). Used in saved step labels, which are matched and parsed in English. */
    val baseName: String,
    val kind: GuidedExerciseKind,
    /** True when the exercise is done on one side, then the other. */
    val perSide: Boolean,
    /** Default hold in seconds (per side). Used when `reps` is null. */
    val holdSec: Int,
    /** Default repetitions (per side). Null for a timed hold. */
    val reps: Int?,
    val sets: Int,
    /** Rest between two sets, in seconds. */
    val restSec: Int,
    /** Seconds one repetition takes at a calm tempo. */
    val repSec: Int,
    val summary: String,
    val steps: List<String>,
    val cues: List<String>,
    val mistake: String,
    val muscles: String,
) {
    /** Short name used in lists and announcements, in the app's language. */
    val name: String get() = L(baseName)

    /** Coach 1's pictures; the other coaches have their own under "exercise_c<N>_..." (see `AtalaImages.exercise`). */
    val imageStart: String get() = "exercise_${id}_1"
    val imageEnd: String get() = "exercise_${id}_2"

    val tempo: GuidedTempo
        get() {
            if (kind == GuidedExerciseKind.Stretch) return GuidedTempo(1.2, 1.2, 5.0)
            if (reps == null) return GuidedTempo(1.5, 0.8, 3.0)
            // One full cycle lasts `repSec` seconds, so the picture follows the rep counter.
            val cycle = maxOf(2, repSec).toDouble()
            return GuidedTempo(cycle * 0.1, cycle * 0.35, cycle * 0.2)
        }
}

/** The nine guided exercises of the plan. */
object ExerciseCatalog {
    val safetyLine: String
        get() = L("Move gently and stop if anything hurts. You should feel effort or a soft pull, never a sharp pain.")

    val stretchIds = listOf("calves", "hamstrings", "quads", "hip_flexors", "glutes")
    val strengthIds = listOf("squat", "lunge", "glute_bridge", "plank")

    private fun stretch(id: String, baseName: String, summary: String, steps: List<String>, cues: List<String>,
                        mistake: String, muscles: String) =
        ExerciseInfo(id, baseName, GuidedExerciseKind.Stretch, perSide = true, holdSec = 45, reps = null, sets = 1,
            restSec = 0, repSec = 3, summary = summary, steps = steps, cues = cues, mistake = mistake, muscles = muscles)

    private fun strength(id: String, baseName: String, perSide: Boolean, reps: Int?, summary: String,
                         steps: List<String>, cues: List<String>, mistake: String, muscles: String) =
        ExerciseInfo(id, baseName, GuidedExerciseKind.Strength, perSide = perSide, holdSec = 30, reps = reps, sets = 3,
            restSec = 30, repSec = 3, summary = summary, steps = steps, cues = cues, mistake = mistake, muscles = muscles)

    val all: List<ExerciseInfo>
        get() = listOf(
            stretch(
                "calves", "Calves",
                summary = L("Hands on a wall, one leg back, heel down."),
                steps = listOf(
                    L("Stand facing a wall and place both hands on it."),
                    L("Step one foot back, toes pointing straight ahead."),
                    L("Bend your front knee and lean in until the back calf pulls gently."),
                ),
                cues = listOf(
                    L("Keep your back heel on the ground."),
                    L("Back leg straight, hips facing the wall."),
                    L("Breathe slowly and stay still."),
                ),
                mistake = L("Letting the back heel lift or the back foot turn outwards."),
                muscles = L("Calves"),
            ),
            stretch(
                "hamstrings", "Hamstrings",
                summary = L("One heel in front, hips back, long back."),
                steps = listOf(
                    L("Stand tall and place one heel in front of you, toes up."),
                    L("Bend the back knee slightly and push your hips back."),
                    L("Lean forward from the hips until the back of the front thigh pulls gently."),
                ),
                cues = listOf(
                    L("Keep your back long, chest open."),
                    L("Front leg straight but not locked."),
                    L("Let the stretch settle, no bouncing."),
                ),
                mistake = L("Rounding your back to reach further down."),
                muscles = L("Back of the thighs"),
            ),
            stretch(
                "quads", "Quadriceps",
                summary = L("Standing, heel towards your glutes."),
                steps = listOf(
                    L("Stand tall, a hand on a wall or a chair if you like."),
                    L("Bend one knee and hold your ankle behind you."),
                    L("Bring your heel towards your glutes until the front of the thigh pulls gently."),
                ),
                cues = listOf(
                    L("Keep your knees close together."),
                    L("Stand tall, tuck your hips under slightly."),
                    L("Relax your shoulders and breathe."),
                ),
                mistake = L("Arching your lower back or letting the knee drift out to the side."),
                muscles = L("Front of the thighs"),
            ),
            stretch(
                "hip_flexors", "Hip flexors",
                summary = L("Half-kneeling, hips gently forward."),
                steps = listOf(
                    L("Kneel on one knee, the other foot flat in front of you."),
                    L("Keep your chest tall and tuck your hips under."),
                    L("Ease your hips forward until the front of the back hip pulls gently."),
                ),
                cues = listOf(
                    L("Chest tall, eyes forward."),
                    L("Squeeze the glute of your back leg."),
                    L("Front knee stays above your ankle."),
                ),
                mistake = L("Leaning forward and arching your lower back instead of moving the hips."),
                muscles = L("Front of the hips"),
            ),
            stretch(
                "glutes", "Glutes",
                summary = L("On your back, one ankle over the other knee."),
                steps = listOf(
                    L("Lie on your back with both knees bent."),
                    L("Cross one ankle over the opposite knee."),
                    L("Pull the lower leg towards your chest until the glute pulls gently."),
                ),
                cues = listOf(
                    L("Keep your head and shoulders on the floor."),
                    L("Let the crossed knee open outwards."),
                    L("Slow breaths, relax into it."),
                ),
                mistake = L("Lifting your head and tensing your neck to pull harder."),
                muscles = L("Glutes and outer hips"),
            ),
            strength(
                "squat", "Squats", perSide = false, reps = 12,
                summary = L("Sit back and down, then stand tall."),
                steps = listOf(
                    L("Stand with your feet a little wider than your hips."),
                    L("Push your hips back and bend your knees, as if sitting on a chair."),
                    L("Press through your feet to stand back up."),
                ),
                cues = listOf(
                    L("Keep your heels on the ground."),
                    L("Knees follow the line of your toes."),
                    L("Chest proud, eyes forward."),
                ),
                mistake = L("Letting your knees fall inwards or your heels lift."),
                muscles = L("Thighs and glutes"),
            ),
            strength(
                "lunge", "Lunges", perSide = true, reps = 10,
                summary = L("A long step forward, both knees bend."),
                steps = listOf(
                    L("Stand tall, feet hip-width apart."),
                    L("Take a long step forward and lower your back knee towards the floor."),
                    L("Push through your front foot to come back up."),
                ),
                cues = listOf(
                    L("Front knee stays above your ankle."),
                    L("Keep your chest tall."),
                    L("Lower slowly, stay in control."),
                ),
                mistake = L("Taking too short a step, so the front knee travels past the toes."),
                muscles = L("Thighs and glutes"),
            ),
            strength(
                "glute_bridge", "Glute bridge", perSide = false, reps = 15,
                summary = L("On your back, lift your hips, lower slowly."),
                steps = listOf(
                    L("Lie on your back, knees bent, feet flat and hip-width apart."),
                    L("Press through your heels and lift your hips."),
                    L("Pause at the top, then lower slowly."),
                ),
                cues = listOf(
                    L("Squeeze your glutes at the top."),
                    L("Shoulders, hips and knees in one line."),
                    L("Keep your ribs down."),
                ),
                mistake = L("Pushing too high and arching your lower back."),
                muscles = L("Glutes and back of the thighs"),
            ),
            strength(
                "plank", "Plank", perSide = false, reps = null,
                summary = L("On your forearms, body in one straight line."),
                steps = listOf(
                    L("Place your forearms on the floor, elbows under your shoulders."),
                    L("Step your feet back and lift your knees."),
                    L("Hold a straight line from head to heels."),
                ),
                cues = listOf(
                    L("Brace your tummy, squeeze your glutes."),
                    L("Hips level, neither high nor sagging."),
                    L("Keep breathing."),
                ),
                mistake = L("Letting your hips sag or holding your breath."),
                muscles = L("Core and shoulders"),
            ),
        )

    fun info(id: String?): ExerciseInfo? = id?.let { wanted -> all.firstOrNull { it.id == wanted } }

    /** Finds the exercise named in a step label. Used for plans saved before steps carried an exercise id. */
    fun match(label: String): ExerciseInfo? {
        val text = label.lowercase()
        val keywords = listOf(
            "glute_bridge" to listOf("bridge"),
            "squat" to listOf("squat"),
            "lunge" to listOf("lunge"),
            "plank" to listOf("plank"),
            "calves" to listOf("calves", "calf stretch"),
            "hamstrings" to listOf("hamstring"),
            "quads" to listOf("quad"),
            "hip_flexors" to listOf("hip flexor", "hips"),
            "glutes" to listOf("glutes", "glute stretch"),
        )
        val id = keywords.firstOrNull { (_, words) -> words.any { it in text } }?.first
        return info(id)
    }

    /** A neutral description for an exercise the catalog does not know (older plans). */
    fun generic(label: String, kind: GuidedExerciseKind, durationSec: Int?): ExerciseInfo {
        val name = label.split(",").first().trim()
        val slug = name.lowercase().replace(" ", "_")
        val stretch = kind == GuidedExerciseKind.Stretch
        return ExerciseInfo(
            id = "custom_$slug",
            baseName = name.ifEmpty { if (stretch) "Stretching" else "Strength" },
            kind = kind, perSide = false,
            holdSec = (durationSec ?: 60).coerceIn(10, 300), reps = null, sets = 1, restSec = 30, repSec = 3,
            summary = L("Take your time and stay in control."),
            steps = listOf(
                L("Get into position calmly."),
                L("Move slowly, through a range that feels comfortable."),
            ),
            cues = listOf(
                L("Breathe steadily."),
                L("Quality before quantity."),
            ),
            mistake = L("Rushing through it."),
            muscles = if (stretch) L("Legs and hips") else L("Legs and core"),
        )
    }
}
