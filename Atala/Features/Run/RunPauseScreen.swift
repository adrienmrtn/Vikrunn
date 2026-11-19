import SwiftUI

/// Paused: the clock stands still until the runner resumes or finishes.
/// Resuming is the big button; finishing is the quieter one and is confirmed by the run flow.
struct RunPauseScreen: View {
    @ObservedObject var tracker: RunTracker
    let coach: Coach
    let onResume: () -> Void
    let onFinish: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                pausedPill
                if tracker.hasSteps {
                    RunSegmentBar(steps: tracker.steps, currentIndex: tracker.stepIndex,
                                  complete: tracker.stepsComplete)
                }
                figures
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

    private var pausedPill: some View {
        HStack(spacing: 8) {
            Image(systemName: "pause.fill")
                .font(.system(size: 13, weight: .bold))
                .accessibilityHidden(true)
            Text(L("Paused"))
                .font(.system(size: 16, weight: .bold))
        }
        .foregroundStyle(Color.white)
        .padding(.horizontal, 16)
        .padding(.vertical, 9)
        .background(Theme.ink, in: Capsule())
        .frame(maxWidth: .infinity)
        .padding(.top, 10)
    }

    // MARK: Figures so far

    private var figures: some View {
        VStack(spacing: 14) {
            RunLiveFigure(label: L("Distance"),
                          value: String(format: "%.2f", tracker.distance / 1000),
                          unit: "km",
                          size: 88,
                          alignment: .center)
            HStack(alignment: .top, spacing: 12) {
                RunLiveFigure(label: L("Time"),
                              value: Fmt.duration(tracker.elapsed),
                              size: 46,
                              alignment: .center)
                RunLiveFigure(label: L("Average pace"),
                              value: Fmt.pace(tracker.averagePace),
                              unit: "/km",
                              size: 46,
                              alignment: .center)
            }
            Text(positionLine)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.muted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 20)
        .frame(maxWidth: .infinity)
        .card(radius: 24)
    }

    /// Where the runner stopped: "Rep 3 of 6", "Warm-up · step 1 of 4", "Free run".
    private var positionLine: String {
        guard tracker.hasSteps else { return L("Free run") }
        if tracker.stepsComplete { return L("Session complete") }
        if let rep = RunFormat.repetition(at: tracker.stepIndex, in: tracker.steps) {
            return L("Rep %@ of %@", rep.number, rep.total)
        }
        let title = tracker.currentStep?.kind.title ?? L("Run")
        return L("%@ · step %@ of %@", title, tracker.stepIndex + 1, tracker.steps.count)
    }

    // MARK: Actions

    private var actions: some View {
        VStack(spacing: 10) {
            Button(action: {
                Haptics.confirm()
                onResume()
            }) {
                HStack(spacing: 10) {
                    Image(systemName: "play.fill")
                        .font(.system(size: 17, weight: .bold))
                    Text(L("Resume"))
                }
            }
            .buttonStyle(RunBigButtonStyle(haptic: nil))
            // No haptic here: success comes with the "Session complete" screen, warning when discarded.
            Button(action: onFinish) {
                HStack(spacing: 8) {
                    Image(systemName: "flag.checkered")
                        .font(.system(size: 15, weight: .semibold))
                    Text(L("Finish session"))
                }
            }
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
