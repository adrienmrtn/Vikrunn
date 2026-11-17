import SwiftUI

/// Run history, with the Apple Health import.
struct ProgressTabHistory: View {
    @EnvironmentObject private var store: AppStore
    @State private var importing = false
    @State private var importMessage: String?
    @State private var showAll = false

    private let collapsedCount = 10

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            MainSectionTitle(title: "History", trailing: countText)
                .padding(.bottom, 4)
            if store.runs.isEmpty {
                MainEmptyState(systemName: "figure.run",
                               title: "No runs yet",
                               message: "Your runs will appear here. Start one with the + button, or bring in the ones you already have in Apple Health.")
            }
            ForEach(visibleRuns) { run in
                NavigationLink {
                    RunSummaryView(run: run)
                        .modifier(MainHidesTabBar())
                } label: {
                    ProgressTabRunRow(run: run)
                }
                .buttonStyle(.plain)
            }
            if store.runs.count > collapsedCount {
                Button(showAll ? "Show fewer" : "Show all \(store.runs.count) runs") {
                    showAll.toggle()
                }
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.green)
                .frame(maxWidth: .infinity, minHeight: 44)
            }
            importButton
                .padding(.top, 14)
            if let importMessage {
                Text(importMessage)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)
            }
        }
    }

    private var sortedRuns: [RunRecord] {
        store.runs.sorted { $0.date > $1.date }
    }

    private var visibleRuns: [RunRecord] {
        showAll ? sortedRuns : Array(sortedRuns.prefix(collapsedCount))
    }

    private var countText: String? {
        store.runs.isEmpty ? nil : "\(store.runs.count) \(store.runs.count == 1 ? "run" : "runs")"
    }

    private var importButton: some View {
        Button {
            importFromHealth()
        } label: {
            HStack(spacing: 8) {
                if importing {
                    ProgressView()
                        .tint(Theme.ink)
                } else {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.orange)
                }
                Text(importing ? "Importing…" : "Import from Apple Health")
            }
        }
        .buttonStyle(SecondaryButtonStyle())
        .disabled(importing)
    }

    private func importFromHealth() {
        guard !importing else { return }
        guard HealthKitService.shared.isAvailable else {
            importMessage = "Apple Health is not available on this device."
            return
        }
        importing = true
        importMessage = nil
        Task { @MainActor in
            let granted = await HealthKitService.shared.requestAuthorization()
            var added = 0
            if granted {
                added = await HealthKitService.shared.importRecentRuns(into: store)
            }
            importing = false
            if !granted {
                importMessage = "Vicrun could not read Apple Health. You can allow it in the Health app, under Sharing."
            } else if added == 0 {
                importMessage = "No new runs found in Apple Health."
            } else {
                importMessage = "\(added) \(added == 1 ? "run" : "runs") imported from Apple Health."
            }
        }
    }
}

/// One run of the history.
private struct ProgressTabRunRow: View {
    let run: RunRecord

    var body: some View {
        HStack(spacing: 12) {
            IconTile(systemName: ProgressTabStats.sourceSymbol(run.source), size: 38)
            VStack(alignment: .leading, spacing: 1) {
                Text(run.title.isEmpty ? "Run" : run.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                Text("\(Fmt.day(run.date, "EEE d MMM")) · \(ProgressTabStats.sourceTitle(run.source))")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 1) {
                Text(Fmt.km(run.distanceKm))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Text("\(Fmt.duration(run.durationSec)) · \(Fmt.pace(run.avgPace))/km")
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.muted)
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.faint)
        }
        .frame(minHeight: 58)
        .contentShape(Rectangle())
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.hairline).frame(height: 1)
        }
        .accessibilityElement(children: .combine)
    }
}
