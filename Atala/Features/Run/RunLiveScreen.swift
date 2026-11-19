import SwiftUI

/// The screen the runner glances at while moving: distance, time and pace in very large figures,
/// the step being run in a black card, the coach's last words, and one big pause button.
/// Finishing goes through the pause screen and is confirmed there, so it cannot happen by accident.
struct RunLiveScreen: View {
    @ObservedObject var tracker: RunTracker
    @ObservedObject var voice: VoiceCoach
    let coach: Coach
    let onPause: () -> Void

    @State private var locked = false

    private var step: WorkoutStep? { tracker.currentStep }
    private var kind: StepKind? { step?.kind }
    private var secondary: Color { RunFormat.tintedText(kind) }

    /// The black step card is shown while a planned step is being run.
    private var showsStepCard: Bool { tracker.hasSteps && !tracker.stepsComplete && step != nil }

    var body: some View {
        ZStack {
            RunFormat.tint(kind).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 14) {
                    topLine
                    if tracker.hasSteps {
                        RunSegmentBar(steps: tracker.steps, currentIndex: tracker.stepIndex,
                                      complete: tracker.stepsComplete)
                    }
                    if showsStepCard {
                        stepCard
                    }
                    figures
                    if let step, let fast = step.paceFast, let slow = step.paceSlow, !tracker.stepsComplete {
                        paceCard(step: step, fast: fast, slow: slow)
                    }
                    RunCoachBubble(coach: coach, text: bubbleText)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 10)
                .padding(.bottom, 10)
            }
            .scrollIndicators(.hidden)
            .scrollBounceBehavior(.basedOnSize)
            .safeAreaInset(edge: .bottom, spacing: 0) { controls }
            if locked {
                RunLockOverlay(onUnlock: {
                    Haptics.confirm()
                    locked = false
                })
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.35), value: kind)
        .animation(.easeInOut(duration: 0.2), value: locked)
    }

    // MARK: Top line

    private var topLine: some View {
        HStack(spacing: 10) {
            Text(pillText)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(RunFormat.isFast(kind) ? Color.white : Theme.green)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(RunFormat.isFast(kind) ? Theme.orange : Color.white, in: Capsule())
            Spacer(minLength: 8)
            HStack(spacing: 6) {
                Circle()
                    .fill(gpsColor)
                    .frame(width: 10, height: 10)
                    .accessibilityHidden(true)
                Text(tracker.isSimulating ? L("Simulated GPS") : gpsText)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
    }

    private var pillText: String {
        if !tracker.hasSteps { return L("Free run") }
        if tracker.stepsComplete { return L("Session complete") }
        return RunFormat.pill(at: tracker.stepIndex, in: tracker.steps)
    }

    private var gpsText: String {
        switch tracker.gps {
        case .good: return L("GPS good")
        case .fair: return L("GPS fair")
        case .poor: return L("GPS weak")
        case .denied: return L("Location off")
        case .unknown: return L("Looking for GPS")
        }
    }

    private var gpsColor: Color {
        if tracker.isSimulating { return Theme.green }
        switch tracker.gps {
        case .good, .fair: return Theme.green
        case .poor: return Theme.orange
        case .denied: return Theme.orangeDeep
        case .unknown: return Theme.faint
        }
    }

    // MARK: Step card

    /// What is left of the current step: (caption, value, unit, "of …").
    private var remaining: (caption: String, value: String, unit: String?, total: String) {
        if let step, let seconds = tracker.stepRemainingSeconds {
            return (L("Time remaining"), Fmt.duration(seconds), nil, L("of %@", RunFormat.spokenAmount(step)))
        }
        if let step, let metres = tracker.stepRemainingMeters {
            let total = L("of %@", RunFormat.amount(step))
            if metres >= 1000 {
                return (L("Distance remaining"), String(format: "%.2f", metres / 1000), "km", total)
            }
            return (L("Distance remaining"), "\(Int(metres.rounded()))", "m", total)
        }
        return (L("Distance"), String(format: "%.2f", tracker.distance / 1000), "km", "")
    }

    /// How far into the current step the runner is, 0 to 1.
    private var stepProgress: Double {
        guard let step else { return 0 }
        if let seconds = step.durationSec, seconds > 0 {
            return Double(tracker.stepElapsed) / Double(seconds)
        }
        if let metres = step.distanceM, metres > 0 {
            return tracker.stepDistance / Double(metres)
        }
        return 0
    }

    private var stepCard: some View {
        let content = remaining
        return RunStepCard(caption: content.caption,
                           value: content.value,
                           unit: content.unit,
                           total: content.total,
                           progress: stepProgress,
                           next: RunFormat.next(after: tracker.stepIndex, in: tracker.steps),
                           position: L("Step %@ of %@", tracker.stepIndex + 1, tracker.steps.count),
                           fast: RunFormat.isFast(kind))
    }

    // MARK: Figures

    private var footnote: String? {
        if !tracker.hasSteps { return L("Run the way you feel") }
        if tracker.stepsComplete { return L("Everything planned is done. Finish when you like.") }
        return nil
    }

    private var figures: some View {
        let compact: Bool = showsStepCard
        return VStack(spacing: compact ? 6 : 12) {
            RunLiveFigure(label: L("Distance"),
                          value: String(format: "%.2f", tracker.distance / 1000),
                          unit: "km",
                          size: compact ? 76 : 108,
                          alignment: .center,
                          labelColor: secondary)
            if let footnote {
                Text(footnote)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            HStack(alignment: .top, spacing: 12) {
                RunLiveFigure(label: L("Time"),
                              value: Fmt.duration(tracker.elapsed),
                              size: compact ? 44 : 52,
                              alignment: .center,
                              labelColor: secondary)
                RunLiveFigure(label: L("Pace"),
                              value: Fmt.pace(tracker.currentPace),
                              unit: "/km",
                              size: compact ? 44 : 52,
                              alignment: .center,
                              labelColor: secondary)
            }
            Text(L("Average pace %@/km", Fmt.pace(tracker.averagePace)))
                .font(.system(size: 15, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: Pace against the target

    private func paceCard(step: WorkoutStep, fast: Int, slow: Int) -> some View {
        let hint = paceHint
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(hint.text)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(hint.color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Spacer(minLength: 8)
                Text(L("Target %@ to %@", Fmt.pace(slow), Fmt.pace(fast)))
                    .font(.system(size: 14, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.body)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            if step.kind == .work {
                RunPaceGauge(pace: tracker.currentPace, paceFast: fast, paceSlow: slow, showsLabels: false)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var paceHint: (text: String, color: Color) {
        let pace = tracker.currentPace
        guard let step else { return (L("By feel"), Theme.green) }
        guard let fast = step.paceFast, let slow = step.paceSlow else {
            return (step.kind == .work ? L("By feel") : L("Easy, as planned"), Theme.green)
        }
        guard pace > 0 else { return (L("Finding your pace…"), Theme.muted) }
        if pace < fast - 5 { return (L("Too fast"), Theme.orangeDeep) }
        if pace > slow + 5 { return (step.kind == .work ? L("Too slow") : L("Nice and easy"), step.kind == .work ? Theme.orangeDeep : Theme.green) }
        return (step.kind == .work ? L("On target") : L("Easy, as planned"), Theme.green)
    }

    private var bubbleText: String {
        if !voice.lastCue.isEmpty { return voice.lastCue }
        return tracker.hasSteps ? L("Here we go. I'll guide you step by step.") : L("Off you go. Run the way you feel.")
    }

    // MARK: Controls

    private var controls: some View {
        HStack {
            RunRoundButton(symbol: "lock.fill", label: L("Lock the screen")) {
                Haptics.tap()
                locked = true
            }
            Spacer()
            Button(action: {
                Haptics.confirm()
                onPause()
            }) {
                Image(systemName: "pause.fill")
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 92, height: 92)
                    .background(Theme.ink, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("Pause"))
            .accessibilityHint(L("You can finish the session from the pause screen"))
            Spacer()
            RunRoundButton(symbol: "forward.end.fill", label: L("Skip to the next step")) { tracker.skipStep() }
                .opacity(tracker.canSkipStep ? 1 : 0)
                .disabled(!tracker.canSkipStep)
        }
        .padding(.horizontal, 28)
        .padding(.top, 8)
        .padding(.bottom, 10)
    }
}

#Preview {
    RunLiveScreen(tracker: RunTracker(), voice: VoiceCoach(), coach: Coach.all[0], onPause: {})
}
