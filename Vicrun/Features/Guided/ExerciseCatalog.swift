import Foundation

enum GuidedExerciseKind: String, Equatable {
    case stretch, strength

    var title: String { self == .stretch ? "Stretching" : "Strength" }
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
    /// Short name used in lists and announcements ("Hamstrings", "Squats").
    let name: String
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
    static let safetyLine = "Move gently and stop if anything hurts. You should feel effort or a soft pull, never a sharp pain."

    static let stretchIDs = ["calves", "hamstrings", "quads", "hip_flexors", "glutes"]
    static let strengthIDs = ["squat", "lunge", "glute_bridge", "plank"]

    static let all: [ExerciseInfo] = [
        ExerciseInfo(
            id: "calves", name: "Calves", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: "Hands on a wall, one leg back, heel down.",
            steps: [
                "Stand facing a wall and place both hands on it.",
                "Step one foot back, toes pointing straight ahead.",
                "Bend your front knee and lean in until the back calf pulls gently.",
            ],
            cues: [
                "Keep your back heel on the ground.",
                "Back leg straight, hips facing the wall.",
                "Breathe slowly and stay still.",
            ],
            mistake: "Letting the back heel lift or the back foot turn outwards.",
            muscles: "Calves"),
        ExerciseInfo(
            id: "hamstrings", name: "Hamstrings", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: "One heel in front, hips back, long back.",
            steps: [
                "Stand tall and place one heel in front of you, toes up.",
                "Bend the back knee slightly and push your hips back.",
                "Lean forward from the hips until the back of the front thigh pulls gently.",
            ],
            cues: [
                "Keep your back long, chest open.",
                "Front leg straight but not locked.",
                "Let the stretch settle, no bouncing.",
            ],
            mistake: "Rounding your back to reach further down.",
            muscles: "Back of the thighs"),
        ExerciseInfo(
            id: "quads", name: "Quadriceps", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: "Standing, heel towards your glutes.",
            steps: [
                "Stand tall, a hand on a wall or a chair if you like.",
                "Bend one knee and hold your ankle behind you.",
                "Bring your heel towards your glutes until the front of the thigh pulls gently.",
            ],
            cues: [
                "Keep your knees close together.",
                "Stand tall, tuck your hips under slightly.",
                "Relax your shoulders and breathe.",
            ],
            mistake: "Arching your lower back or letting the knee drift out to the side.",
            muscles: "Front of the thighs"),
        ExerciseInfo(
            id: "hip_flexors", name: "Hip flexors", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: "Half-kneeling, hips gently forward.",
            steps: [
                "Kneel on one knee, the other foot flat in front of you.",
                "Keep your chest tall and tuck your hips under.",
                "Ease your hips forward until the front of the back hip pulls gently.",
            ],
            cues: [
                "Chest tall, eyes forward.",
                "Squeeze the glute of your back leg.",
                "Front knee stays above your ankle.",
            ],
            mistake: "Leaning forward and arching your lower back instead of moving the hips.",
            muscles: "Front of the hips"),
        ExerciseInfo(
            id: "glutes", name: "Glutes", kind: .stretch, perSide: true,
            holdSec: 45, reps: nil, sets: 1, restSec: 0, repSec: 3,
            summary: "On your back, one ankle over the other knee.",
            steps: [
                "Lie on your back with both knees bent.",
                "Cross one ankle over the opposite knee.",
                "Pull the lower leg towards your chest until the glute pulls gently.",
            ],
            cues: [
                "Keep your head and shoulders on the floor.",
                "Let the crossed knee open outwards.",
                "Slow breaths, relax into it.",
            ],
            mistake: "Lifting your head and tensing your neck to pull harder.",
            muscles: "Glutes and outer hips"),
        ExerciseInfo(
            id: "squat", name: "Squats", kind: .strength, perSide: false,
            holdSec: 30, reps: 12, sets: 3, restSec: 30, repSec: 3,
            summary: "Sit back and down, then stand tall.",
            steps: [
                "Stand with your feet a little wider than your hips.",
                "Push your hips back and bend your knees, as if sitting on a chair.",
                "Press through your feet to stand back up.",
            ],
            cues: [
                "Keep your heels on the ground.",
                "Knees follow the line of your toes.",
                "Chest proud, eyes forward.",
            ],
            mistake: "Letting your knees fall inwards or your heels lift.",
            muscles: "Thighs and glutes"),
        ExerciseInfo(
            id: "lunge", name: "Lunges", kind: .strength, perSide: true,
            holdSec: 30, reps: 10, sets: 3, restSec: 30, repSec: 3,
            summary: "A long step forward, both knees bend.",
            steps: [
                "Stand tall, feet hip-width apart.",
                "Take a long step forward and lower your back knee towards the floor.",
                "Push through your front foot to come back up.",
            ],
            cues: [
                "Front knee stays above your ankle.",
                "Keep your chest tall.",
                "Lower slowly, stay in control.",
            ],
            mistake: "Taking too short a step, so the front knee travels past the toes.",
            muscles: "Thighs and glutes"),
        ExerciseInfo(
            id: "glute_bridge", name: "Glute bridge", kind: .strength, perSide: false,
            holdSec: 30, reps: 15, sets: 3, restSec: 30, repSec: 3,
            summary: "On your back, lift your hips, lower slowly.",
            steps: [
                "Lie on your back, knees bent, feet flat and hip-width apart.",
                "Press through your heels and lift your hips.",
                "Pause at the top, then lower slowly.",
            ],
            cues: [
                "Squeeze your glutes at the top.",
                "Shoulders, hips and knees in one line.",
                "Keep your ribs down.",
            ],
            mistake: "Pushing too high and arching your lower back.",
            muscles: "Glutes and back of the thighs"),
        ExerciseInfo(
            id: "plank", name: "Plank", kind: .strength, perSide: false,
            holdSec: 30, reps: nil, sets: 3, restSec: 30, repSec: 3,
            summary: "On your forearms, body in one straight line.",
            steps: [
                "Place your forearms on the floor, elbows under your shoulders.",
                "Step your feet back and lift your knees.",
                "Hold a straight line from head to heels.",
            ],
            cues: [
                "Brace your tummy, squeeze your glutes.",
                "Hips level, neither high nor sagging.",
                "Keep breathing.",
            ],
            mistake: "Letting your hips sag or holding your breath.",
            muscles: "Core and shoulders"),
    ]

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
            id: "custom_\(slug)", name: name.isEmpty ? kind.title : name, kind: kind, perSide: false,
            holdSec: min(max(durationSec ?? 60, 10), 300), reps: nil, sets: 1, restSec: 30, repSec: 3,
            summary: "Take your time and stay in control.",
            steps: [
                "Get into position calmly.",
                "Move slowly, through a range that feels comfortable.",
            ],
            cues: [
                "Breathe steadily.",
                "Quality before quantity.",
            ],
            mistake: "Rushing through it.",
            muscles: kind == .stretch ? "Legs and hips" : "Legs and core")
    }
}
