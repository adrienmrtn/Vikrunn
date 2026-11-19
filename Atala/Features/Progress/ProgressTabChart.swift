import SwiftUI

/// Weekly distance of the last weeks: one bar per week, the current one highlighted, the planned
/// distance of the plan weeks as a light track behind, and a dashed line at the average.
struct ProgressTabChart: View {
    let weeks: [ProgressTabWeek]
    let showsPlanned: Bool
    @State private var selectedID: Date?
    @Environment(\.mainStagedRevealed) private var revealed
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let chartHeight: CGFloat = 120
    private let pastColor = Color(hex: 0x8DB8A8)

    var body: some View {
        let average: Double = averageKm
        let top: Double = topKm
        ProgressTabSection(title: L("Weekly distance"), trailing: trailingText(average: average)) {
            VStack(alignment: .leading, spacing: 12) {
                if hasDistance {
                    headline
                    bars(top: top, average: average)
                    axis
                    if showsPlanned {
                        legend
                    }
                } else {
                    ProgressTabHint(systemName: "chart.bar.fill",
                                    text: L("Your weekly distance builds up here, one bar per week."))
                }
            }
            .progressTabCard()
        }
    }

    // MARK: Figures

    private var hasDistance: Bool {
        weeks.contains { $0.km > 0 }
    }

    /// Average of the weeks before the current one, which is still under way.
    private var averageKm: Double {
        let past: [ProgressTabWeek] = weeks.filter { !$0.isCurrent }
        guard !past.isEmpty else { return 0 }
        let total: Double = past.reduce(0) { $0 + $1.km }
        return total / Double(past.count)
    }

    private var topKm: Double {
        let done: Double = weeks.map { $0.km }.max() ?? 0
        let planned: Double = showsPlanned ? (weeks.map { $0.plannedKm }.max() ?? 0) : 0
        return max(1, max(done, planned))
    }

    private var shownWeek: ProgressTabWeek? {
        if let selectedID, let week = weeks.first(where: { $0.monday == selectedID }) {
            return week
        }
        return weeks.last
    }

    private func trailingText(average: Double) -> String? {
        guard hasDistance, average > 0 else { return nil }
        return L("Average %@ km",ProgressTabStats.number(average))
    }

    // MARK: Parts

    @ViewBuilder
    private var headline: some View {
        if let week = shownWeek {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(ProgressTabStats.number(week.km))
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(Theme.ink)
                Text("km")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.muted)
                Spacer(minLength: 8)
                Text(week.isCurrent ? L("This week") : L("Week of %@", Fmt.day(week.monday, "d MMM")))
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
            }
            .accessibilityElement(children: .combine)
        }
    }

    private func bars(top: Double, average: Double) -> some View {
        let grown: Bool = revealed || reduceMotion
        let averageOffset: CGFloat = -chartHeight * CGFloat(min(average / top, 1))
        return ZStack(alignment: .bottom) {
            HStack(alignment: .bottom, spacing: 6) {
                ForEach(Array(weeks.enumerated()), id: \.element.id) { index, week in
                    bar(week, index: index, top: top, grown: grown)
                }
            }
            if average > 0 {
                ProgressTabDashedLine()
                    .stroke(Theme.ink.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    .frame(height: 1)
                    .offset(y: averageOffset)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .frame(height: chartHeight, alignment: .bottom)
    }

    private func bar(_ week: ProgressTabWeek, index: Int, top: Double, grown: Bool) -> some View {
        let fullHeight: CGFloat = week.km > 0 ? max(4, chartHeight * CGFloat(week.km / top)) : 0
        let doneHeight: CGFloat = grown ? fullHeight : 0
        let plannedHeight: CGFloat = showsPlanned && week.plannedKm > 0 ? max(4, chartHeight * CGFloat(week.plannedKm / top)) : 0
        let isSelected: Bool = selectedID == week.monday
        let fill: Color = (week.isCurrent || isSelected) ? Theme.green : pastColor
        let growth: Animation? = reduceMotion ? nil : .spring(response: 0.55, dampingFraction: 0.85).delay(0.2 + Double(index) * 0.03)
        return ZStack(alignment: .bottom) {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
                .fill(Theme.greenTint)
                .frame(height: plannedHeight)
            RoundedRectangle(cornerRadius: 5, style: .continuous)
                .fill(fill)
                .frame(height: doneHeight)
                .animation(growth, value: grown)
        }
        .frame(maxWidth: .infinity)
        .frame(height: chartHeight, alignment: .bottom)
        .contentShape(Rectangle())
        .onTapGesture {
            Haptics.select()
            selectedID = isSelected ? nil : week.monday
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(barLabel(week))
    }

    private func barLabel(_ week: ProgressTabWeek) -> String {
        let date: String = Fmt.day(week.monday, "d MMMM")
        if showsPlanned && week.plannedKm > 0 {
            return L("Week of %@: %@ of %@ kilometres", date, ProgressTabStats.number(week.km), ProgressTabStats.number(week.plannedKm))
        }
        return L("Week of %@: %@ kilometres", date, ProgressTabStats.number(week.km))
    }

    private var axis: some View {
        HStack {
            if let first = weeks.first {
                Text(Fmt.day(first.monday, "d MMM"))
            }
            Spacer(minLength: 8)
            Text(L("This week"))
                .fontWeight(.semibold)
                .foregroundStyle(Theme.ink)
        }
        .font(.system(size: 11))
        .foregroundStyle(Theme.muted)
        .accessibilityHidden(true)
    }

    private var legend: some View {
        HStack(spacing: 14) {
            legendItem(color: Theme.greenTint, title: L("Planned"))
            legendItem(color: Theme.green, title: L("Done"))
            Spacer(minLength: 0)
        }
    }

    private func legendItem(color: Color, title: String) -> some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(color)
                .frame(width: 10, height: 10)
            Text(title)
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
        }
    }
}

/// A horizontal line across its frame, to be stroked with a dash.
private struct ProgressTabDashedLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        return path
    }
}
