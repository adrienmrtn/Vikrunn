import Foundation

/// A recorded line of the coach. The clips live in the app bundle, one set per coach, named
/// `voice_c<coach>_<family>_<variant>.mp3` (`voice_c<coach>_km_<n>.mp3` for the kilometres).
/// The wording of every clip is listed in `coach_voice_phrases.json`, next to the files.
enum VoiceClip: Equatable {
    case start, warmup, work, recovery, cooldown, steady
    /// "Three kilometres." Recorded from 1 to `VoiceClip.lastKilometre`.
    case km(Int)
    case fast, slow, complete, cheer

    static let lastKilometre = 21

    private var family: String {
        switch self {
        case .start: return "start"
        case .warmup: return "warmup"
        case .work: return "work"
        case .recovery: return "recovery"
        case .cooldown: return "cooldown"
        case .steady: return "steady"
        case .km: return "km"
        case .fast: return "fast"
        case .slow: return "slow"
        case .complete: return "complete"
        case .cheer: return "cheer"
        }
    }

    /// How many recordings exist for this line (the coach picks one at random).
    private var variants: Int {
        switch self {
        case .start, .work, .fast, .slow, .complete: return 3
        case .warmup, .recovery, .cooldown, .steady: return 2
        case .cheer: return 15
        case .km: return 1
        }
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

    /// Name of the bundled file for a coach, without its extension. `avoiding` is the name played
    /// last, so the same recording does not come twice in a row when there are several.
    func resourceName(coachID: Int, avoiding last: String?) -> String {
        let prefix = "voice_c\(coachID)_\(family)_"
        if case .km(let count) = self { return prefix + "\(count)" }
        var pick = Int.random(in: 1...variants)
        if variants > 1, prefix + "\(pick)" == last {
            pick = pick % variants + 1
        }
        return prefix + "\(pick)"
    }

    /// The file in the app bundle, nil when this coach has no recording for the line.
    func url(coachID: Int, avoiding last: String?) -> (name: String, url: URL)? {
        if case .km(let count) = self, !(1...Self.lastKilometre).contains(count) { return nil }
        let name = resourceName(coachID: coachID, avoiding: last)
        guard let url = Bundle.main.url(forResource: name, withExtension: "mp3") else { return nil }
        return (name, url)
    }
}
