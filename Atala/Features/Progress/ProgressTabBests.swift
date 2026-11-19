import SwiftUI

/// Personal bests, one row each, in one card.
struct ProgressTabBestsList: View {
    let bests: [ProgressTabBest]

    var body: some View {
        ProgressTabSection(title: L("Personal bests")) {
            VStack(alignment: .leading, spacing: 0) {
                if bests.isEmpty {
                    ProgressTabHint(systemName: "medal.fill",
                                    text: L("Your bests will appear here after your first run."))
                        .padding(.vertical, 18)
                }
                ForEach(Array(bests.enumerated()), id: \.element.id) { index, best in
                    row(best, isLast: index == bests.count - 1)
                }
            }
            .padding(.horizontal, 16)
            .progressTabCard(padding: 0)
        }
    }

    private func row(_ best: ProgressTabBest, isLast: Bool) -> some View {
        HStack(spacing: 12) {
            IconTile(systemName: ProgressTabStats.bestSymbol(best.id), size: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(best.title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(best.detail)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            Spacer(minLength: 8)
            Text(best.value)
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .layoutPriority(1)
        }
        .padding(.vertical, 12)
        .frame(minHeight: 60)
        .overlay(alignment: .bottom) {
            if !isLast {
                Rectangle().fill(Theme.hairline).frame(height: 1)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
