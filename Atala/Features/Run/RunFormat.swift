import SwiftUI

/// Wording and colours shared by the run screens and the voice coach.
enum RunFormat {
    // MARK: Colours

    static func isFast(_ kind: StepKind?) -> Bool { kind == .work }

    /// Background tint of the live screen.
    static func tint(_ kind: StepKind?) -> Color {
        isFast(kind) ? Theme.orangeTint : Theme.greenTint
    }

    /// Secondary text colour on the tinted background.
    static func tintedText(_ kind: StepKind?) -> Color {
        isFast(kind) ? Color(hex: 0x7A4A2E) : Color(hex: 0x44615A)
    }

    static func segmentColor(_ kind: StepKind) -> Color {
        kind == .work ? Theme.orange : Theme.greenSoft
    }

    // MARK: Numbers

    /// "5.10 km"
    static func distance(_ metres: Double) -> String {
        String(format: "%.2f km", max(0, metres) / 1000)
    }

    /// "15 min", "90 s", "800 m", "6 km"
    static func amount(_ step: WorkoutStep) -> String {
        if let metres = step.distanceM { return shortDistance(metres) }
        if let seconds = step.durationSec { return shortDuration(seconds) }
        return ""
    }

    static func shortDistance(_ metres: Int) -> String {
        if metres < 1000 { return "\(metres) m" }
        if metres % 1000 == 0 { return "\(metres / 1000) km" }
        return String(format: "%.1f km", Double(metres) / 1000)
    }

    static func shortDuration(_ seconds: Int) -> String {
        if seconds < 120 && seconds % 60 != 0 { return L("%@ s", seconds) }
        if seconds % 60 == 0 { return L("%@ min", seconds / 60) }
        return Fmt.duration(seconds)
    }

    /// "15 minutes", "800 metres", "6.5 kilometres": for the "of …" line and the voice.
    static func spokenAmount(_ step: WorkoutStep) -> String {
        if let metres = step.distanceM { return spokenDistance(metres) }
        if let seconds = step.durationSec { return spokenDuration(seconds) }
        return ""
    }

    static func spokenDistance(_ metres: Int) -> String {
        if metres < 1000 { return L("%@ metres", metres) }
        if metres == 1000 { return L("1 kilometre") }
        if metres % 1000 == 0 { return L("%@ kilometres", metres / 1000) }
        return L("%@ kilometres", String(format: "%.1f", Double(metres) / 1000))
    }

    static func spokenDuration(_ seconds: Int) -> String {
        let total = max(0, seconds)
        let hours = total / 3600, minutes = (total % 3600) / 60, secs = total % 60
        var parts: [String] = []
        if hours > 0 { parts.append(hours == 1 ? L("1 hour") : L("%@ hours", hours)) }
        if minutes > 0 { parts.append(minutes == 1 ? L("1 minute") : L("%@ minutes", minutes)) }
        if secs > 0 || parts.isEmpty { parts.append(secs == 1 ? L("1 second") : L("%@ seconds", secs)) }
        return parts.joined(separator: " ")
    }

    /// Middle of the target range, in seconds per kilometre.
    static func targetPace(_ step: WorkoutStep) -> Int? {
        if let fast = step.paceFast, let slow = step.paceSlow { return (fast + slow) / 2 }
        return step.paceFast ?? step.paceSlow
    }

    // MARK: Steps

    /// (3, 6) for the third fast step out of six. Nil when the step is not a repetition.
    static func repetition(at index: Int, in steps: [WorkoutStep]) -> (number: Int, total: Int)? {
        guard steps.indices.contains(index), steps[index].kind == .work else { return nil }
        let total = steps.filter { $0.kind == .work }.count
        guard total > 1 else { return nil }
        let number = steps[0...index].filter { $0.kind == .work }.count
        return (number, total)
    }

    /// "Warm-up" or "Fast · 3 of 6".
    static func pill(at index: Int, in steps: [WorkoutStep]) -> String {
        guard steps.indices.contains(index) else { return L("Free run") }
        let title = steps[index].kind.title
        if let rep = repetition(at: index, in: steps) { return L("%@ · %@ of %@", title, rep.number, rep.total) }
        return title
    }

    /// "Next: 800 m fast", "Next: recovery 400 m", "Last step".
    static func next(after index: Int, in steps: [WorkoutStep]) -> String {
        let nextIndex = index + 1
        guard steps.indices.contains(nextIndex) else { return L("Last step") }
        let step = steps[nextIndex]
        let quantity = amount(step)
        switch step.kind {
        case .work: return L("Next: %@ fast", quantity)
        default: return L("Next: %@ %@", step.kind.title.lowercased(), quantity)
        }
    }

    /// Relative width of a step in the session bar.
    static func weight(_ step: WorkoutStep) -> CGFloat {
        CGFloat(max(60, step.estimatedSeconds(fallbackPace: 360)))
    }

    /// Short text shown in the coach bubble when a step starts.
    static func stepCue(at index: Int, in steps: [WorkoutStep]) -> String {
        guard steps.indices.contains(index) else { return "" }
        let step = steps[index]
        if step.kind == .work, let pace = targetPace(step) {
            return L("%@, %@. Target %@/km.", step.kind.title, amount(step), Fmt.pace(pace))
        } else if step.kind != .work, !step.label.isEmpty {
            return L("%@, %@. %@.", step.kind.title, amount(step), step.label)
        }
        return L("%@, %@.", step.kind.title, amount(step))
    }

    /// The same announcement, written to be read aloud.
    static func stepSpeech(at index: Int, in steps: [WorkoutStep]) -> String {
        guard steps.indices.contains(index) else { return "" }
        let step = steps[index]
        let title = step.kind.title
        let quantity = spokenAmount(step)
        if step.kind == .work, let pace = targetPace(step) {
            if let rep = repetition(at: index, in: steps) {
                return L("%@, %@ of %@, %@, target %@ per kilometre.", title, rep.number, rep.total, quantity, Fmt.pace(pace))
            }
            return L("%@, %@, target %@ per kilometre.", title, quantity, Fmt.pace(pace))
        } else if step.kind != .work, !step.label.isEmpty {
            return L("%@, %@. %@.", title, quantity, step.label)
        }
        if let rep = repetition(at: index, in: steps) {
            return L("%@, %@ of %@, %@.", title, rep.number, rep.total, quantity)
        }
        return L("%@, %@.", title, quantity)
    }
}
