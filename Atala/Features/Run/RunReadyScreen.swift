import SwiftUI
import UIKit

/// "Ready to go?": GPS signal, voice, a word from the coach, then the start button.
struct RunReadyScreen: View {
    let workout: Workout?
    @ObservedObject var tracker: RunTracker
    let onStart: () -> Void
    let onClose: () -> Void

    @EnvironmentObject private var store: AppStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                closeButton
                header
                rows
                CoachNote(coach: store.coach, text: coachLine)
                CoachPoseView(coach: store.coach, pose: .run)
                    .frame(height: 210)
                    .frame(maxWidth: .infinity)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 4)
            .padding(.bottom, 16)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            BottomBar {
                PrimaryButton(title: L("Start session"), action: onStart)
            }
        }
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
            Text(sessionLine)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.orangeDeep)
            Text(L("Ready to go?"))
                .font(Theme.title(28))
                .foregroundStyle(Theme.ink)
        }
    }

    private var rows: some View {
        VStack(spacing: 0) {
            Divider().overlay(Theme.hairline)
            RunReadyRow(symbol: "location.fill", tint: Theme.green, background: Theme.greenTint,
                        title: L("GPS signal"), subtitle: gpsSubtitle) {
                gpsTrailing
            }
            Divider().overlay(Theme.hairline)
            RunReadyRow(symbol: "speaker.wave.2.fill", tint: Theme.orangeDeep, background: Theme.orangeTint,
                        title: L("%@'s voice", store.coach.name), subtitle: voiceSubtitle) {
                Toggle(L("Coach voice"), isOn: $store.settings.voice.enabled)
                    .labelsHidden()
                    .tint(Theme.green)
            }
            Divider().overlay(Theme.hairline)
            RunReadyRow(symbol: "music.note", tint: Theme.yellowDeep, background: Theme.yellowTint,
                        title: L("Music"), subtitle: L("Gets quieter when %@ speaks", store.coach.name)) {
                EmptyView()
            }
            Divider().overlay(Theme.hairline)
        }
    }

    // MARK: GPS

    private var gpsSubtitle: String {
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

    @ViewBuilder
    private var gpsTrailing: some View {
        switch tracker.gps {
        case .good, .fair:
            RunReadyBadge(symbol: "checkmark", color: Theme.green)
        case .poor:
            RunReadyBadge(symbol: "exclamationmark", color: Theme.orange)
        case .denied:
            Button(L("Settings")) {
                Haptics.tap()
                openSettings()
            }
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .frame(minHeight: 44)
        case .unknown:
            ProgressView()
        }
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    // MARK: Wording

    private var sessionLine: String {
        guard let workout else { return L("Free run") }
        return "\(workout.title) · \(Fmt.minutes(workout.durationMin))"
    }

    private var voiceSubtitle: String {
        guard ProAccess.isPro else { return L("Included in Atala Pro") }
        guard store.settings.voice.enabled else { return L("Off for this run") }
        return tracker.hasSteps ? L("Announces each step") : L("Announces each kilometre")
    }

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

/// One line of the checklist: icon tile, title, subtitle and a trailing control.
private struct RunReadyRow<Trailing: View>: View {
    let symbol: String
    let tint: Color
    let background: Color
    let title: String
    let subtitle: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: 14) {
            IconTile(systemName: symbol, tint: tint, background: background, size: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .frame(minHeight: 64)
        .padding(.vertical, 4)
    }
}

private struct RunReadyBadge: View {
    let symbol: String
    let color: Color

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 26, height: 26)
            .overlay(
                Image(systemName: symbol)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
            )
            .accessibilityHidden(true)
    }
}

#Preview {
    RunReadyScreen(workout: AppStore.preview.nextRun, tracker: RunTracker(), onStart: {}, onClose: {})
        .environmentObject(AppStore.preview)
}
