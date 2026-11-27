import SwiftUI
import UIKit
import CoreLocation

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
/// One neutral "Continue" button that leads to the system question (guideline 5.1.1(iv)): no
/// "Allow" wording and no way around the question here. Whatever the answer, the flow moves on.
struct OnboardingPermissionsScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @ObservedObject private var location = LocationPermission.shared
    /// True between the tap and the user's answer to the system question.
    @State private var isAsking = false

    var body: some View {
        OnboardingScaffold(title: L("Let Atala follow your runs"),
                           buttonTitle: L("Continue"),
                           canContinue: !isAsking,
                           secondaryTitle: location.isRefused ? L("Open Settings") : nil,
                           onSecondary: location.isRefused ? openSettings : nil,
                           onContinue: continueTapped) {
            OnboardingLinkGraphic(symbol: "location.fill", tint: Theme.orange, linked: location.isAllowed)
                .animation(.spring(response: 0.45, dampingFraction: 0.7), value: location.isAllowed)
            VStack(alignment: .leading, spacing: 12) {
                OnboardingBenefitRow(text: L("Your distance and pace, live"))
                OnboardingBenefitRow(text: L("%@'s voice when you're too fast or too slow", store.coach.name))
                OnboardingBenefitRow(text: L("Your route on a map after every run"))
            }
            Text(locationFootnote)
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .onAppear { location.refresh() }
        .onChange(of: location.status) { _, status in
            // The user has answered the system question: on to the next screen.
            guard isAsking, status != .notDetermined else { return }
            isAsking = false
            if location.isAllowed { Haptics.success() }
            nav.next(store)
        }
    }

    private var locationFootnote: String {
        if location.isRefused {
            return L("Location is off for Atala. Turn it on in Settings, or log your runs by hand.")
        }
        return L("Only while you're running with the app. You can say no and log your runs by hand.")
    }

    /// First time: shows the system question, and the answer moves the flow on.
    /// Already answered (allowed or refused): straight to the next screen.
    private func continueTapped() {
        guard !isAsking else { return }
        guard location.status == .notDetermined else {
            nav.next(store)
            return
        }
        isAsking = true
        location.request()
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

/// Screen 33: Apple Health, then into the app.
/// Like the location screen, one neutral "Continue" button leads to the system sheet
/// (guideline 5.1.1(iv)), where the user picks what to share or declines. Then into the app.
struct OnboardingHealthConnectScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var isAsking = false

    private var connected: Bool { store.settings.healthImportEnabled }
    private var available: Bool { HealthKitService.shared.isAvailable }

    var body: some View {
        OnboardingScaffold(title: L("Connect Apple Health"),
                           buttonTitle: isAsking ? L("One moment…") : L("Continue"),
                           canContinue: !isAsking,
                           onContinue: continueTapped) {
            OnboardingLinkGraphic(symbol: "heart.fill", tint: Color(hex: 0xFF2D55), linked: connected)
                .animation(.spring(response: 0.45, dampingFraction: 0.7), value: connected)
            VStack(alignment: .leading, spacing: 12) {
                OnboardingBenefitRow(text: L("Your past runs fill your history from day one"))
                OnboardingBenefitRow(text: L("Runs from your watch count in your plan"))
                OnboardingBenefitRow(text: L("Your heart rate on every run summary"))
            }
            Text(available
                 ? L("Optional. You can turn it off at any time in your profile.")
                 : L("Not available on this device"))
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            if let workout = store.nextRun {
                firstSessionCard(workout)
            }
        }
    }

    /// Shows the Health sheet when it can, brings in the recent runs if the user shared them,
    /// then opens the app. Whatever the answer, the user goes on.
    private func continueTapped() {
        guard !isAsking else { return }
        guard available, !connected else {
            store.finishOnboarding()
            return
        }
        isAsking = true
        Task { @MainActor in
            let granted = await HealthKitService.shared.requestAuthorization()
            store.settings.healthImportEnabled = granted
            if granted {
                Haptics.success()
                _ = await HealthKitService.shared.importRecentRuns(into: store)
            }
            isAsking = false
            store.finishOnboarding()
        }
    }

    private func firstSessionCard(_ workout: Workout) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Circle()
                    .fill(Theme.greenSoft)
                    .frame(width: 8, height: 8)
                Text(L("Your first session · %@", Fmt.day(workout.date, "EEEE d MMMM")))
                    .font(.system(size: 13))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
            }
            Text(workout.title)
                .font(Theme.title(24))
                .foregroundStyle(Theme.ink)
            HStack(spacing: 0) {
                metric(Fmt.km(workout.distanceKm), L("Distance"))
                metric(Fmt.minutes(workout.durationMin), L("Duration"))
                metric(workout.targetPace.map { "\(Fmt.pace($0))/km" } ?? L("Free"), L("Target pace"))
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
