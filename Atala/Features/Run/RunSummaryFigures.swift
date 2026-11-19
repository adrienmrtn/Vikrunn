import SwiftUI

/// One figure of the summary grid.
struct RunSummaryFigure: Identifiable {
    let id: String
    let symbol: String
    let label: String
    let value: String
    let unit: String?
}

/// The key figures of a run: the distance in very large type, then a grid with the time and the
/// average pace (free), and with Atala Pro the elevation gain, the heart rate and the fastest
/// kilometre when the run has them.
struct RunSummaryFiguresCard: View {
    let run: RunRecord
    let analysis: RunAnalysis
    let isPro: Bool

    private var figures: [RunSummaryFigure] {
        var list: [RunSummaryFigure] = [
            RunSummaryFigure(id: "time", symbol: "stopwatch", label: L("Time"),
                             value: Fmt.duration(run.durationSec), unit: nil),
            RunSummaryFigure(id: "pace", symbol: "speedometer", label: L("Average pace"),
                             value: Fmt.pace(run.avgPace), unit: "/km"),
        ]
        guard isPro else { return list }
        if let gain = analysis.elevationGainM {
            list.append(RunSummaryFigure(id: "gain", symbol: "mountain.2.fill", label: L("Elevation gain"),
                                         value: "+\(gain)", unit: "m"))
        }
        if let heartRate = run.avgHeartRate {
            list.append(RunSummaryFigure(id: "hr", symbol: "heart.fill", label: L("Average heart rate"),
                                         value: "\(heartRate)", unit: "bpm"))
        }
        if let peak = run.maxHeartRate {
            list.append(RunSummaryFigure(id: "hrmax", symbol: "bolt.heart.fill", label: L("Max heart rate"),
                                         value: "\(peak)", unit: "bpm"))
        }
        if let fastest = analysis.fastestSplit {
            list.append(RunSummaryFigure(id: "fastest", symbol: "hare.fill", label: L("Fastest kilometre"),
                                         value: Fmt.pace(fastest.pace), unit: "/km"))
        }
        return list
    }

    var body: some View {
        let columns: [GridItem] = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
        let items: [RunSummaryFigure] = figures
        return VStack(alignment: .leading, spacing: 16) {
            distance
            Rectangle()
                .fill(Theme.cardBorder)
                .frame(height: 1)
            LazyVGrid(columns: columns, alignment: .leading, spacing: 18) {
                ForEach(items) { item in
                    cell(item)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
    }

    private var distance: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("Distance"))
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.muted)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(String(format: "%.2f", max(0, run.distanceM) / 1000))
                    .font(.system(size: 60, weight: .bold))
                    .monospacedDigit()
                    .tracking(-2)
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                Text("km")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Theme.green)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private func cell(_ item: RunSummaryFigure) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 5) {
                Image(systemName: item.symbol)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Theme.green)
                    .accessibilityHidden(true)
                Text(item.label)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(item.value)
                    .font(.system(size: 26, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                if let unit = item.unit {
                    Text(unit)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Top of the summary for a run without a route (entered by hand, treadmill, Apple Health, GPS
/// off): the title and the date on the brand green, where the map would be.
struct RunSummaryPlainHeader: View {
    let symbol: String
    let dateLine: String
    let title: String

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Theme.forest)
                .frame(width: 46, height: 46)
                .background(Theme.cream, in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(dateLine)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.greenOnDark)
                    .fixedSize(horizontal: false, vertical: true)
                Text(title)
                    .font(Theme.title(28))
                    .foregroundStyle(Color.white)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.forest, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// What a runner without Atala Pro sees in place of the detailed figures: the splits blurred
/// behind a padlock when the run has some, a simple locked row otherwise. A tap opens the paywall.
struct RunSummaryProTeaser: View {
    let splits: [RunKmSplit]
    let fastestID: Int?
    let onUnlock: () -> Void

    var body: some View {
        Button(action: {
            Haptics.tap()
            onUnlock()
        }) {
            if splits.count >= 2 {
                blurred
            } else {
                row
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("Splits, charts and heart rate: see the detail of every run with Atala Pro"))
    }

    private var blurred: some View {
        ZStack {
            RunSplitsCard(splits: Array(splits.prefix(6)), fastestID: fastestID)
                .blur(radius: 7)
                .overlay(Color.white.opacity(0.35))
                .accessibilityHidden(true)
            VStack(spacing: 10) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color.white)
                    .frame(width: 48, height: 48)
                    .background(Theme.forest, in: Circle())
                Text(L("Splits, charts and heart rate"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.ink)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Text(L("See the detail of every run with Atala Pro"))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.body)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Text(L("Unlock with Atala Pro"))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Color.white)
                    .padding(.horizontal, 18)
                    .frame(height: 42)
                    .background(Theme.forest, in: Capsule())
                    .padding(.top, 2)
            }
            .padding(16)
        }
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .contentShape(Rectangle())
    }

    private var row: some View {
        HStack(spacing: 14) {
            IconTile(systemName: "lock.fill", size: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(L("Splits, charts and heart rate"))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text(L("See the detail of every run with Atala Pro"))
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 4)
            RunProTag()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 22)
    }
}
