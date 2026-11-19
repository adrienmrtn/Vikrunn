import SwiftUI

/// The screen the runner glances at: one very large number, the pace, the coach's last words.
struct RunLiveScreen: View {
    @ObservedObject var tracker: RunTracker
    @ObservedObject var voice: VoiceCoach
    let coach: Coach
    let onPause: () -> Void

    @State private var locked = false

    private var step: WorkoutStep? { tracker.currentStep }
    private var kind: StepKind? { step?.kind }
    private var secondary: Color { RunFormat.tintedText(kind) }

    var body: some View {
        ZStack {
            RunFormat.tint(kind).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 22) {
                    if tracker.hasSteps {
                        RunSegmentBar(steps: tracker.steps, currentIndex: tracker.stepIndex,
                                      complete: tracker.stepsComplete)
                    }
                    stepHeader
                    primaryNumber
                    paceCard
                    RunCoachBubble(coach: coach, text: bubbleText)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 14)
                .padding(.bottom, 12)
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

    // MARK: Step

    private var stepHeader: some View {
        HStack(spacing: 10) {
            Text(pillText)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(RunFormat.isFast(kind) ? Color.white : Theme.green)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(RunFormat.isFast(kind) ? Theme.orange : Color.white, in: Capsule())
            Spacer(minLength: 8)
            Text(nextText)
                .font(.system(size: 14))
                .foregroundStyle(secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
    }

    private var pillText: String {
        if !tracker.hasSteps { return L("Free run") }
        if tracker.stepsComplete { return L("Session complete") }
        return RunFormat.pill(at: tracker.stepIndex, in: tracker.steps)
    }

    private var nextText: String {
        if !tracker.hasSteps { return tracker.isSimulating ? L("Simulated GPS") : gpsText }
        if tracker.stepsComplete { return L("Finish when you like") }
        return RunFormat.next(after: tracker.stepIndex, in: tracker.steps)
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

    // MARK: Primary number

    private var primary: (caption: String, value: String, unit: String?, footnote: String) {
        if let step, let remaining = tracker.stepRemainingSeconds {
            return (L("Time remaining"), Fmt.duration(remaining), nil, L("of %@", RunFormat.spokenAmount(step)))
        }
        if let step, let remaining = tracker.stepRemainingMeters {
            let footnote = L("of %@", RunFormat.amount(step))
            if remaining >= 1000 {
                return (L("Distance remaining"), String(format: "%.2f", remaining / 1000), "km", footnote)
            }
            return (L("Distance remaining"), "\(Int(remaining.rounded()))", "m", footnote)
        }
        let footnote = tracker.stepsComplete ? L("Everything planned is done") : L("Run the way you feel")
        return (L("Distance"), String(format: "%.2f", tracker.distance / 1000), "km", footnote)
    }

    private var primaryNumber: some View {
        let content = primary
        return VStack(spacing: 4) {
            Text(content.caption)
                .font(.system(size: 14))
                .foregroundStyle(secondary)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(content.value)
                    .font(.system(size: 104, weight: .bold))
                    .monospacedDigit()
                    .tracking(-3)
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                if let unit = content.unit {
                    Text(unit)
                        .font(.system(size: 30, weight: .bold))
                        .foregroundStyle(Theme.ink)
                }
            }
            Text(content.footnote)
                .font(.system(size: 15))
                .foregroundStyle(secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 6)
        .accessibilityElement(children: .combine)
    }

    // MARK: Pace card

    private var paceCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text(L("Pace"))
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                Spacer()
                Text(paceHint.text)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(paceHint.color)
            }
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(Fmt.pace(tracker.currentPace))
                    .font(.system(size: 44, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                Text("/km")
                    .font(.system(size: 16))
                    .foregroundStyle(Theme.muted)
            }
            if let step, step.kind == .work, let fast = step.paceFast, let slow = step.paceSlow {
                RunPaceGauge(pace: tracker.currentPace, paceFast: fast, paceSlow: slow)
            }
            Divider().overlay(Theme.hairline)
            RunStatsRow(items: [
                (value: Fmt.duration(tracker.elapsed), label: L("Total time")),
                (value: RunFormat.distance(tracker.distance), label: L("Distance")),
                (value: Fmt.pace(tracker.averagePace), label: L("Average pace")),
            ])
        }
        .padding(18)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private var paceHint: (text: String, color: Color) {
        let pace = tracker.currentPace
        guard let step else { return (L("By feel"), Theme.green) }
        guard let fast = step.paceFast, let slow = step.paceSlow else {
            return (step.kind == .work ? L("By feel") : L("Easy, as planned"), Theme.green)
        }
        guard pace > 0 else { return (L("Target %@ to %@", Fmt.pace(slow), Fmt.pace(fast)), Theme.muted) }
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
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 84, height: 84)
                    .background(Theme.ink, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("Pause"))
            Spacer()
            RunRoundButton(symbol: "forward.end.fill", label: L("Skip to the next step")) { tracker.skipStep() }
                .opacity(tracker.canSkipStep ? 1 : 0)
                .disabled(!tracker.canSkipStep)
        }
        .padding(.horizontal, 28)
        .padding(.top, 10)
        .padding(.bottom, 12)
    }
}

#Preview {
    RunLiveScreen(tracker: RunTracker(), voice: VoiceCoach(), coach: Coach.all[0], onPause: {})
}
