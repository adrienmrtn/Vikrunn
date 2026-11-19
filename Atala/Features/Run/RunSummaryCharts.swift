import SwiftUI

/// Kilometre by kilometre: the faster the kilometre, the longer its bar.
struct RunSplitsCard: View {
    let splits: [RunKmSplit]
    let fastestID: Int?

    private var paces: [Int] { splits.map { $0.pace }.filter { $0 > 0 } }
    private var fastest: Int { paces.min() ?? 0 }
    private var slowest: Int { paces.max() ?? 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                Text(L("Pace per kilometre"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                if let fastestID {
                    Text(L("Fastest: km %@", fastestID))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.green)
                }
            }
            VStack(spacing: 9) {
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

    private func label(_ split: RunKmSplit) -> String {
        split.isPartial ? String(format: "%.1f", split.distanceM / 1000) : "\(split.id)"
    }

    private func ratio(_ split: RunKmSplit) -> CGFloat {
        guard split.pace > 0, slowest > fastest else { return 1 }
        let position = CGFloat(slowest - split.pace) / CGFloat(slowest - fastest)
        return 0.4 + 0.6 * min(1, max(0, position))
    }

    private func row(_ split: RunKmSplit) -> some View {
        let isFastest = split.id == fastestID
        return HStack(spacing: 10) {
            Text(label(split))
                .font(.system(size: 13, weight: .semibold))
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
                .font(.system(size: 14, weight: isFastest ? .bold : .semibold))
                .monospacedDigit()
                .foregroundStyle(Theme.ink)
                .frame(width: 48, alignment: .trailing)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Kilometre %@, %@ per kilometre", label(split), Fmt.pace(split.pace)))
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
