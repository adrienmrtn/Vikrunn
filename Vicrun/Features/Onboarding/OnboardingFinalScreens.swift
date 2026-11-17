import SwiftUI

/// Screen 32: permissions. Location now, notifications in a later version.
struct OnboardingPermissionsScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var locationRequested = false

    var body: some View {
        OnboardingScaffold {
            VStack(alignment: .leading, spacing: 8) {
                Text("Welcome, \(store.firstName)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.green)
                Text("Two last settings")
                    .font(Theme.title(28))
                    .foregroundStyle(Theme.ink)
                    .accessibilityAddTraits(.isHeader)
                Text("You can say no: your plan still works, and you log your runs by hand.")
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.muted)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            locationCard
            notificationsCard
        }
    }

    private var locationCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                IconTile(systemName: "location.fill", tint: Theme.orangeDeep, background: Theme.orangeTint)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Location during your runs")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Text("To measure your distance and your pace. Only while you are running with the app.")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.muted)
                        .lineSpacing(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            Button(action: {
                LocationPermission.shared.request()
                locationRequested = true
            }) {
                Text(locationRequested ? "Requested" : "Allow location")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(locationRequested ? Theme.greenSoft : Theme.ink, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(locationRequested)
        }
        .padding(18)
        .card(radius: 22)
    }

    private var notificationsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 14) {
                IconTile(systemName: "bell.fill")
                VStack(alignment: .leading, spacing: 3) {
                    Text("Notifications")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Text("A reminder the day before each session, and nothing else. They arrive in a coming update.")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.muted)
                        .lineSpacing(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            Text("Later")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Theme.surface, in: Capsule())
        }
        .padding(18)
        .card(radius: 22)
    }
}

/// Screen 33: optional Apple Health connection, then into the app.
struct OnboardingHealthConnectScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var isAsking = false
    @State private var wasRefused = false

    private var connected: Bool { store.settings.healthImportEnabled }
    private var available: Bool { HealthKitService.shared.isAvailable }

    private var connectTitle: String {
        if connected { return "Connected" }
        if !available { return "Not available on this device" }
        return isAsking ? "Asking…" : "Connect Apple Health"
    }

    var body: some View {
        OnboardingScaffold(title: "Connect Apple Health",
                           subtitle: "Optional. I read your past runs, so your history starts full instead of empty.",
                           buttonTitle: "Start",
                           onContinue: { store.finishOnboarding() }) {
            healthCard
            if let workout = store.nextRun {
                firstSessionCard(workout)
            }
        }
    }

    private var healthCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            infoRow(symbol: "figure.run", tint: Theme.green, background: Theme.greenTint,
                    title: "Runs and distances", text: "The runs recorded by your iPhone or your watch")
            infoRow(symbol: "heart.fill", tint: Theme.orangeDeep, background: Theme.orangeTint,
                    title: "Heart rate", text: "The average of each run, when there is one")
            Button(action: connect) {
                HStack(spacing: 8) {
                    if connected {
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .bold))
                    }
                    Text(connectTitle)
                        .font(.system(size: 15, weight: .semibold))
                }
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(buttonColor, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(connected || !available || isAsking)
            Text(wasRefused
                 ? "Access was not granted. You can turn it on later in your profile."
                 : "You can remove the access at any time in your profile.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(18)
        .card(radius: 22)
    }

    private var buttonColor: Color {
        if connected { return Theme.greenSoft }
        return available && !isAsking ? Theme.ink : Theme.faint
    }

    private func infoRow(symbol: String, tint: Color, background: Color, title: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            IconTile(systemName: symbol, tint: tint, background: background)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Text(text)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
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
        isAsking = true
        Task {
            let granted = await HealthKitService.shared.requestAuthorization()
            store.settings.healthImportEnabled = granted
            wasRefused = !granted
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
