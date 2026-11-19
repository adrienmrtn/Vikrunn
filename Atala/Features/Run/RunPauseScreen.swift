import SwiftUI

/// Paused: the clock stands still until the runner resumes or finishes.
struct RunPauseScreen: View {
    @ObservedObject var tracker: RunTracker
    let coach: Coach
    let onResume: () -> Void
    let onFinish: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                if tracker.hasSteps {
                    RunSegmentBar(steps: tracker.steps, currentIndex: tracker.stepIndex,
                                  complete: tracker.stepsComplete)
                }
                clock
                statsCard
                RunCoachBubble(coach: coach, text: L("Take your time. We start again whenever you like."),
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
            Text(L("Paused"))
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Theme.ink, in: Capsule())
            Text(Fmt.duration(tracker.elapsed))
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
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 18)
    }

    private var positionLine: String {
        let distance = RunFormat.distance(tracker.distance)
        guard tracker.hasSteps else { return L("Free run") + " · " + distance }
        if tracker.stepsComplete { return L("Session complete") + " · " + distance }
        if let rep = RunFormat.repetition(at: tracker.stepIndex, in: tracker.steps) {
            return L("Rep %@ of %@ · %@", rep.number, rep.total, distance)
        }
        let title = tracker.currentStep?.kind.title ?? L("Run")
        return "\(title) · \(distance)"
    }

    private var statsCard: some View {
        RunStatsRow(items: statItems)
            .padding(18)
            .card(radius: 24)
    }

    private var statItems: [(value: String, label: String)] {
        var items: [(value: String, label: String)] = [
            (value: Fmt.pace(tracker.averagePace), label: L("Average pace")),
            (value: RunFormat.distance(tracker.distance), label: L("Distance")),
        ]
        if tracker.hasSteps {
            let current = tracker.stepsComplete ? tracker.steps.count : tracker.stepIndex + 1
            items.append((value: L("%@ of %@", current, tracker.steps.count), label: L("Step")))
        }
        return items
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
                    Text(L("Resume"))
                        .font(.system(size: 17, weight: .semibold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 58)
                .background(Theme.ink, in: Capsule())
            }
            .buttonStyle(.plain)
            // No haptic here: success comes with the "Session complete" screen, warning when discarded.
            Button(L("Finish session"), action: onFinish)
                .buttonStyle(SecondaryButtonStyle(haptic: nil))
            if tracker.canSkipToCooldown {
                Button(L("Skip straight to the cool-down")) {
                    tracker.skipToCooldown()
                    onResume()
                }
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity, minHeight: 44)
            }
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(Color.white)
    }
}

#Preview {
    RunPauseScreen(tracker: RunTracker(), coach: Coach.all[0], onResume: {}, onFinish: {})
}
