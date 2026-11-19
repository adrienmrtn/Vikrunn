import SwiftUI
import UIKit

/// Training paces of the current plan (read-only).
struct ProfilePacesSection: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        Section {
            if let plan = store.plan {
                row(L("Easy"), plan.paces.easy)
                row(L("Long run"), plan.paces.long)
                row(L("Tempo"), plan.paces.tempo)
                row(L("Intervals"), plan.paces.interval)
                row(plan.goal.isOpenEnded ? L("Brisk") : L("Race pace"), plan.paces.race)
            } else {
                Text(L("Your paces appear once your plan is built."))
                    .foregroundStyle(Theme.muted)
            }
        } header: {
            Text(L("Training paces"))
        } footer: {
            if store.plan != nil {
                Text(L("Calculated by your coach from your level and your goal. They follow your plan."))
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
            Toggle(L("Voice coach"), isOn: $store.settings.voice.enabled)
            Group {
                Toggle(L("Step changes"), isOn: $store.settings.voice.announceSteps)
                Toggle(L("Pace too fast or too slow"), isOn: $store.settings.voice.announcePace)
                Toggle(L("Every kilometre"), isOn: $store.settings.voice.announceEveryKm)
            }
            .disabled(!store.settings.voice.enabled)
        } header: {
            Text(L("Voice during runs"))
        } footer: {
            Text(L("Your coach speaks over your music and stays quiet the rest of the time."))
        }
    }
}

/// Apple Health import.
struct ProfileHealthSection: View {
    @EnvironmentObject private var store: AppStore
    @State private var message: String?

    var body: some View {
        Section {
            Toggle(L("Import runs from Apple Health"), isOn: $store.settings.healthImportEnabled)
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
            return L("Apple Health is not available on this device.")
        }
        return message ?? L("Runs recorded with a watch or another app are added to your history.")
    }

    private func enableImport() {
        Task { @MainActor in
            let granted = await HealthKitService.shared.requestAuthorization()
            guard granted else {
                Haptics.error()
                store.settings.healthImportEnabled = false
                message = L("Atala could not read Apple Health. You can allow it in the Health app, under Sharing.")
                return
            }
            let added = await HealthKitService.shared.importRecentRuns(into: store)
            message = added == 0
                ? L("Apple Health is connected. No new runs to import for now.")
                : (added == 1 ? L("%@ run imported from Apple Health.", added) : L("%@ runs imported from Apple Health.", added))
        }
    }
}

/// Subscription row: the paywall for someone on the free plan, the App Store's own page for a subscriber.
struct ProfileSubscriptionSection: View {
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var showPaywall = false

    var body: some View {
        Section(L("Subscription")) {
            Button {
                Haptics.tap()
                if purchases.isPro {
                    if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
                        UIApplication.shared.open(url)
                    }
                } else {
                    showPaywall = true
                }
            } label: {
                HStack(spacing: 8) {
                    Text("Atala Pro")
                        .foregroundStyle(Theme.ink)
                    if purchases.isPro {
                        ProChip()
                    }
                    Spacer()
                    Text(purchases.isPro ? L("Manage") : L("Free plan · Unlock"))
                        .foregroundStyle(purchases.isPro ? Theme.muted : Theme.green)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.faint)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .proPaywall(isPresented: $showPaywall)
            if !purchases.isPro, !PurchaseService.isPlaceholder {
                Button(L("Restore purchases")) {
                    Haptics.tap()
                    Task { _ = await purchases.restore() }
                }
                .foregroundStyle(Theme.ink)
            }
            Button(L("Session reminders")) {
                Haptics.tap()
                Task { @MainActor in
                    // First time: the system question. Afterwards the choice lives in Settings.
                    var openSettings = await RunReminders.isAuthorized()
                    if !openSettings {
                        let granted = await RunReminders.requestAuthorization()
                        openSettings = !granted
                    }
                    if openSettings, let url = URL(string: UIApplication.openSettingsURLString) {
                        // Inside a task, the system picks the asynchronous form of `open`.
                        _ = await UIApplication.shared.open(url)
                    }
                }
            }
            .foregroundStyle(Theme.ink)
            ProfileLanguageRow()
            if let terms = AtalaLinks.terms {
                Link(L("Terms of use"), destination: terms)
                    .foregroundStyle(Theme.ink)
            }
            if let privacy = AtalaLinks.privacy {
                Link(L("Privacy policy"), destination: privacy)
                    .foregroundStyle(Theme.ink)
            }
        }
    }
}

/// Language row: the current language's own name, and a sheet with the list to pick another one.
@MainActor
private struct ProfileLanguageRow: View {
    @State private var showLanguages = false
    @State private var selection: String = AppLanguage.current

    private var currentName: String {
        AppLanguage.all.first(where: { $0.code == AppLanguage.current })?.name ?? ""
    }

    var body: some View {
        Button {
            Haptics.tap()
            selection = AppLanguage.current
            showLanguages = true
        } label: {
            HStack(spacing: 8) {
                Text(L("Language"))
                    .foregroundStyle(Theme.ink)
                Spacer()
                Text(currentName)
                    .foregroundStyle(Theme.muted)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.faint)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showLanguages) {
            NavigationStack {
                ScrollView {
                    LanguageList(selection: $selection)
                        .padding(.horizontal, Theme.screenPadding)
                        .padding(.vertical, 12)
                }
                .navigationTitle(L("Language"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(L("Done")) {
                            Haptics.tap()
                            showLanguages = false
                        }
                        .fontWeight(.semibold)
                    }
                }
            }
            .tint(Theme.green)
            .onChange(of: selection) { _, code in
                // Rebuilds the app in the new language, which also closes this sheet.
                LanguageModel.shared.choose(code)
            }
        }
    }
}

/// Help: what to put in the message, then the support address.
struct ProfileHelpSection: View {
    @State private var copied = false

    var body: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Text(L("Before you write, please include:"))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                ProfileHelpLine(text: L("Your phone model"))
                ProfileHelpLine(text: L("Your subscription"))
                ProfileHelpLine(text: L("Your country"))
                ProfileHelpLine(text: L("For a bug, attach a screenshot or a screen recording"))
            }
            .padding(.vertical, 4)
            .accessibilityElement(children: .combine)
            Button {
                Haptics.tap()
                if let url = AtalaLinks.supportMail {
                    UIApplication.shared.open(url) { opened in
                        // No mail app set up (the simulator, for instance): copy the address instead.
                        if !opened {
                            UIPasteboard.general.string = AtalaLinks.supportEmail
                            copied = true
                        }
                    }
                }
            } label: {
                HStack {
                    Image(systemName: "envelope.fill")
                        .foregroundStyle(Theme.green)
                    Text(AtalaLinks.supportEmail)
                        .foregroundStyle(Theme.ink)
                    Spacer()
                    Text(copied ? L("Copied") : L("Write to us"))
                        .foregroundStyle(Theme.muted)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.faint)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        } header: {
            Text(L("Help"))
        }
    }
}

private struct ProfileHelpLine: View {
    let text: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text("•")
                .foregroundStyle(Theme.muted)
            Text(text)
                .font(.system(size: 15))
                .foregroundStyle(Theme.body)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
