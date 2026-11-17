import SwiftUI
import UIKit

enum MainTab: Hashable {
    case today, plan, coach, progress
}

enum MainPlusAction {
    case freeRun, pastRun
}

/// The application shell: four tabs and a central "+" button.
struct MainTabView: View {
    @EnvironmentObject private var store: AppStore
    @StateObject private var chrome = MainChrome()
    @State private var tab: MainTab = .today
    @State private var showPlus = false
    @State private var pendingAction: MainPlusAction?
    @State private var showFreeRun = false
    @State private var showManualEntry = false
    @State private var keyboardVisible = false
    /// Drives the entrance of the tab bar, last block of the opening animation.
    @State private var tabBarRevealed = false

    var body: some View {
        VStack(spacing: 0) {
            tabContent
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            if !chrome.tabBarHidden && !keyboardVisible {
                MainTabBar(selection: $tab) { showPlus = true }
                    .mainStagedAppear(index: 9)
                    .environment(\.mainStagedRevealed, tabBarRevealed)
            }
        }
        .background(Color.white)
        .environmentObject(chrome)
        .onAppear {
            store.refreshMissed()
            revealTabBar()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            keyboardVisible = true
        }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            keyboardVisible = false
        }
        .sheet(isPresented: $showPlus, onDismiss: runPendingAction) {
            MainPlusSheet { action in
                pendingAction = action
                showPlus = false
            }
            .presentationDetents([.height(260)])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showManualEntry) {
            ManualRunEntryView(workout: nil)
                .environmentObject(store)
        }
        .sheet(isPresented: $chrome.showProfile) {
            ProfileView()
                .environmentObject(store)
                .environmentObject(chrome)
        }
        .fullScreenCover(isPresented: $showFreeRun) {
            RunFlowView(workout: nil)
                .environmentObject(store)
        }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch tab {
        case .today:
            TodayView()
        case .plan:
            PlanTabView()
        case .coach:
            CoachChatView()
        case .progress:
            ProgressTabView()
        }
    }

    /// The tab bar comes in once, after the blocks of the first screen (same delay as `MainStagedEntrance`).
    private func revealTabBar() {
        guard !tabBarRevealed else { return }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 120_000_000)
            tabBarRevealed = true
        }
    }

    /// Opens the chosen flow once the "+" sheet is fully dismissed.
    private func runPendingAction() {
        guard let action = pendingAction else { return }
        pendingAction = nil
        switch action {
        case .freeRun:
            showFreeRun = true
        case .pastRun:
            showManualEntry = true
        }
    }
}

// MARK: - Tab bar

private struct MainTabBar: View {
    @Binding var selection: MainTab
    let onPlus: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            item(.today, title: "Today", symbol: "house", selectedSymbol: "house.fill")
            item(.plan, title: "Plan", symbol: "calendar", selectedSymbol: "calendar")
            plusButton
            item(.coach, title: "Coach", symbol: "bubble.left", selectedSymbol: "bubble.left.fill")
            item(.progress, title: "Progress", symbol: "chart.bar", selectedSymbol: "chart.bar.fill")
        }
        .padding(.horizontal, 8)
        .padding(.top, 8)
        .padding(.bottom, 2)
        .frame(maxWidth: .infinity)
        .background(Color.white.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Theme.hairline)
                .frame(height: 1)
        }
    }

    private func item(_ tab: MainTab, title: String, symbol: String, selectedSymbol: String) -> some View {
        let selected = selection == tab
        return Button {
            selection = tab
        } label: {
            VStack(spacing: 4) {
                Image(systemName: selected ? selectedSymbol : symbol)
                    .font(.system(size: 20, weight: selected ? .semibold : .regular))
                    .frame(height: 24)
                Text(title)
                    .font(.system(size: 11, weight: selected ? .semibold : .regular))
            }
            .foregroundStyle(selected ? Theme.ink : Theme.muted)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var plusButton: some View {
        Button(action: onPlus) {
            Image(systemName: "plus")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 52, height: 52)
                .background(Theme.ink, in: Circle())
                .shadow(color: .black.opacity(0.18), radius: 6, x: 0, y: 3)
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity)
        .offset(y: -6)
        .accessibilityLabel("Add a run")
    }
}

// MARK: - "+" sheet

private struct MainPlusSheet: View {
    let onPick: (MainPlusAction) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Add a run")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.ink)
                .padding(.bottom, 2)
            row(title: "Start a free run",
                subtitle: "Outside the plan, tracked with GPS",
                systemName: "figure.run",
                action: .freeRun)
            row(title: "Add a past run",
                subtitle: "Treadmill or a run you did without the app",
                systemName: "square.and.pencil",
                action: .pastRun)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.white)
    }

    private func row(title: String, subtitle: String, systemName: String, action: MainPlusAction) -> some View {
        Button {
            onPick(action)
        } label: {
            HStack(spacing: 14) {
                IconTile(systemName: systemName)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.faint)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(minHeight: 68)
            .card()
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    MainTabView()
        .environmentObject(AppStore.preview)
}
