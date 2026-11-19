import Foundation

/// A recorded line of the coach. Each line has an identifier ("start_2", "km_3", "ex_calves") and
/// one file per language and voice: downloaded packs first (see `VoicePack`), then the English
/// clips shipped in the app bundle as `voice_c<coach>_<id>.mp3` (run lines only).
/// The English wording of the bundled clips is listed in `coach_voice_phrases.json`.
enum VoiceClip: Equatable {
    // Runs.
    case start, warmup, work, recovery, cooldown, steady
    /// "Three kilometres." Recorded from 1 to `VoiceClip.lastKilometre`.
    case km(Int)
    case fast, slow, complete, cheer

    // Guided strength and stretching sessions.
    case first, next
    /// The name of a catalog exercise, by its identifier ("calves", "squat").
    case exercise(String)
    case left, right, switchSides, rest, go
    /// "Set two." From 2 to 6.
    case set(Int)
    /// "Hold for thirty seconds." For the durations in `VoiceClip.holds`.
    case hold(Int)
    /// "Twelve reps." For the counts in `VoiceClip.repCounts`.
    case reps(Int)
    case halfway
    /// "Three." From 3 to 1.
    case count(Int)
    case done

    static let lastKilometre = 21
    static let exerciseIDs = ["calves", "hamstrings", "quads", "hip_flexors", "glutes",
                              "squat", "lunge", "glute_bridge", "plank"]
    static let sets = 2...6
    static let holds = [20, 30, 40, 45, 60]
    static let repCounts = [8, 10, 12, 15, 20]
    static let counts = 1...3

    /// Start of the identifier; the variant number follows when there are several recordings.
    private var stem: String {
        switch self {
        case .start: return "start"
        case .warmup: return "warmup"
        case .work: return "work"
        case .recovery: return "recovery"
        case .cooldown: return "cooldown"
        case .steady: return "steady"
        case .km(let count): return "km_\(count)"
        case .fast: return "fast"
        case .slow: return "slow"
        case .complete: return "complete"
        case .cheer: return "cheer"
        case .first: return "g_first"
        case .next: return "g_next"
        case .exercise(let id): return "ex_" + id
        case .left: return "g_left"
        case .right: return "g_right"
        case .switchSides: return "g_switch"
        case .rest: return "g_rest"
        case .go: return "g_go"
        case .set(let number): return "g_set_\(number)"
        case .hold(let seconds): return "hold_\(seconds)"
        case .reps(let count): return "reps_\(count)"
        case .halfway: return "g_halfway"
        case .count(let number): return "count_\(number)"
        case .done: return "g_done"
        }
    }

    /// How many recordings exist for this line (the coach picks one at random). Zero: a single one.
    private var variants: Int {
        switch self {
        case .start, .work, .fast, .slow, .complete: return 3
        case .warmup, .recovery, .cooldown, .steady: return 2
        case .cheer: return 15
        default: return 0
        }
    }

    /// False for a number or an exercise that was never recorded.
    private var isRecorded: Bool {
        switch self {
        case .km(let count): return (1...Self.lastKilometre).contains(count)
        case .exercise(let id): return Self.exerciseIDs.contains(id)
        case .set(let number): return Self.sets.contains(number)
        case .hold(let seconds): return Self.holds.contains(seconds)
        case .reps(let count): return Self.repCounts.contains(count)
        case .count(let number): return Self.counts.contains(number)
        default: return true
        }
    }

    /// Every identifier of a pack, for the download.
    static var allIDs: [String] {
        var lines: [VoiceClip] = [.start, .warmup, .work, .recovery, .cooldown, .steady,
                                  .fast, .slow, .complete, .cheer,
                                  .first, .next, .left, .right, .switchSides, .rest, .go, .halfway, .done]
        lines += (1...lastKilometre).map { VoiceClip.km($0) }
        lines += exerciseIDs.map { VoiceClip.exercise($0) }
        lines += sets.map { VoiceClip.set($0) }
        lines += holds.map { VoiceClip.hold($0) }
        lines += repCounts.map { VoiceClip.reps($0) }
        lines += counts.map { VoiceClip.count($0) }
        var ids: [String] = []
        for line in lines {
            if line.variants == 0 {
                ids.append(line.stem)
            } else {
                ids += (1...line.variants).map { "\(line.stem)_\($0)" }
            }
        }
        return ids
    }

    /// The clip that opens a step of this kind, nil when the step has no recorded line.
    static func opening(_ kind: StepKind) -> VoiceClip? {
        switch kind {
        case .warmup: return .warmup
        case .work: return .work
        case .recovery: return .recovery
        case .cooldown: return .cooldown
        case .steady: return .steady
        case .exercise: return nil
        }
    }

    /// True when `id` is a recording of this line (whatever the variant).
    func matches(_ id: String) -> Bool {
        variants == 0 ? id == stem : id.hasPrefix(stem + "_")
    }

    /// Identifier of one recording. `last` is the one played last for this line, so the same
    /// recording does not come twice in a row when there are several.
    private func pickID(avoiding last: String?) -> String {
        guard variants > 0 else { return stem }
        var pick = Int.random(in: 1...variants)
        if variants > 1, "\(stem)_\(pick)" == last {
            pick = pick % variants + 1
        }
        return "\(stem)_\(pick)"
    }

    /// The file to play for a coach, nil when there is no recording of the line on the phone.
    @MainActor
    func url(coachID: Int, avoiding last: String?) -> (id: String, url: URL)? {
        guard isRecorded else { return nil }
        let id = pickID(avoiding: last)
        if let url = VoicePack.localURL(id: id, coachID: coachID) { return (id, url) }
        // The clips in the bundle: the woman's voice is filed under coach 1, the man's under coach 4.
        let bundled = "voice_c\(Coach.maleIDs.contains(coachID) ? 4 : 1)_\(id)"
        guard let url = Bundle.main.url(forResource: bundled, withExtension: "mp3") else { return nil }
        return (id, url)
    }
}
