import SwiftUI
import UIKit

/// Atala on the left, the service it connects to on the right, and dots travelling between them.
struct OnboardingLinkGraphic: View {
    let symbol: String
    let tint: Color
    /// True once the connection is made: the dots turn into a solid green link with a check.
    var linked: Bool = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulsing = false

    private static let tileSize: CGFloat = 84

    var body: some View {
        HStack(spacing: 14) {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Theme.forest)
                .frame(width: Self.tileSize, height: Self.tileSize)
                .overlay(
                    BrandLogo(style: .mark, height: 40)
                        .foregroundStyle(Theme.cream)
                )
                .shadow(color: Theme.forest.opacity(0.25), radius: 12, x: 0, y: 8)
            link
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(Color.white)
                .frame(width: Self.tileSize, height: Self.tileSize)
                .overlay(
                    Image(systemName: symbol)
                        .font(.system(size: 38, weight: .semibold))
                        .foregroundStyle(tint)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(Theme.cardBorder, lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.08), radius: 12, x: 0, y: 8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 26)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityHidden(true)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                pulsing = true
            }
        }
    }

    /// Every other dot is lit, and they swap on each pulse.
    private func dotOpacity(_ index: Int) -> Double {
        let even = index % 2 == 0
        return pulsing == even ? 1 : 0.25
    }

    @ViewBuilder private var link: some View {
        if linked {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 30))
                .foregroundStyle(Theme.green)
                .frame(width: 64)
                .transition(.scale.combined(with: .opacity))
        } else {
            HStack(spacing: 7) {
                ForEach(0..<4, id: \.self) { index in
                    Circle()
                        .fill(Theme.green)
                        .frame(width: 7, height: 7)
                        .opacity(dotOpacity(index))
                }
            }
            .frame(width: 64)
        }
    }
}

/// One short benefit with a green check.
private struct OnboardingBenefitRow: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 20))
                .foregroundStyle(Theme.green)
                .accessibilityHidden(true)
            Text(text)
                .font(.system(size: 16))
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
    }
}

/// Screen 32: the location permission.
struct OnboardingPermissionsScreen: View {
    @EnvironmentObject private var store: AppStore
    @ObservedObject private var location = LocationPermission.shared

    var body: some View {
        OnboardingScaffold(title: "Let Atala follow your runs") {
            OnboardingLinkGraphic(symbol: "location.fill", tint: Theme.orange, linked: location.isAllowed)
                .animation(.spring(response: 0.45, dampingFraction: 0.7), value: location.isAllowed)
            VStack(alignment: .leading, spacing: 12) {
                OnboardingBenefitRow(text: "Your distance and pace, live")
                OnboardingBenefitRow(text: "\(store.coach.name)'s voice when you're too fast or too slow")
                OnboardingBenefitRow(text: "Your route on a map after every run")
            }
            locationButton
            Text(locationFootnote)
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .onAppear { location.refresh() }
        .onChange(of: location.isAllowed) { _, allowed in
            if allowed { Haptics.success() }
        }
    }

    private var locationButtonTitle: String {
        if location.isAllowed { return "Location allowed" }
        if location.isRefused { return "Open Settings" }
        return "Allow location"
    }

    private var locationFootnote: String {
        if location.isRefused {
            return "Location is off for Atala. Turn it on in Settings, or log your runs by hand."
        }
        return "Only while you're running with the app. You can say no and log your runs by hand."
    }

    private var locationButton: some View {
        Button(action: locationTapped) {
            HStack(spacing: 8) {
                Image(systemName: location.isAllowed ? "checkmark" : "location.fill")
                    .font(.system(size: 14, weight: .bold))
                Text(locationButtonTitle)
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundStyle(location.isAllowed ? Theme.green : Color.white)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(location.isAllowed ? Theme.greenTint : Theme.orange, in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(location.isAllowed)
    }

    private func locationTapped() {
        Haptics.confirm()
        if location.isRefused {
            guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
            UIApplication.shared.open(url)
        } else {
            location.request()
        }
    }
}

/// Screen 33: optional Apple Health connection, then into the app.
struct OnboardingHealthConnectScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var isAsking = false
    @State private var wasRefused = false
    /// Number of runs brought in from Health, once the import has run.
    @State private var importedCount: Int?

    private var connected: Bool { store.settings.healthImportEnabled }
    private var available: Bool { HealthKitService.shared.isAvailable }

    private var connectTitle: String {
        if connected { return "Connected" }
        if !available { return "Not available on this device" }
        return isAsking ? "Connecting…" : "Connect Apple Health"
    }

    private var footnote: String {
        if wasRefused { return "Access was not granted. You can turn it on later in your profile." }
        if let importedCount {
            if importedCount == 0 {
                return "Connected. No run found in the last 60 days: your history starts with your first session."
            }
            return "\(importedCount) \(importedCount == 1 ? "run" : "runs") from the last 60 days added to your history."
        }
        return "Optional. You can turn it off at any time in your profile."
    }

    var body: some View {
        OnboardingScaffold(title: "Connect Apple Health",
                           buttonTitle: "Start",
                           canContinue: !isAsking,
                           onContinue: { store.finishOnboarding() }) {
            OnboardingLinkGraphic(symbol: "heart.fill", tint: Color(hex: 0xFF2D55), linked: connected)
                .animation(.spring(response: 0.45, dampingFraction: 0.7), value: connected)
            VStack(alignment: .leading, spacing: 12) {
                OnboardingBenefitRow(text: "Your past runs fill your history from day one")
                OnboardingBenefitRow(text: "Runs from your watch count in your plan")
                OnboardingBenefitRow(text: "Your heart rate on every run summary")
            }
            connectButton
            Text(footnote)
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            if let workout = store.nextRun {
                firstSessionCard(workout)
            }
        }
    }

    private var connectButton: some View {
        Button(action: connect) {
            HStack(spacing: 8) {
                Image(systemName: connected ? "checkmark" : "heart.fill")
                    .font(.system(size: 14, weight: .bold))
                Text(connectTitle)
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundStyle(connected ? Theme.green : Color.white)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(buttonColor, in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(connected || !available || isAsking)
    }

    private var buttonColor: Color {
        if connected { return Theme.greenTint }
        return available && !isAsking ? Theme.ink : Theme.faint
    }

    private func firstSessionCard(_ workout: Workout) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Circle()
                    .fill(Theme.greenSoft)
                    .frame(width: 8, height: 8)
                Text("Your first session · \(Fmt.day(workout.date, "EEEE d MMMM"))")
                    .font(.system(size: 13))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
            }
            Text(workout.title)
                .font(Theme.title(24))
                .foregroundStyle(Theme.ink)
            HStack(spacing: 0) {
                metric(Fmt.km(workout.distanceKm), "Distance")
                metric(Fmt.minutes(workout.durationMin), "Duration")
                metric(workout.targetPace.map { "\(Fmt.pace($0))/km" } ?? "Free", "Target pace")
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 22)
    }

    private func metric(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Theme.ink)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(Color(hex: 0x5A5A5A))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func connect() {
        guard available, !connected, !isAsking else { return }
        Haptics.confirm()
        isAsking = true
        Task { @MainActor in
            let granted = await HealthKitService.shared.requestAuthorization()
            store.settings.healthImportEnabled = granted
            wasRefused = !granted
            if granted {
                Haptics.success()
            } else {
                Haptics.warning()
            }
            if granted {
                importedCount = await HealthKitService.shared.importRecentRuns(into: store)
            }
            isAsking = false
        }
    }
}

#Preview("Permissions") {
    OnboardingPermissionsScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Apple Health") {
    OnboardingHealthConnectScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
