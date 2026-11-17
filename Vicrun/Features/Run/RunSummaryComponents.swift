import SwiftUI
import MapKit
import CoreLocation

/// The route of the run drawn on a map. Not interactive, so it scrolls with the page.
struct RunRouteMap: View {
    let route: [RoutePoint]

    private var coordinates: [CLLocationCoordinate2D] { route.map { $0.coordinate } }

    private var region: MKCoordinateRegion {
        let lats: [Double] = route.map { $0.lat }
        let lons: [Double] = route.map { $0.lon }
        let minLat = lats.min() ?? 0, maxLat = lats.max() ?? 0
        let minLon = lons.min() ?? 0, maxLon = lons.max() ?? 0
        let center = CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2)
        let span = MKCoordinateSpan(latitudeDelta: max(0.003, (maxLat - minLat) * 1.5),
                                    longitudeDelta: max(0.003, (maxLon - minLon) * 1.5))
        return MKCoordinateRegion(center: center, span: span)
    }

    var body: some View {
        Map(initialPosition: MapCameraPosition.region(region), interactionModes: []) {
            MapPolyline(coordinates: coordinates)
                .stroke(Theme.orange, lineWidth: 4)
            if let start = coordinates.first {
                Annotation("Start", coordinate: start) {
                    RunRouteDot(color: Theme.green)
                }
                .annotationTitles(.hidden)
            }
            if let end = coordinates.last {
                Annotation("Finish", coordinate: end) {
                    RunRouteDot(color: Theme.ink)
                }
                .annotationTitles(.hidden)
            }
        }
        .frame(height: 210)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(Theme.cardBorder, lineWidth: 1)
        )
        .accessibilityLabel("Map of your route")
    }
}

private struct RunRouteDot: View {
    let color: Color

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 14, height: 14)
            .overlay(Circle().stroke(Color.white, lineWidth: 3))
    }
}

/// Bars for the fast repetitions: the faster the rep, the taller the bar.
struct RunRepsCard: View {
    let splits: [RunSplit]

    private var paces: [Int] { splits.map { $0.pace } }
    private var fastest: Int { paces.filter { $0 > 0 }.min() ?? 0 }
    private var slowest: Int { paces.max() ?? 0 }
    private var onTargetCount: Int { splits.filter { $0.onTarget }.count }

    /// Distance-based reps show their time; time-based reps show their pace.
    private var showsTime: Bool {
        let distances: [Double] = splits.map { $0.distanceM }
        guard let shortest = distances.min(), let longest = distances.max(), shortest > 0 else { return false }
        return longest / shortest < 1.08
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("Your \(splits.count) fast reps")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                Text("\(onTargetCount) of \(splits.count) on target")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(onTargetCount * 2 >= splits.count ? Theme.green : Theme.orangeDeep)
            }
            bars
            Text(footnote)
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .card(radius: 24)
    }

    private var bars: some View {
        HStack(alignment: .bottom, spacing: splits.count > 8 ? 4 : 8) {
            ForEach(Array(splits.enumerated()), id: \.offset) { index, split in
                VStack(spacing: 6) {
                    Text(label(for: split))
                        .font(.system(size: 12, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(split.pace == fastest ? Theme.orangeDeep : Theme.orange)
                        .frame(height: barHeight(for: split))
                    Text("\(index + 1)")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 108, alignment: .bottom)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilitySummary)
    }

    private func label(for split: RunSplit) -> String {
        showsTime ? Fmt.duration(split.durationSec) : Fmt.pace(split.pace)
    }

    private func barHeight(for split: RunSplit) -> CGFloat {
        guard split.pace > 0, slowest > fastest else { return 55 }
        let ratio = CGFloat(slowest - split.pace) / CGFloat(slowest - fastest)
        return 40 + 25 * min(1, max(0, ratio))
    }

    private var averagePace: Int {
        let metres = splits.reduce(0.0) { $0 + $1.distanceM }
        let seconds = splits.reduce(0) { $0 + $1.durationSec }
        return metres > 0 ? Int(Double(seconds) / (metres / 1000)) : 0
    }

    private var footnote: String {
        var text: String
        if showsTime {
            let averageTime = splits.reduce(0) { $0 + $1.durationSec } / max(1, splits.count)
            text = "Average \(Fmt.duration(averageTime)), that is \(Fmt.pace(averagePace)) per km."
        } else {
            text = "Average \(Fmt.pace(averagePace)) per km."
        }
        if let first = splits.first, let fast = first.paceFast, let slow = first.paceSlow {
            text += " Target: \(Fmt.pace(fast)) to \(Fmt.pace(slow))."
        }
        return text
    }

    private var accessibilitySummary: String {
        "\(splits.count) fast reps, \(onTargetCount) on target. Fastest pace \(Fmt.pace(fastest)) per kilometre."
    }
}

/// A finished interval session, for previews.
enum RunSummarySample {
    static var run: RunRecord {
        let start = Date().addingTimeInterval(-3600)
        var splits: [RunSplit] = [RunSplit(label: "Easy pace", kind: .warmup, distanceM: 2550, durationSec: 900)]
        let times: [Int] = [211, 213, 210, 214, 212, 209]
        for time in times {
            splits.append(RunSplit(label: "800 m", kind: .work, distanceM: 800, durationSec: time, paceFast: 260, paceSlow: 270))
            splits.append(RunSplit(label: "Very slow jog", kind: .recovery, distanceM: 400, durationSec: 170))
        }
        var route: [RoutePoint] = []
        for index in 0..<120 {
            let angle = Double(index) / 120 * 2 * Double.pi
            route.append(RoutePoint(lat: 48.8606 + 0.004 * cos(angle),
                                    lon: 2.3376 + 0.006 * sin(angle),
                                    time: start.addingTimeInterval(Double(index) * 27)))
        }
        return RunRecord(workoutID: UUID(), title: "6 × 800 m", date: start, source: .gps,
                         distanceM: 9620, durationSec: 3288, avgHeartRate: nil,
                         route: route, splits: splits, feeling: .good)
    }
}
