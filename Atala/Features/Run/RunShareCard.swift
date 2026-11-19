import SwiftUI
import UIKit

/// The two looks of the picture shared from a run: forest green, or white.
enum RunShareStyle: String, CaseIterable, Identifiable {
    case dark, light

    var id: String { rawValue }

    var name: String {
        switch self {
        case .dark: return L("Dark")
        case .light: return L("Light")
        }
    }

    var background: Color {
        switch self {
        case .dark: return Theme.forest
        case .light: return Color.white
        }
    }

    /// Titles and figures.
    var primary: Color {
        switch self {
        case .dark: return Color.white
        case .light: return Theme.forest
        }
    }

    /// Date and captions.
    var secondary: Color {
        switch self {
        case .dark: return Theme.greenOnDark
        case .light: return Theme.muted
        }
    }

    var route: Color { Theme.orange }

    /// Ring around the start and finish dots: the colour of the card behind them.
    var dotRing: Color { background }

    var startDot: Color {
        switch self {
        case .dark: return Color.white
        case .light: return Theme.green
        }
    }

    var hairline: Color {
        switch self {
        case .dark: return Color.white.opacity(0.16)
        case .light: return Theme.cardBorder
        }
    }

    var logo: Color {
        switch self {
        case .dark: return Theme.cream
        case .light: return Theme.forest
        }
    }
}

/// The GPS points of a run laid flat: longitudes squeezed by the cosine of the latitude so that
/// shapes keep their proportions (equirectangular projection), north at the top.
struct RunShareProjection {
    /// Projected points; x grows to the east, y grows to the south, the smallest of each is 0.
    let points: [CGPoint]
    /// Width and height covered by the points, in the same unit.
    let size: CGSize

    /// Nil when there is no route to draw (fewer than two points, or no movement).
    init?(route: [RoutePoint]) {
        guard route.count >= 2 else { return nil }
        let kept: [RoutePoint] = Self.thinned(route, to: 600)
        let lats: [Double] = kept.map { $0.lat }
        let lons: [Double] = kept.map { $0.lon }
        guard let minLat = lats.min(), let maxLat = lats.max(),
              let minLon = lons.min(), let maxLon = lons.max() else { return nil }
        let middle: Double = (minLat + maxLat) / 2
        let squeeze: Double = max(0.01, cos(middle * Double.pi / 180))
        let width: Double = (maxLon - minLon) * squeeze
        let height: Double = maxLat - minLat
        // Less than a few metres in every direction: nothing worth drawing.
        guard max(width, height) > 0.00003 else { return nil }
        var projected: [CGPoint] = []
        for point in kept {
            let x: Double = (point.lon - minLon) * squeeze
            let y: Double = maxLat - point.lat
            projected.append(CGPoint(x: x, y: y))
        }
        points = projected
        size = CGSize(width: width, height: height)
    }

    /// The points scaled to fit `rect`, proportions kept, centred.
    func fitted(in rect: CGRect) -> [CGPoint] {
        let width: CGFloat = max(size.width, 0.000000001)
        let height: CGFloat = max(size.height, 0.000000001)
        let scale: CGFloat = min(rect.width / width, rect.height / height)
        let offsetX: CGFloat = rect.minX + (rect.width - size.width * scale) / 2
        let offsetY: CGFloat = rect.minY + (rect.height - size.height * scale) / 2
        var result: [CGPoint] = []
        for point in points {
            result.append(CGPoint(x: offsetX + point.x * scale, y: offsetY + point.y * scale))
        }
        return result
    }

    private static func thinned(_ route: [RoutePoint], to limit: Int) -> [RoutePoint] {
        guard route.count > limit, limit > 1 else { return route }
        var result: [RoutePoint] = []
        let step: Double = Double(route.count - 1) / Double(limit - 1)
        for index in 0..<limit {
            let position: Int = min(route.count - 1, Int((Double(index) * step).rounded()))
            result.append(route[position])
        }
        return result
    }
}

/// The route as one line, fitted in the shape's frame with a margin.
struct RunShareRouteLine: Shape {
    let projection: RunShareProjection
    var margin: CGFloat = 10

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let fitted: [CGPoint] = projection.fitted(in: rect.insetBy(dx: margin, dy: margin))
        guard let first = fitted.first else { return path }
        path.move(to: first)
        for point in fitted.dropFirst() {
            path.addLine(to: point)
        }
        return path
    }
}

/// A round dot at the start or at the finish of the route.
struct RunShareRouteDot: Shape {
    let projection: RunShareProjection
    let atStart: Bool
    var radius: CGFloat = 6
    var margin: CGFloat = 10

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let fitted: [CGPoint] = projection.fitted(in: rect.insetBy(dx: margin, dy: margin))
        guard let centre = atStart ? fitted.first : fitted.last else { return path }
        path.addEllipse(in: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2, height: radius * 2))
        return path
    }
}

/// The route drawn large: a soft halo, the line, then the start and finish dots.
struct RunShareRouteView: View {
    let projection: RunShareProjection
    let style: RunShareStyle

    var body: some View {
        ZStack {
            RunShareRouteLine(projection: projection)
                .stroke(style.route.opacity(0.22), style: StrokeStyle(lineWidth: 13, lineCap: .round, lineJoin: .round))
            RunShareRouteLine(projection: projection)
                .stroke(style.route, style: StrokeStyle(lineWidth: 4.5, lineCap: .round, lineJoin: .round))
            RunShareRouteDot(projection: projection, atStart: true, radius: 8)
                .fill(style.dotRing)
            RunShareRouteDot(projection: projection, atStart: true, radius: 5)
                .fill(style.startDot)
            RunShareRouteDot(projection: projection, atStart: false, radius: 8)
                .fill(style.dotRing)
            RunShareRouteDot(projection: projection, atStart: false, radius: 5)
                .fill(style.route)
        }
    }
}

/// The picture shared from a run, portrait 4:5 (360 × 450 points, 1080 × 1350 pixels at scale 3):
/// title and date, the route drawn large from the GPS points, three big figures and the Atala logo.
/// A run without a route shows its figures only. Self-contained (no environment), so it can be
/// rendered off screen.
struct RunShareCard: View {
    let run: RunRecord
    let style: RunShareStyle

    static let width: CGFloat = 360
    static let height: CGFloat = 450

    private var projection: RunShareProjection? { RunShareProjection(route: run.route) }

    private var distanceValue: String { String(format: "%.2f", max(0, run.distanceM) / 1000) }
    private var paceValue: String { run.avgPace > 0 ? Fmt.pace(run.avgPace) : "--" }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            heading
            if let projection {
                RunShareRouteView(projection: projection, style: style)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.vertical, 10)
                figureRow(showsDistance: true)
            } else {
                Spacer(minLength: 0)
                hero
                Spacer(minLength: 0)
                figureRow(showsDistance: false)
            }
            Rectangle()
                .fill(style.hairline)
                .frame(height: 1)
                .padding(.top, 18)
            BrandLogo(style: .full, height: 16)
                .foregroundStyle(style.logo)
                .padding(.top, 14)
        }
        .padding(.horizontal, 26)
        .padding(.top, 28)
        .padding(.bottom, 22)
        .frame(width: Self.width, height: Self.height, alignment: .topLeading)
        .background(style.background)
    }

    private var heading: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(Fmt.day(run.date))
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(style.secondary)
                .lineLimit(1)
            Text(RunSummaryShare.title(for: run))
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(style.primary)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Without a route, the distance takes the place of the drawing.
    private var hero: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("Distance"))
                .font(.system(size: 12, weight: .semibold))
                .tracking(1)
                .textCase(.uppercase)
                .foregroundStyle(style.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(distanceValue)
                    .font(.system(size: 96, weight: .bold))
                    .monospacedDigit()
                    .tracking(-3)
                    .foregroundStyle(style.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                Text("km")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(style.route)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func figureRow(showsDistance: Bool) -> some View {
        HStack(alignment: .top, spacing: 10) {
            if showsDistance {
                figure(distanceValue, "km", L("Distance"))
            }
            figure(Fmt.duration(run.durationSec), nil, L("Time"))
            figure(paceValue, "/km", L("Pace"))
        }
    }

    private func figure(_ value: String, _ unit: String?, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.system(size: 28, weight: .bold))
                    .monospacedDigit()
                    .tracking(-0.5)
                    .foregroundStyle(style.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                if let unit {
                    Text(unit)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(style.primary)
                        .lineLimit(1)
                }
            }
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .tracking(0.8)
                .textCase(.uppercase)
                .foregroundStyle(style.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Renders the share card as a picture.
enum RunShareRenderer {
    /// 1080 × 1350 pixels. Nil when the card could not be rendered (the text is shared instead).
    @MainActor
    static func image(for run: RunRecord, style: RunShareStyle) -> UIImage? {
        let renderer = ImageRenderer(content: RunShareCard(run: run, style: style))
        renderer.scale = 3
        return renderer.uiImage
    }
}

#Preview {
    HStack {
        RunShareCard(run: RunSummarySample.run, style: .dark)
        RunShareCard(run: RunSummarySample.run, style: .light)
    }
}
