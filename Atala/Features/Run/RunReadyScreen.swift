import SwiftUI
import UIKit

/// "Ready to go?": what the session is, the GPS signal on one line, the voice settings in one
/// card, a word from the coach, then one big start button.
struct RunReadyScreen: View {
    let workout: Workout?
    @ObservedObject var tracker: RunTracker
    let onStart: () -> Void
    let onClose: () -> Void

    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var showPaywall = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                closeButton
                header
                RunReadyGPSLine(quality: tracker.gps,
                                simulated: tracker.isSimulating,
                                text: gpsText,
                                onSettings: openSettings)
                if let workout {
                    RunReadySessionCard(workout: workout)
                }
                settingsCard
                CoachNote(coach: store.coach, text: coachLine)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 4)
            .padding(.bottom, 16)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            BottomBar {
                Button(action: onStart) {
                    HStack(spacing: 10) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 17, weight: .bold))
                        Text(L("Start"))
                    }
                }
                .buttonStyle(RunBigButtonStyle())
            }
        }
        .proPaywall(isPresented: $showPaywall)
    }

    private var closeButton: some View {
        Button(action: {
            Haptics.tap()
            onClose()
        }) {
            Image(systemName: "xmark")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(L("Close"))
        .padding(.leading, -10)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L("Ready to go?"))
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.orangeDeep)
            Text(workout?.title ?? L("Free run"))
                .font(Theme.title(32))
                .tracking(-0.8)
                .foregroundStyle(Theme.ink)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
            if workout == nil {
                Text(L("No plan, no target. Your distance, time and pace are recorded."))
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Settings

    private var hasPlannedSteps: Bool {
        guard let workout else { return false }
        return !workout.blocks.isEmpty
    }

    private var voiceIsOn: Bool { purchases.isPro && store.settings.voice.enabled }

    private var settingsCard: some View {
        VStack(spacing: 0) {
            CoachVoiceRow(coachName: store.coach.name,
                          isPro: purchases.isPro,
                          isOn: $store.settings.voice.enabled,
                          onText: hasPlannedSteps ? L("Announces each step") : L("Announces each kilometre"),
                          offText: L("Off for this run"),
                          onLocked: { showPaywall = true })
            if voiceIsOn {
                if hasPlannedSteps {
                    RunReadyToggleRow(title: L("Step changes"), isOn: $store.settings.voice.announceSteps)
                    RunReadyToggleRow(title: L("Pace too fast or too slow"), isOn: $store.settings.voice.announcePace)
                }
                RunReadyToggleRow(title: L("Every kilometre"), isOn: $store.settings.voice.announceEveryKm)
                Rectangle().fill(Theme.hairline).frame(height: 1)
                    .padding(.top, 6)
                HStack(spacing: 10) {
                    Image(systemName: "music.note")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.yellowDeep)
                        .accessibilityHidden(true)
                    Text(L("Your music gets quieter when %@ speaks", store.coach.name))
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
                .padding(.top, 12)
                .padding(.bottom, 4)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
        .animation(.easeInOut(duration: 0.2), value: voiceIsOn)
    }

    // MARK: GPS

    private var gpsText: String {
        if tracker.isSimulating { return L("Simulated signal (simulator)") }
        switch tracker.gps {
        case .unknown: return L("Looking for satellites…")
        case .denied: return L("Location is off: only your time will be measured. Turn it on in Settings, or add the run by hand afterwards.")
        case .poor:
            if let metres = accuracyMetres { return L("Weak, accuracy %@ m. Move to an open spot.", metres) }
            return L("Weak. Move to an open spot.")
        case .fair:
            if let metres = accuracyMetres { return L("Fair, accuracy %@ m", metres) }
            return L("Fair")
        case .good:
            if let metres = accuracyMetres { return L("Good, accuracy %@ m", metres) }
            return L("Good")
        }
    }

    private var accuracyMetres: Int? {
        guard let accuracy = tracker.accuracy else { return nil }
        return Int(accuracy.rounded())
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    // MARK: Wording

    private var coachLine: String {
        guard let first = tracker.steps.first else {
            return L("No plan today. Run the way you feel, I'll keep track of everything.")
        }
        let hasFastPart = tracker.steps.contains { $0.kind == .work }
        if first.kind == .warmup, let seconds = first.durationSec {
            let minutes = max(1, seconds / 60)
            return hasFastPart
                ? L("We start with %@ very easy minutes. I'll tell you when to speed up.", minutes)
                : L("We start with %@ very easy minutes.", minutes)
        }
        if first.kind == .work {
            return L("Today is your day. Start a touch slower than you want to, I'll guide you.")
        }
        return L("Easy and steady today. You should be able to talk the whole way.")
    }
}

// MARK: - GPS line

/// The GPS signal on one line: a coloured dot, "GPS" and its state.
private struct RunReadyGPSLine: View {
    let quality: RunGPSQuality
    let simulated: Bool
    let text: String
    let onSettings: () -> Void

    private var color: Color {
        if simulated { return Theme.green }
        switch quality {
        case .good, .fair: return Theme.green
        case .poor: return Theme.orange
        case .denied: return Theme.orangeDeep
        case .unknown: return Theme.faint
        }
    }

    private var showsSettings: Bool { quality == .denied && !simulated }

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            ZStack {
                Circle()
                    .fill(color.opacity(0.22))
                    .frame(width: 22, height: 22)
                Circle()
                    .fill(color)
                    .frame(width: 10, height: 10)
            }
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text("GPS")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Text(text)
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.body)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
            if showsSettings {
                Button(L("Settings")) {
                    Haptics.tap()
                    onSettings()
                }
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .frame(minHeight: 44)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

// MARK: - Settings rows

/// A secondary switch of the settings card, lined up under the title of the voice row.
private struct RunReadyToggleRow: View {
    let title: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            Text(title)
                .font(.system(size: 15))
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .tint(Theme.green)
        .padding(.leading, 58)
        .frame(minHeight: 44)
    }
}

// MARK: - Session card

/// One line of the session at a glance.
private struct RunReadyPlanRow: Identifiable {
    let id: Int
    let color: Color
    let title: String
    let detail: String?
    let amount: String
}

/// What the session is: its key figures, its shape as a bar, and its steps in a few lines.
private struct RunReadySessionCard: View {
    let workout: Workout

    private var steps: [WorkoutStep] { workout.flatSteps.filter { $0.kind != .exercise } }

    private var rows: [RunReadyPlanRow] {
        var result: [RunReadyPlanRow] = []
        for block in workout.blocks {
            let blockSteps: [WorkoutStep] = block.steps.filter { $0.kind != .exercise }
            if block.repeats > 1, let main = blockSteps.first(where: { $0.kind == .work }) ?? blockSteps.first {
                var parts: [String] = []
                if let range = WorkoutDetailFormat.paceRange(main) { parts.append(range) }
                if let rest = blockSteps.first(where: { $0.kind == .recovery }) {
                    parts.append(L("Recovery %@", RunFormat.amount(rest)))
                }
                let detail: String? = parts.isEmpty ? nil : parts.joined(separator: " · ")
                result.append(RunReadyPlanRow(id: result.count,
                                              color: WorkoutDetailFormat.dotColor(main),
                                              title: "\(block.repeats) × \(RunFormat.amount(main))",
                                              detail: detail,
                                              amount: main.kind.title))
            } else {
                for step in blockSteps {
                    result.append(RunReadyPlanRow(id: result.count,
                                                  color: WorkoutDetailFormat.dotColor(step),
                                                  title: WorkoutDetailFormat.title(step),
                                                  detail: WorkoutDetailFormat.detail(step),
                                                  amount: WorkoutDetailFormat.amount(step)))
                }
            }
        }
        return result
    }

    var body: some View {
        let planRows: [RunReadyPlanRow] = rows
        return VStack(alignment: .leading, spacing: 14) {
            facts
            if !steps.isEmpty {
                RunSegmentBar(steps: steps, currentIndex: 0, complete: false, preview: true)
            }
            if planRows.isEmpty {
                Text(L("Run as you feel, with no set structure."))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                VStack(spacing: 10) {
                    ForEach(planRows) { row in
                        line(row)
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 22)
    }

    private var facts: some View {
        HStack(alignment: .top, spacing: 8) {
            if workout.distanceKm > 0 {
                fact(Fmt.km(workout.distanceKm), L("Distance"))
            }
            fact(Fmt.minutes(workout.durationMin), L("Duration"))
            if let pace = workout.targetPace {
                fact("\(Fmt.pace(pace))/km", L("Target pace"))
            }
        }
    }

    private func fact(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 22, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    private func line(_ row: RunReadyPlanRow) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Circle()
                .fill(row.color)
                .frame(width: 9, height: 9)
                .alignmentGuide(.firstTextBaseline) { dimensions in dimensions[.bottom] + 2 }
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 1) {
                Text(row.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                if let detail = row.detail {
                    Text(detail)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 8)
            Text(row.amount)
                .font(.system(size: 15, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    RunReadyScreen(workout: AppStore.preview.nextRun, tracker: RunTracker(), onStart: {}, onClose: {})
        .environmentObject(AppStore.preview)
}
