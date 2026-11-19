import SwiftUI

/// Run history, with the Apple Health import.
struct ProgressTabHistory: View {
    @EnvironmentObject private var store: AppStore
    /// The runs to list: the saved ones, or the sample ones behind the blur of the locked tab.
    let runs: [RunRecord]
    /// True for the sample runs, which open nothing.
    let isSample: Bool
    @State private var importing = false
    @State private var importMessage: String?
    @State private var showAll = false

    private let collapsedCount = 10

    var body: some View {
        ProgressTabSection(title: L("History"), trailing: countText) {
            VStack(alignment: .leading, spacing: 0) {
                if runs.isEmpty {
                    MainEmptyState(systemName: "figure.run",
                                   title: L("No runs yet"),
                                   message: L("Tap + to record a run, or bring in the ones you already have in Apple Health."))
                } else {
                    list
                }
            }
            .progressTabCard(padding: 0)
            if runs.count > collapsedCount {
                Button(showAll ? L("Show fewer") : L("Show all %@ runs", runs.count)) {
                    Haptics.tap()
                    showAll.toggle()
                }
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.green)
                .frame(maxWidth: .infinity, minHeight: 44)
            }
            importButton
                .padding(.top, 4)
            if let importMessage {
                Text(importMessage)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private var list: some View {
        let visible: [RunRecord] = visibleRuns
        let lastID: UUID? = visible.last?.id
        return VStack(spacing: 0) {
            ForEach(visible) { run in
                if isSample {
                    ProgressTabRunRow(run: run, isLast: run.id == lastID)
                } else {
                    NavigationLink {
                        MainRunSummaryHost(run: run)
                    } label: {
                        ProgressTabRunRow(run: run, isLast: run.id == lastID)
                    }
                    .buttonStyle(.plain)
                    .hapticOnTap()
                }
            }
        }
        .padding(.horizontal, 16)
    }

    private var sortedRuns: [RunRecord] {
        runs.sorted { $0.date > $1.date }
    }

    private var visibleRuns: [RunRecord] {
        showAll ? sortedRuns : Array(sortedRuns.prefix(collapsedCount))
    }

    private var countText: String? {
        runs.isEmpty ? nil : (runs.count == 1 ? L("%@ run", runs.count) : L("%@ runs", runs.count))
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
                Text(importing ? L("Importing…") : L("Import from Apple Health"))
            }
        }
        .buttonStyle(SecondaryButtonStyle())
        .disabled(importing)
    }

    private func importFromHealth() {
        guard !importing else { return }
        guard HealthKitService.shared.isAvailable else {
            importMessage = L("Apple Health is not available on this device.")
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
                Haptics.error()
            } else if added > 0 {
                Haptics.success()
            }
            if !granted {
                importMessage = L("Atala could not read Apple Health. You can allow it in the Health app, under Sharing.")
            } else if added == 0 {
                importMessage = L("No new runs found in Apple Health.")
            } else {
                importMessage = added == 1 ? L("%@ run imported from Apple Health.", added) : L("%@ runs imported from Apple Health.", added)
            }
        }
    }
}

/// One run of the history: the day, the title with time and pace, the source kept quiet, and the
/// distance as the figure that stands out.
private struct ProgressTabRunRow: View {
    let run: RunRecord
    let isLast: Bool

    var body: some View {
        HStack(spacing: 12) {
            dateTile
            VStack(alignment: .leading, spacing: 3) {
                Text(run.title.isEmpty ? L("Run") : L(run.title))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                Text("\(Fmt.duration(run.durationSec)) · \(Fmt.pace(run.avgPace))/km")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
                HStack(spacing: 4) {
                    Image(systemName: ProgressTabStats.sourceSymbol(run.source))
                        .font(.system(size: 9, weight: .semibold))
                    Text(ProgressTabStats.sourceTitle(run.source))
                        .font(.system(size: 11))
                        .lineLimit(1)
                }
                .foregroundStyle(Theme.faint)
            }
            Spacer(minLength: 8)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(ProgressTabStats.number(run.distanceKm))
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.ink)
                Text("km")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.muted)
            }
            .lineLimit(1)
            .layoutPriority(1)
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.faint)
                .accessibilityHidden(true)
        }
        .padding(.vertical, 12)
        .frame(minHeight: 72)
        .contentShape(Rectangle())
        .overlay(alignment: .bottom) {
            if !isLast {
                Rectangle().fill(Theme.hairline).frame(height: 1)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private var dateTile: some View {
        VStack(spacing: 0) {
            Text(Fmt.day(run.date, "d"))
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.ink)
            Text(Fmt.day(run.date, "MMM"))
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Theme.muted)
        }
        .lineLimit(1)
        .minimumScaleFactor(0.7)
        .frame(width: 44, height: 46)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var accessibilityText: String {
        let title: String = run.title.isEmpty ? L("Run") : L(run.title)
        let date: String = Fmt.day(run.date, "EEEE d MMMM")
        let figures: String = "\(Fmt.km(run.distanceKm)), \(Fmt.duration(run.durationSec)), \(Fmt.pace(run.avgPace))/km"
        return "\(title), \(date), \(figures), \(ProgressTabStats.sourceTitle(run.source))"
    }
}
