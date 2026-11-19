import SwiftUI

/// Paused: the clock stands still until the user resumes or ends the session.
struct GuidedPauseScreen: View {
    @ObservedObject var engine: GuidedSessionEngine
    let coach: Coach
    let onResume: () -> Void
    let onEnd: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                GuidedProgressBar(count: engine.exercises.count,
                                  currentIndex: engine.current?.exerciseIndex ?? 0)
                clock
                RunStatsRow(items: statItems)
                    .padding(18)
                    .card(radius: 24)
                RunCoachBubble(coach: coach, text: "Take your time. We pick it up whenever you like.",
                               showsSpeaker: false)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 14)
            .padding(.bottom, 12)
        }
        .scrollIndicators(.hidden)
        .scrollBounceBehavior(.basedOnSize)
        .background(Color.white.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) { actions }
    }

    private var clock: some View {
        VStack(spacing: 6) {
            Text("Paused")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Theme.ink, in: Capsule())
            Text(Fmt.duration(engine.totalElapsed))
                .font(.system(size: 84, weight: .bold))
                .monospacedDigit()
                .tracking(-2)
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .padding(.top, 10)
            Text(positionLine)
                .font(.system(size: 15))
                .foregroundStyle(Theme.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 18)
    }

    private var positionLine: String {
        guard let segment = engine.current, let exercise = engine.currentExercise else { return "Session" }
        var line = exercise.info.name
        if let side = segment.side, segment.kind != .rest { line += ", \(side.spoken)" }
        if exercise.sets > 1 { line += " · set \(segment.setNumber) of \(exercise.sets)" }
        return line
    }

    private var statItems: [(value: String, label: String)] {
        let number = (engine.current?.exerciseIndex ?? 0) + 1
        return [
            (value: "\(number) of \(engine.exercises.count)", label: "Exercise"),
            (value: "\(engine.completedExercises.count)", label: "Completed"),
            (value: Fmt.minutes(max(1, Int((Double(engine.plannedSeconds) / 60).rounded()))), label: "Planned"),
        ]
    }

    private var actions: some View {
        VStack(spacing: 10) {
            Button(action: {
                Haptics.confirm()
                onResume()
            }) {
                HStack(spacing: 10) {
                    Image(systemName: "play.fill")
                        .font(.system(size: 16, weight: .bold))
                    Text("Resume")
                        .font(.system(size: 17, weight: .semibold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 58)
                .background(Theme.ink, in: Capsule())
            }
            .buttonStyle(.plain)
            Button("End session", action: onEnd)
                .buttonStyle(SecondaryButtonStyle())
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(Color.white)
    }
}

/// After the last exercise: the figures, a word from the coach, an optional feeling, then "Done".
struct GuidedFinishScreen: View {
    let workout: Workout
    let elapsedSec: Int
    let doneCount: Int
    let totalCount: Int
    let coach: Coach
    let onDone: (Feeling?) -> Void

    @State private var feeling: Feeling?

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                header
                RunStatsRow(items: [
                    (value: Fmt.duration(elapsedSec), label: "Total time"),
                    (value: "\(doneCount) of \(totalCount)", label: "Exercises done"),
                    (value: workout.kind.title, label: "Session"),
                ])
                .padding(18)
                .card(radius: 24)
                CoachNote(coach: coach, text: coachLine)
                VStack(alignment: .leading, spacing: 9) {
                    Text("How did it feel? (optional)")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    ForEach(Feeling.allCases) { option in
                        GuidedFeelingRow(feeling: option, subtitle: subtitle(for: option),
                                         selected: feeling == option) {
                            feeling = feeling == option ? nil : option
                        }
                    }
                }
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 20)
            .padding(.bottom, 16)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            BottomBar {
                PrimaryButton(title: "Done") { onDone(feeling) }
            }
        }
    }

    private var header: some View {
        VStack(spacing: 12) {
            CoachPoseView(coach: coach, pose: .cheer)
                .frame(height: 170)
                .accessibilityHidden(true)
            Text("Session complete")
                .font(Theme.title(28))
                .foregroundStyle(Theme.ink)
            Text(workout.title)
                .font(.system(size: 15))
                .foregroundStyle(Theme.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var coachLine: String {
        if feeling == .pain {
            return "Thank you for telling me. Take it easy today, and tell me more in the chat whenever you like."
        }
        if doneCount < totalCount {
            return "Every bit counts. You showed up, and that is what builds the habit."
        }
        if workout.kind == .strength {
            return "Well done. Stronger legs and core will carry you through the end of your runs."
        }
        return "Well done. A few calm minutes like these help you feel fresh for your next run."
    }

    private func subtitle(for option: Feeling) -> String {
        switch option {
        case .easy: return "I could have done more"
        case .good: return "Just right"
        case .hard: return "That was a lot for me"
        case .pain: return "I'd rather tell \(coach.name)"
        }
    }
}

private struct GuidedFeelingRow: View {
    let feeling: Feeling
    let subtitle: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: {
            Haptics.select()
            action()
        }) {
            HStack(spacing: 14) {
                Circle()
                    .fill(RunFeelingStyle.background(feeling))
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: RunFeelingStyle.symbol(feeling))
                            .font(.system(size: 19, weight: .semibold))
                            .foregroundStyle(RunFeelingStyle.tint(feeling))
                    )
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(feeling.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                SelectionMark(selected: selected)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(minHeight: 68)
            .card(selected: selected)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

#Preview("Pause") {
    let engine = GuidedSessionEngine()
    engine.configure(exercises: GuidedPrescription.strengthSession(phase: .build))
    engine.start()
    engine.pause()
    return GuidedPauseScreen(engine: engine, coach: Coach.all[0], onResume: {}, onEnd: {})
}

#Preview("Finish") {
    let store = AppStore.preview
    let workout = store.allWorkouts.first { $0.kind == .stretching }
    return Group {
        if let workout {
            GuidedFinishScreen(workout: workout, elapsedSec: 462, doneCount: 5, totalCount: 5,
                               coach: store.coach, onDone: { _ in })
        }
    }
}
