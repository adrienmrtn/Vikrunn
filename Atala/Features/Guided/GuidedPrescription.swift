import Foundation

/// Fixed pauses of a guided session, in seconds.
enum GuidedTiming {
    static let getReady = 5
    static let switchSide = 5
}

/// What a step label says about sets, reps and holds ("3 × 12", "3 × 40 s", "45 s each side").
struct GuidedLabelAmount: Equatable {
    var sets: Int?
    var reps: Int?
    var holdSec: Int?

    static func parse(_ label: String) -> GuidedLabelAmount {
        guard let comma = label.firstIndex(of: ",") else { return GuidedLabelAmount() }
        let tail = String(label[label.index(after: comma)...]).lowercased()
        let tokens: [String] = tail.split(separator: " ").map { String($0) }
        var sets: Int?
        var amount: Int?
        var unitIndex = 0
        if let cross = tokens.firstIndex(where: { $0 == "×" || $0 == "x" }),
           cross > 0, cross + 1 < tokens.count,
           let first = Int(tokens[cross - 1]), let second = Int(tokens[cross + 1]) {
            sets = first
            amount = second
            unitIndex = cross + 2
        } else if let first = tokens.first, let value = Int(first) {
            amount = value
            unitIndex = 1
        }
        guard let amount else { return GuidedLabelAmount() }
        let unit = unitIndex < tokens.count ? tokens[unitIndex] : ""
        if unit == "s" || unit == "sec" || unit == "seconds" {
            return GuidedLabelAmount(sets: sets, reps: nil, holdSec: amount)
        }
        if unit == "min" || unit == "minute" || unit == "minutes" {
            return GuidedLabelAmount(sets: sets, reps: nil, holdSec: amount * 60)
        }
        return GuidedLabelAmount(sets: sets, reps: amount, holdSec: nil)
    }
}

/// One exercise as it is prescribed in a session: the catalog entry plus sets, reps or hold, and rest.
struct GuidedPrescription: Equatable {
    var info: ExerciseInfo
    var sets: Int
    /// Repetitions per set (per side). Nil for a timed hold.
    var reps: Int?
    /// Hold per set (per side), used when `reps` is nil.
    var holdSec: Int
    var restSec: Int

    init(info: ExerciseInfo, sets: Int? = nil, reps: Int? = nil, holdSec: Int? = nil, restSec: Int? = nil) {
        self.info = info
        self.sets = min(max(sets ?? info.sets, 1), 6)
        if let reps {
            self.reps = min(max(reps, 1), 50)
        } else if holdSec == nil {
            self.reps = info.reps
        } else {
            self.reps = nil
        }
        self.holdSec = min(max(holdSec ?? info.holdSec, 5), 300)
        self.restSec = max(0, restSec ?? info.restSec)
    }

    var perSide: Bool { info.perSide }
    var isTimed: Bool { reps == nil }
    private var sides: Int { perSide ? 2 : 1 }

    /// Seconds of work for one side of one set.
    var workSec: Int {
        if let reps { return reps * max(1, info.repSec) }
        return holdSec
    }

    /// Whole exercise, with the get-ready countdown, side switches and rests. Matches the guided session.
    var totalSec: Int {
        let oneSet = sides * workSec + (sides - 1) * GuidedTiming.switchSide
        return GuidedTiming.getReady + sets * oneSet + (sets - 1) * restSec
    }

    /// "3 × 12", "3 × 40 s", "45 s each side", in the app's language.
    var amountText: String {
        if let reps {
            if sets > 1 { return perSide ? L("%@ × %@ each side", sets, reps) : "\(sets) × \(reps)" }
            return perSide ? L("%@ reps each side", reps) : L("%@ reps", reps)
        }
        if sets > 1 { return perSide ? L("%@ × %@ s each side", sets, holdSec) : L("%@ × %@ s", sets, holdSec) }
        return perSide ? L("%@ s each side", holdSec) : L("%@ s", holdSec)
    }

    /// The same in English, for the saved step label (`GuidedLabelAmount.parse` reads it back).
    private var baseAmountText: String {
        var text: String
        if let reps {
            text = sets > 1 ? "\(sets) × \(reps)" : "\(reps) reps"
        } else {
            text = sets > 1 ? "\(sets) × \(holdSec) s" : "\(holdSec) s"
        }
        if perSide { text += " each side" }
        return text
    }

    var label: String { "\(info.baseName), \(baseAmountText)" }

    /// The step saved in the plan for this exercise.
    var workoutStep: WorkoutStep {
        WorkoutStep(kind: .exercise, label: label, durationSec: totalSec, exerciseID: info.id)
    }

    // MARK: Sessions of the plan

    static func totalMinutes(_ exercises: [GuidedPrescription]) -> Int {
        let seconds = exercises.reduce(0) { $0 + $1.totalSec }
        return max(1, Int((Double(seconds) / 60).rounded()))
    }

    /// The five stretches, each side. Holds get a little longer as the plan advances.
    static func stretchingSession(phase: PlanPhase) -> [GuidedPrescription] {
        let hold: Int
        switch phase {
        case .base: hold = 40
        case .build: hold = 50
        case .specific: hold = 60
        case .taper: hold = 45
        }
        return ExerciseCatalog.stretchIDs.compactMap { (id: String) -> GuidedPrescription? in
            guard let info = ExerciseCatalog.info(id: id) else { return nil }
            return GuidedPrescription(info: info, sets: 1, holdSec: hold, restSec: 0)
        }
    }

    /// Squats, lunges, glute bridge and plank. One more set after the base weeks, then a few more reps.
    static func strengthSession(phase: PlanPhase) -> [GuidedPrescription] {
        let sets: Int
        let amounts: [String: Int]
        switch phase {
        case .base, .taper:
            sets = 2
            amounts = ["squat": 12, "lunge": 8, "glute_bridge": 12, "plank": 30]
        case .build:
            sets = 3
            amounts = ["squat": 12, "lunge": 10, "glute_bridge": 15, "plank": 40]
        case .specific:
            sets = 3
            amounts = ["squat": 15, "lunge": 12, "glute_bridge": 18, "plank": 45]
        }
        return ExerciseCatalog.strengthIDs.compactMap { (id: String) -> GuidedPrescription? in
            guard let info = ExerciseCatalog.info(id: id) else { return nil }
            let amount = amounts[id]
            if info.reps == nil {
                return GuidedPrescription(info: info, sets: sets, holdSec: amount, restSec: 30)
            }
            return GuidedPrescription(info: info, sets: sets, reps: amount, restSec: 30)
        }
    }

    // MARK: Reading a saved session

    /// The exercises of a strength or stretching session, read from its steps.
    /// Steps without an exercise id (older plans) are matched by their label.
    static func session(for workout: Workout) -> [GuidedPrescription] {
        let kind: GuidedExerciseKind = workout.kind == .strength ? .strength : .stretch
        var result: [GuidedPrescription] = []
        for block in workout.blocks {
            for _ in 0..<max(1, block.repeats) {
                for step in block.steps where step.kind == .exercise {
                    result.append(prescription(for: step, kind: kind))
                }
            }
        }
        if result.isEmpty {
            // A session with no exercise steps (for example a run turned into stretching by the coach).
            result = kind == .strength ? strengthSession(phase: .base) : stretchingSession(phase: .base)
        }
        return result
    }

    static func prescription(for step: WorkoutStep, kind: GuidedExerciseKind) -> GuidedPrescription {
        let parsed = GuidedLabelAmount.parse(step.label)
        if let info = ExerciseCatalog.info(id: step.exerciseID) ?? ExerciseCatalog.match(label: step.label) {
            return GuidedPrescription(info: info, sets: parsed.sets, reps: parsed.reps, holdSec: parsed.holdSec)
        }
        let info = ExerciseCatalog.generic(label: step.label, kind: kind, durationSec: step.durationSec)
        return GuidedPrescription(info: info, sets: parsed.sets, reps: parsed.reps, holdSec: parsed.holdSec)
    }
}
