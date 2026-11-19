import Foundation

enum GuidedExerciseKind: String, Equatable {
    case stretch, strength

    var title: String { self == .stretch ? L("Stretching") : L("Strength") }
    /// SF Symbol used when an exercise image is missing.
    var symbol: String { self == .stretch ? "figure.cooldown" : "figure.strengthtraining.functional" }
}

/// How the two frames of an exercise alternate (seconds).
struct GuidedTempo: Equatable {
    var startHold: Double
    var fade: Double
    var deepHold: Double
}

/// Everything the app knows about one exercise: defaults, wording and images.
struct ExerciseInfo: Identifiable, Equatable {
    let id: String
    /// English name ("Hamstrings", "Squats"). Used in saved step labels, which are matched and parsed in English.
    let baseName: String
    let kind: GuidedExerciseKind
    /// True when the exercise is done on one side, then the other.
    let perSide: Bool
    /// Default hold in seconds (per side). Used when `reps` is nil.
    let holdSec: Int
    /// Default repetitions (per side). Nil for a timed hold.
    let reps: Int?
    let sets: Int
    /// Rest between two sets, in seconds.
    let restSec: Int
    /// Seconds one repetition takes at a calm tempo.
    let repSec: Int
    let summary: String
    let steps: [String]
    let cues: [String]
    let mistake: String
    let muscles: String

    /// Short name used in lists and announcements, in the app's language.
    var name: String { L(baseName) }

    var imageStart: String { "exercise_\(id)_1" }
    var imageEnd: String { "exercise_\(id)_2" }

    var tempo: GuidedTempo {
        if kind == .stretch { return GuidedTempo(startHold: 1.2, fade: 1.2, deepHold: 5) }
        if reps == nil { return GuidedTempo(startHold: 1.5, fade: 0.8, deepHold: 3) }
        // One full cycle lasts `repSec` seconds, so the picture follows the rep counter.
        let cycle = Double(max(2, repSec))
        return GuidedTempo(startHold: cycle * 0.1, fade: cycle * 0.35, deepHold: cycle * 0.2)
    }
}

/// The nine guided exercises of the plan.
enum ExerciseCatalog {
    static var safetyLine: String { L("Move gently and stop if anything hurts. You should feel effort or a soft pull, never a sharp pain.") }

    static let stretchIDs = ["calves", "hamstrings", "quads", "hip_flexors", "glutes"]
    static let strengthIDs = ["squat", "lunge", "glute_bridge", "plank"]

    static var all: [ExerciseInfo] { [
        ExerciseInfo(
            id: "calves", baseName: "Calves", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: L("Hands on a wall, one leg back, heel down."),
            steps: [
                L("Stand facing a wall and place both hands on it."),
                L("Step one foot back, toes pointing straight ahead."),
                L("Bend your front knee and lean in until the back calf pulls gently."),
            ],
            cues: [
                L("Keep your back heel on the ground."),
                L("Back leg straight, hips facing the wall."),
                L("Breathe slowly and stay still."),
            ],
            mistake: L("Letting the back heel lift or the back foot turn outwards."),
            muscles: L("Calves")),
        ExerciseInfo(
            id: "hamstrings", baseName: "Hamstrings", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: L("One heel in front, hips back, long back."),
            steps: [
                L("Stand tall and place one heel in front of you, toes up."),
                L("Bend the back knee slightly and push your hips back."),
                L("Lean forward from the hips until the back of the front thigh pulls gently."),
            ],
            cues: [
                L("Keep your back long, chest open."),
                L("Front leg straight but not locked."),
                L("Let the stretch settle, no bouncing."),
            ],
            mistake: L("Rounding your back to reach further down."),
            muscles: L("Back of the thighs")),
        ExerciseInfo(
            id: "quads", baseName: "Quadriceps", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: L("Standing, heel towards your glutes."),
            steps: [
                L("Stand tall, a hand on a wall or a chair if you like."),
                L("Bend one knee and hold your ankle behind you."),
                L("Bring your heel towards your glutes until the front of the thigh pulls gently."),
            ],
            cues: [
                L("Keep your knees close together."),
                L("Stand tall, tuck your hips under slightly."),
                L("Relax your shoulders and breathe."),
            ],
            mistake: L("Arching your lower back or letting the knee drift out to the side."),
            muscles: L("Front of the thighs")),
        ExerciseInfo(
            id: "hip_flexors", baseName: "Hip flexors", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: L("Half-kneeling, hips gently forward."),
            steps: [
                L("Kneel on one knee, the other foot flat in front of you."),
                L("Keep your chest tall and tuck your hips under."),
                L("Ease your hips forward until the front of the back hip pulls gently."),
            ],
            cues: [
                L("Chest tall, eyes forward."),
                L("Squeeze the glute of your back leg."),
                L("Front knee stays above your ankle."),
            ],
            mistake: L("Leaning forward and arching your lower back instead of moving the hips."),
            muscles: L("Front of the hips")),
        ExerciseInfo(
            id: "glutes", baseName: "Glutes", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: L("On your back, one ankle over the other knee."),
            steps: [
                L("Lie on your back with both knees bent."),
                L("Cross one ankle over the opposite knee."),
                L("Pull the lower leg towards your chest until the glute pulls gently."),
            ],
            cues: [
                L("Keep your head and shoulders on the floor."),
                L("Let the crossed knee open outwards."),
                L("Slow breaths, relax into it."),
            ],
            mistake: L("Lifting your head and tensing your neck to pull harder."),
            muscles: L("Glutes and outer hips")),
        ExerciseInfo(
            id: "squat", baseName: "Squats", kind: .strength, perSide: false,
            holdSec: 30, reps: 12, sets: 3, restSec: 30, repSec: 3,
            summary: L("Sit back and down, then stand tall."),
            steps: [
                L("Stand with your feet a little wider than your hips."),
                L("Push your hips back and bend your knees, as if sitting on a chair."),
                L("Press through your feet to stand back up."),
            ],
            cues: [
                L("Keep your heels on the ground."),
                L("Knees follow the line of your toes."),
                L("Chest proud, eyes forward."),
            ],
            mistake: L("Letting your knees fall inwards or your heels lift."),
            muscles: L("Thighs and glutes")),
        ExerciseInfo(
            id: "lunge", baseName: "Lunges", kind: .strength, perSide: true,
            holdSec: 30, reps: 10, sets: 3, restSec: 30, repSec: 3,
            summary: L("A long step forward, both knees bend."),
            steps: [
                L("Stand tall, feet hip-width apart."),
                L("Take a long step forward and lower your back knee towards the floor."),
                L("Push through your front foot to come back up."),
            ],
            cues: [
                L("Front knee stays above your ankle."),
                L("Keep your chest tall."),
                L("Lower slowly, stay in control."),
            ],
            mistake: L("Taking too short a step, so the front knee travels past the toes."),
            muscles: L("Thighs and glutes")),
        ExerciseInfo(
            id: "glute_bridge", baseName: "Glute bridge", kind: .strength, perSide: false,
            holdSec: 30, reps: 15, sets: 3, restSec: 30, repSec: 3,
            summary: L("On your back, lift your hips, lower slowly."),
            steps: [
                L("Lie on your back, knees bent, feet flat and hip-width apart."),
                L("Press through your heels and lift your hips."),
                L("Pause at the top, then lower slowly."),
            ],
            cues: [
                L("Squeeze your glutes at the top."),
                L("Shoulders, hips and knees in one line."),
                L("Keep your ribs down."),
            ],
            mistake: L("Pushing too high and arching your lower back."),
            muscles: L("Glutes and back of the thighs")),
        ExerciseInfo(
            id: "plank", baseName: "Plank", kind: .strength, perSide: false,
            holdSec: 30, reps: nil, sets: 3, restSec: 30, repSec: 3,
            summary: L("On your forearms, body in one straight line."),
            steps: [
                L("Place your forearms on the floor, elbows under your shoulders."),
                L("Step your feet back and lift your knees."),
                L("Hold a straight line from head to heels."),
            ],
            cues: [
                L("Brace your tummy, squeeze your glutes."),
                L("Hips level, neither high nor sagging."),
                L("Keep breathing."),
            ],
            mistake: L("Letting your hips sag or holding your breath."),
            muscles: L("Core and shoulders")),
    ] }

    static func info(id: String?) -> ExerciseInfo? {
        guard let id else { return nil }
        return all.first { $0.id == id }
    }

    /// Finds the exercise named in a step label. Used for plans saved before steps carried an exercise id.
    static func match(label: String) -> ExerciseInfo? {
        let text = label.lowercased()
        let keywords: [(String, [String])] = [
            ("glute_bridge", ["bridge"]),
            ("squat", ["squat"]),
            ("lunge", ["lunge"]),
            ("plank", ["plank"]),
            ("calves", ["calves", "calf stretch"]),
            ("hamstrings", ["hamstring"]),
            ("quads", ["quad"]),
            ("hip_flexors", ["hip flexor", "hips"]),
            ("glutes", ["glutes", "glute stretch"]),
        ]
        for (id, words) in keywords {
            if words.contains(where: { text.contains($0) }) {
                return info(id: id)
            }
        }
        return nil
    }

    /// A neutral description for an exercise the catalog does not know (older plans).
    static func generic(label: String, kind: GuidedExerciseKind, durationSec: Int?) -> ExerciseInfo {
        let head = label.split(separator: ",").first.map(String.init) ?? label
        let name = head.trimmingCharacters(in: .whitespaces)
        let slug = name.lowercased().replacingOccurrences(of: " ", with: "_")
        return ExerciseInfo(
            id: "custom_\(slug)", baseName: name.isEmpty ? (kind == .stretch ? "Stretching" : "Strength") : name, kind: kind, perSide: false,
            holdSec: min(max(durationSec ?? 60, 10), 300), reps: nil, sets: 1, restSec: 30, repSec: 3,
            summary: L("Take your time and stay in control."),
            steps: [
                L("Get into position calmly."),
                L("Move slowly, through a range that feels comfortable."),
            ],
            cues: [
                L("Breathe steadily."),
                L("Quality before quantity."),
            ],
            mistake: L("Rushing through it."),
            muscles: kind == .stretch ? L("Legs and hips") : L("Legs and core"))
    }
}
