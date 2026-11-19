import SwiftUI
import MapKit
import CoreLocation

/// The part of the map that shows the whole route, with room around it.
enum RunRouteGeometry {
    static func coordinates(of route: [RoutePoint]) -> [CLLocationCoordinate2D] {
        route.map { $0.coordinate }
    }

    /// `padding` is the share of the route's size added around it (0.4 = 20 % on each side).
    static func region(of route: [RoutePoint], padding: Double) -> MKCoordinateRegion {
        let lats: [Double] = route.map { $0.lat }
        let lons: [Double] = route.map { $0.lon }
        let minLat: Double = lats.min() ?? 0
        let maxLat: Double = lats.max() ?? 0
        let minLon: Double = lons.min() ?? 0
        let maxLon: Double = lons.max() ?? 0
        let center = CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: (minLon + maxLon) / 2)
        let span = MKCoordinateSpan(latitudeDelta: max(0.003, (maxLat - minLat) * (1 + padding)),
                                    longitudeDelta: max(0.003, (maxLon - minLon) * (1 + padding)))
        return MKCoordinateRegion(center: center, span: span)
    }
}

/// The route drawn on a map: a white-edged orange line, a green dot at the start, a black one at
/// the finish.
private struct RunRouteMapLayer: View {
    let route: [RoutePoint]
    let interactive: Bool

    var body: some View {
        let coordinates: [CLLocationCoordinate2D] = RunRouteGeometry.coordinates(of: route)
        let region: MKCoordinateRegion = RunRouteGeometry.region(of: route, padding: interactive ? 0.35 : 0.5)
        let modes: MapInteractionModes = interactive ? .all : []
        return Map(initialPosition: MapCameraPosition.region(region), interactionModes: modes) {
            MapPolyline(coordinates: coordinates)
                .stroke(Color.white, lineWidth: 8)
            MapPolyline(coordinates: coordinates)
                .stroke(Theme.orange, lineWidth: 4.5)
            if let start = coordinates.first {
                Annotation(L("Start"), coordinate: start) {
                    RunRouteDot(color: Theme.green)
                }
                .annotationTitles(.hidden)
            }
            if let end = coordinates.last {
                Annotation(L("Finish"), coordinate: end) {
                    RunRouteDot(color: Theme.ink)
                }
                .annotationTitles(.hidden)
            }
        }
    }
}

/// The route of the run at the top of the summary. Not interactive, so it scrolls with the page;
/// a tap opens it full screen, where it can be moved and zoomed.
struct RunRouteMap: View {
    let route: [RoutePoint]
    /// "9.62 km", shown on the full-screen map.
    var caption: String = ""

    @State private var expanded = false

    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: 24, style: .continuous) }

    var body: some View {
        RunRouteMapLayer(route: route, interactive: false)
            .frame(height: 260)
            .clipShape(shape)
            .overlay(shape.stroke(Theme.cardBorder, lineWidth: 1))
            .overlay {
                Button(action: {
                    Haptics.tap()
                    expanded = true
                }) {
                    ZStack(alignment: .topTrailing) {
                        Color.clear
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(Theme.ink)
                            .frame(width: 40, height: 40)
                            .background(Color.white, in: Circle())
                            .shadow(color: Color.black.opacity(0.15), radius: 6, x: 0, y: 2)
                            .padding(12)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L("Map of your route"))
                .accessibilityHint(L("Opens the map full screen"))
            }
            .fullScreenCover(isPresented: $expanded) {
                RunRouteFullMap(route: route, caption: caption)
            }
    }
}

/// The route full screen: the map can be moved and zoomed. Closed with the round button.
struct RunRouteFullMap: View {
    let route: [RoutePoint]
    let caption: String

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            RunRouteMapLayer(route: route, interactive: true)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    closeButton
                    Spacer(minLength: 0)
                }
                Spacer(minLength: 0)
                if !caption.isEmpty {
                    Text(caption)
                        .font(.system(size: 17, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(Color.white)
                        .padding(.horizontal, 18)
                        .frame(height: 44)
                        .background(Theme.forest, in: Capsule())
                        .shadow(color: Color.black.opacity(0.18), radius: 8, x: 0, y: 3)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 12)
        }
    }

    private var closeButton: some View {
        Button(action: {
            Haptics.tap()
            dismiss()
        }) {
            Image(systemName: "xmark")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Theme.ink)
                .frame(width: 44, height: 44)
                .background(Color.white, in: Circle())
                .shadow(color: Color.black.opacity(0.18), radius: 8, x: 0, y: 3)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L("Close"))
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
                Text(L("Your %@ fast reps", splits.count))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                Text(L("%@ of %@ on target", onTargetCount, splits.count))
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
            text = L("Average %@, that is %@ per km.", Fmt.duration(averageTime), Fmt.pace(averagePace))
        } else {
            text = L("Average %@ per km.", Fmt.pace(averagePace))
        }
        if let first = splits.first, let fast = first.paceFast, let slow = first.paceSlow {
            text += " " + L("Target: %@ to %@.", Fmt.pace(fast), Fmt.pace(slow))
        }
        return text
    }

    private var accessibilitySummary: String {
        L("%@ fast reps, %@ on target. Fastest pace %@ per kilometre.", splits.count, onTargetCount, Fmt.pace(fastest))
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
