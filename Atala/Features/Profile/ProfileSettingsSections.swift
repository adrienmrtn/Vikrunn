import SwiftUI

/// Training paces of the current plan (read-only).
struct ProfilePacesSection: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        Section {
            if let plan = store.plan {
                row("Easy", plan.paces.easy)
                row("Long run", plan.paces.long)
                row("Tempo", plan.paces.tempo)
                row("Intervals", plan.paces.interval)
                row(plan.goal.isOpenEnded ? "Brisk" : "Race pace", plan.paces.race)
            } else {
                Text("Your paces appear once your plan is built.")
                    .foregroundStyle(Theme.muted)
            }
        } header: {
            Text("Training paces")
        } footer: {
            if store.plan != nil {
                Text("Calculated by your coach from your level and your goal. They follow your plan.")
            }
        }
    }

    private func row(_ title: String, _ pace: Int) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text("\(Fmt.pace(pace))/km")
                .foregroundStyle(Theme.muted)
                .monospacedDigit()
        }
        .accessibilityElement(children: .combine)
    }
}

/// Voice announcements during a run.
struct ProfileVoiceSection: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        Section {
            Toggle("Voice coach", isOn: $store.settings.voice.enabled)
            Group {
                Toggle("Step changes", isOn: $store.settings.voice.announceSteps)
                Toggle("Pace too fast or too slow", isOn: $store.settings.voice.announcePace)
                Toggle("Every kilometre", isOn: $store.settings.voice.announceEveryKm)
            }
            .disabled(!store.settings.voice.enabled)
        } header: {
            Text("Voice during runs")
        } footer: {
            Text("Your coach speaks over your music and stays quiet the rest of the time.")
        }
    }
}

/// Apple Health import.
struct ProfileHealthSection: View {
    @EnvironmentObject private var store: AppStore
    @State private var message: String?

    var body: some View {
        Section {
            Toggle("Import runs from Apple Health", isOn: $store.settings.healthImportEnabled)
                .disabled(!HealthKitService.shared.isAvailable)
                .onChange(of: store.settings.healthImportEnabled) { _, isOn in
                    if isOn {
                        enableImport()
                    } else {
                        message = nil
                    }
                }
        } header: {
            Text("Apple Health")
        } footer: {
            Text(footerText)
        }
    }

    private var footerText: String {
        if !HealthKitService.shared.isAvailable {
            return "Apple Health is not available on this device."
        }
        return message ?? "Runs recorded with a watch or another app are added to your history."
    }

    private func enableImport() {
        Task { @MainActor in
            let granted = await HealthKitService.shared.requestAuthorization()
            guard granted else {
                Haptics.error()
                store.settings.healthImportEnabled = false
                message = "Atala could not read Apple Health. You can allow it in the Health app, under Sharing."
                return
            }
            let added = await HealthKitService.shared.importRecentRuns(into: store)
            message = added == 0
                ? "Apple Health is connected. No new runs to import for now."
                : "\(added) \(added == 1 ? "run" : "runs") imported from Apple Health."
        }
    }
}

/// Subscription row and its placeholder sheet (the real paywall comes later).
struct ProfileSubscriptionSection: View {
    @State private var showSheet = false

    var body: some View {
        Section("Subscription") {
            Button {
                Haptics.tap()
                showSheet = true
            } label: {
                HStack {
                    Text("Atala Premium")
                        .foregroundStyle(Theme.ink)
                    Spacer()
                    Text("Free trial (demo)")
                        .foregroundStyle(Theme.muted)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.faint)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .sheet(isPresented: $showSheet) {
                ProfileSubscriptionSheet()
                    .presentationDetents([.medium])
                    .presentationDragIndicator(.visible)
            }
        }
    }
}

private struct ProfileSubscriptionSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            IconTile(systemName: "sparkles", size: 52)
            Text("Free trial (demo)")
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Theme.ink)
            Text("Everything is unlocked while we finish the app: your plan, your coach and the voice guidance. Subscriptions are not available yet, and nothing will be charged.")
                .font(.system(size: 15))
                .lineSpacing(4)
                .foregroundStyle(Theme.body)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
            PrimaryButton(title: "Got it") { dismiss() }
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 32)
        .padding(.bottom, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.white)
    }
}
