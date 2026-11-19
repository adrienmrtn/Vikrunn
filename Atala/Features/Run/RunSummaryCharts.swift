import SwiftUI

/// Kilometre by kilometre, as a table: the kilometre, a bar (the faster, the longer), its pace and
/// the gap to the average pace of the run.
struct RunSplitsCard: View {
    let splits: [RunKmSplit]
    let fastestID: Int?
    /// Average pace of the whole run, seconds per kilometre. 0 hides the gap column.
    var averagePace: Int = 0

    private var paces: [Int] { splits.map { $0.pace }.filter { $0 > 0 } }
    private var fastest: Int { paces.min() ?? 0 }
    private var slowest: Int { paces.max() ?? 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text(L("Splits"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                if let fastestID {
                    Text(L("Fastest: km %@", fastestID))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.green)
                }
            }
            columnTitles
            VStack(spacing: 0) {
                ForEach(splits) { split in
                    row(split)
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
    }

    private var columnTitles: some View {
        HStack(spacing: 10) {
            Text("km")
                .frame(width: 30, alignment: .leading)
            Spacer(minLength: 0)
            Text(L("Pace"))
                .frame(width: 52, alignment: .trailing)
            if averagePace > 0 {
                Text(L("vs avg"))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(width: 50, alignment: .trailing)
            }
        }
        .font(.system(size: 12, weight: .semibold))
        .foregroundStyle(Theme.muted)
        .accessibilityHidden(true)
    }

    private func label(_ split: RunKmSplit) -> String {
        split.isPartial ? String(format: "%.1f", split.distanceM / 1000) : "\(split.id)"
    }

    private func ratio(_ split: RunKmSplit) -> CGFloat {
        guard split.pace > 0, slowest > fastest else { return 1 }
        let position = CGFloat(slowest - split.pace) / CGFloat(slowest - fastest)
        return 0.4 + 0.6 * min(1, max(0, position))
    }

    /// "−0:12" when faster than the average, "+0:08" when slower.
    private func gap(_ split: RunKmSplit) -> String {
        let difference: Int = split.pace - averagePace
        let seconds: Int = abs(difference)
        let sign: String = difference < 0 ? "−" : "+"
        return sign + String(format: "%d:%02d", seconds / 60, seconds % 60)
    }

    private func row(_ split: RunKmSplit) -> some View {
        let isFastest: Bool = split.id == fastestID
        let isFaster: Bool = averagePace > 0 && split.pace > 0 && split.pace <= averagePace
        return HStack(spacing: 10) {
            Text(label(split))
                .font(.system(size: 14, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Theme.muted)
                .frame(width: 30, alignment: .leading)
            GeometryReader { geo in
                Capsule()
                    .fill(isFastest ? Theme.green : Theme.greenSoft.opacity(0.55))
                    .frame(width: max(8, geo.size.width * ratio(split)))
            }
            .frame(height: 10)
            Text(Fmt.pace(split.pace))
                .font(.system(size: 15, weight: isFastest ? .bold : .semibold))
                .monospacedDigit()
                .foregroundStyle(Theme.ink)
                .frame(width: 52, alignment: .trailing)
            if averagePace > 0 {
                Text(split.pace > 0 ? gap(split) : "")
                    .font(.system(size: 13, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(isFaster ? Theme.green : Theme.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(width: 50, alignment: .trailing)
            }
        }
        .frame(minHeight: 30)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.cardBorder).frame(height: 1)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Kilometre %@, %@ per kilometre", label(split), Fmt.pace(split.pace)))
    }
}

/// The pace along the run as a line: the higher the line, the faster. A dashed line marks the average.
struct RunPaceChartCard: View {
    let points: [RunPacePoint]
    /// Average pace of the whole run, seconds per kilometre.
    let averagePace: Int

    private var fastest: Double { points.map { $0.pace }.min() ?? 0 }
    private var slowest: Double { points.map { $0.pace }.max() ?? 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text(L("Pace"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                Text(L("Average %@/km", Fmt.pace(averagePace)))
                    .font(.system(size: 13, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.green)
            }
            GeometryReader { geo in
                ZStack {
                    line(in: geo.size, closed: true)
                        .fill(LinearGradient(colors: [Theme.green.opacity(0.30), Theme.green.opacity(0.03)],
                                             startPoint: .top, endPoint: .bottom))
                    averageLine(in: geo.size)
                        .stroke(Theme.faint, style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    line(in: geo.size, closed: false)
                        .stroke(Theme.green, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                }
            }
            .frame(height: 110)
            HStack {
                Text(L("Fastest %@", Fmt.pace(Int(fastest.rounded()))))
                Spacer(minLength: 8)
                Text(L("Slowest %@", Fmt.pace(Int(slowest.rounded()))))
            }
            .font(.system(size: 12))
            .monospacedDigit()
            .foregroundStyle(Theme.muted)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Pace along the run: average %@ per kilometre, fastest %@, slowest %@",
                              Fmt.pace(averagePace), Fmt.pace(Int(fastest.rounded())), Fmt.pace(Int(slowest.rounded()))))
    }

    /// Height of a pace in the chart, 0 at the bottom (slowest) and 1 at the top (fastest).
    private func level(_ pace: Double) -> CGFloat {
        // A very even run still gets a visible band: at least thirty seconds from bottom to top.
        let span: Double = max(30, slowest - fastest)
        let top: Double = fastest - (span - (slowest - fastest)) / 2
        return CGFloat(1 - (pace - top) / span)
    }

    private func height(_ pace: Double, in size: CGSize) -> CGFloat {
        // Keep a little room above and below the line.
        size.height * (0.92 - 0.84 * min(1, max(0, level(pace))))
    }

    private func line(in size: CGSize, closed: Bool) -> Path {
        var path = Path()
        guard points.count >= 2, let first = points.first, let last = points.last,
              last.distanceM > first.distanceM else { return path }
        let length: Double = last.distanceM - first.distanceM
        func position(_ point: RunPacePoint) -> CGPoint {
            let x: CGFloat = size.width * CGFloat((point.distanceM - first.distanceM) / length)
            return CGPoint(x: x, y: height(point.pace, in: size))
        }
        path.move(to: position(first))
        for point in points.dropFirst() {
            path.addLine(to: position(point))
        }
        if closed {
            path.addLine(to: CGPoint(x: size.width, y: size.height))
            path.addLine(to: CGPoint(x: 0, y: size.height))
            path.closeSubpath()
        }
        return path
    }

    private func averageLine(in size: CGSize) -> Path {
        var path = Path()
        guard averagePace > 0 else { return path }
        let y: CGFloat = height(Double(averagePace), in: size)
        path.move(to: CGPoint(x: 0, y: y))
        path.addLine(to: CGPoint(x: size.width, y: y))
        return path
    }
}

/// The ups and downs of the route, with the total climb.
struct RunElevationCard: View {
    let points: [RunElevationPoint]
    let gainM: Int

    private var lowest: Double { points.map { $0.altitudeM }.min() ?? 0 }
    private var highest: Double { points.map { $0.altitudeM }.max() ?? 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text(L("Elevation"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                Text("+\(gainM) m")
                    .font(.system(size: 15, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.orangeDeep)
            }
            GeometryReader { geo in
                ZStack {
                    profile(in: geo.size, closed: true)
                        .fill(LinearGradient(colors: [Theme.orange.opacity(0.35), Theme.orange.opacity(0.05)],
                                             startPoint: .top, endPoint: .bottom))
                    profile(in: geo.size, closed: false)
                        .stroke(Theme.orange, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                }
            }
            .frame(height: 92)
            HStack {
                Text(L("Lowest %@ m", Int(lowest.rounded())))
                Spacer(minLength: 8)
                Text(L("Highest %@ m", Int(highest.rounded())))
            }
            .font(.system(size: 12))
            .foregroundStyle(Theme.muted)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Elevation: %@ metres of climb", gainM))
    }

    /// The altitude line across the width; `closed` adds the area underneath.
    private func profile(in size: CGSize, closed: Bool) -> Path {
        var path = Path()
        guard points.count >= 2, let last = points.last, last.distanceM > 0 else { return path }
        // A flat run still gets a visible band: at least ten metres from bottom to top.
        let span = max(10, highest - lowest)
        let base = lowest - (span - (highest - lowest)) / 2
        func position(_ point: RunElevationPoint) -> CGPoint {
            let x = size.width * CGFloat(point.distanceM / last.distanceM)
            let level = CGFloat((point.altitudeM - base) / span)
            // Keep a little room above and below the line.
            let y = size.height * (0.92 - 0.84 * level)
            return CGPoint(x: x, y: y)
        }
        path.move(to: position(points[0]))
        for point in points.dropFirst() {
            path.addLine(to: position(point))
        }
        if closed {
            path.addLine(to: CGPoint(x: size.width, y: size.height))
            path.addLine(to: CGPoint(x: 0, y: size.height))
            path.closeSubpath()
        }
        return path
    }
}
