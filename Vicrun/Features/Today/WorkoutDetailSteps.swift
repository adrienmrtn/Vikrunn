import SwiftUI

/// Step-by-step breakdown of a session, built from its blocks.
struct WorkoutDetailSteps: View {
    let workout: Workout

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(workout.kind.isRun ? "Step by step" : "Exercises")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .padding(.bottom, 4)
            if workout.blocks.isEmpty {
                Text("Run as you feel, with no set structure.")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.muted)
                    .padding(.vertical, 10)
            }
            ForEach(workout.blocks) { block in
                if block.repeats > 1 {
                    WorkoutDetailRepeatBlock(block: block)
                } else {
                    ForEach(block.steps) { step in
                        WorkoutDetailStepRow(step: step)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A single step: dot, name, pace hint and its duration or distance.
private struct WorkoutDetailStepRow: View {
    let step: WorkoutStep

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(WorkoutDetailFormat.dotColor(step))
                .frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 1) {
                Text(WorkoutDetailFormat.title(step))
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.ink)
                if let detail = WorkoutDetailFormat.detail(step) {
                    Text(detail)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                }
            }
            Spacer(minLength: 8)
            Text(WorkoutDetailFormat.amount(step))
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.ink)
        }
        .frame(minHeight: 54)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.hairline).frame(height: 1)
        }
        .accessibilityElement(children: .combine)
    }
}

/// A group of steps repeated several times, shown as "6 ×".
private struct WorkoutDetailRepeatBlock: View {
    let block: WorkoutBlock

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Text("\(block.repeats) ×")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Theme.ink)
                .frame(width: 44)
                .frame(maxHeight: .infinity)
                .background(Theme.orange, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .accessibilityLabel("\(block.repeats) times")
            VStack(alignment: .leading, spacing: 10) {
                ForEach(block.steps) { step in
                    stepLine(step)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.vertical, 12)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.hairline).frame(height: 1)
        }
    }

    private func stepLine(_ step: WorkoutStep) -> some View {
        HStack(alignment: .center, spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                Text(WorkoutDetailFormat.title(step))
                    .font(.system(size: 15, weight: step.kind == .work ? .semibold : .regular))
                    .foregroundStyle(Theme.ink)
                if let detail = WorkoutDetailFormat.detail(step) {
                    Text(detail)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                }
            }
            Spacer(minLength: 8)
            Text(WorkoutDetailFormat.repeatAmount(step))
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.ink)
        }
        .accessibilityElement(children: .combine)
    }
}

/// Text helpers for the steps of a session.
enum WorkoutDetailFormat {
    static func dotColor(_ step: WorkoutStep) -> Color {
        switch step.kind {
        case .work: return Theme.orange
        case .exercise: return Theme.yellow
        case .warmup, .recovery, .cooldown, .steady: return Theme.greenSoft
        }
    }

    static func title(_ step: WorkoutStep) -> String {
        switch step.kind {
        case .warmup, .cooldown, .recovery:
            return step.kind.title
        case .work, .steady, .exercise:
            return step.label.isEmpty ? step.kind.title : step.label
        }
    }

    static func paceRange(_ step: WorkoutStep) -> String? {
        if let fast = step.paceFast, let slow = step.paceSlow {
            return fast == slow ? "\(Fmt.pace(fast))/km" : "\(Fmt.pace(fast)) – \(Fmt.pace(slow))/km"
        }
        if let pace = step.paceFast ?? step.paceSlow {
            return "\(Fmt.pace(pace))/km"
        }
        return nil
    }

    static func detail(_ step: WorkoutStep) -> String? {
        let range = paceRange(step)
        switch step.kind {
        case .warmup, .cooldown, .recovery:
            var parts: [String] = []
            if !step.label.isEmpty { parts.append(step.label) }
            if let range { parts.append(range) }
            return parts.isEmpty ? nil : parts.joined(separator: ", ")
        case .work, .steady:
            return range ?? "Free pace"
        case .exercise:
            return nil
        }
    }

    static func meters(_ meters: Int) -> String {
        meters >= 1000 ? Fmt.km(Double(meters) / 1000) : "\(meters) m"
    }

    static func seconds(_ seconds: Int) -> String {
        seconds % 60 == 0 ? "\(seconds / 60) min" : Fmt.duration(seconds)
    }

    /// Distance or duration of the step.
    static func amount(_ step: WorkoutStep) -> String {
        if let distance = step.distanceM { return meters(distance) }
        if let duration = step.durationSec { return seconds(duration) }
        return ""
    }

    /// Inside a repeat block, a distance effort named after its distance shows its expected time.
    static func repeatAmount(_ step: WorkoutStep) -> String {
        if step.kind == .work, let distance = step.distanceM,
           let fast = step.paceFast, let slow = step.paceSlow,
           title(step) == meters(distance) || title(step) == "\(distance) m" {
            let pace = Double(fast + slow) / 2
            return Fmt.duration(Int((Double(distance) / 1000 * pace).rounded()))
        }
        return amount(step)
    }
}
