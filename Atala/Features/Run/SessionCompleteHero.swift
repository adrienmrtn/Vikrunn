import SwiftUI
import UIKit

/// Stages of the "Session complete" sequence, in the order they are reached.
enum SessionCompleteStage {
    static let check = 1
    static let title = 2
    static let dataLine = 3
    static let sheet = 4
    /// One stage per stat: 5, 6 and 7.
    static let firstStat = 5
    static let coach = 8
    static let extras = 9
    static let button = 10
}

/// One figure of the content sheet. It counts from zero to `value` when it appears.
struct SessionCompleteStat {
    let value: Double
    let label: String
    let format: (Double) -> String
}

/// Text that follows `value` while the change is animated.
struct SessionCompleteCountingText: View, Animatable {
    var value: Double
    let format: (Double) -> String

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View {
        Text(format(value))
    }
}

/// A stat of the sheet: rises in, and its number counts up.
struct SessionCompleteStatView: View {
    let stat: SessionCompleteStat
    let shown: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            SessionCompleteCountingText(value: shown ? stat.value : 0, format: stat.format)
                .font(.system(size: 24, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .animation(reduceMotion ? nil : .easeOut(duration: 0.6), value: shown)
            Text(stat.label)
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .opacity(shown ? 1 : 0)
        .offset(y: shown ? 0 : 12)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(stat.label): \(stat.format(stat.value))")
    }
}

/// The tick of the check mark, in the unit square of its frame.
struct SessionCompleteCheckShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + rect.width * 0.29, y: rect.minY + rect.height * 0.53))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.44, y: rect.minY + rect.height * 0.67))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.72, y: rect.minY + rect.height * 0.37))
        return path
    }
}

/// A white check mark that draws itself inside a ring, with a small pop.
struct SessionCompleteCheck: View {
    let drawn: Bool
    var size: CGFloat = 68

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var drawing: Animation? {
        reduceMotion ? nil : .easeOut(duration: 0.45)
    }

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(0.16))
            Circle()
                .trim(from: 0, to: drawn ? 1 : 0)
                .stroke(Color.white, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(drawing, value: drawn)
            SessionCompleteCheckShape()
                .trim(from: 0, to: drawn ? 1 : 0)
                .stroke(Color.white, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
                .animation(drawing, value: drawn)
        }
        .frame(width: size, height: size)
        .scaleEffect(drawn ? 1 : 0.6)
        .opacity(drawn ? 1 : 0)
        .accessibilityHidden(true)
    }
}

/// A short burst of small pieces in the brand colours, flying up from one point and falling back.
/// Draws nothing once `duration` has passed; the owner removes it afterwards.
struct SessionCompleteConfetti: View {
    let start: Date
    /// Distance from the top of the view to the point the pieces fly from.
    let originY: CGFloat

    static let duration: Double = 1.2
    private static let count = 26
    private static let gravity: Double = 560
    private static let colors: [Color] = [Theme.green, Theme.orange, Theme.cream]

    /// A fixed pseudo-random value in 0..<1 for a piece: the burst is the same every time.
    private static func unit(_ index: Int, _ salt: Double) -> Double {
        let raw: Double = sin(Double(index) * 12.9898 + salt * 78.233) * 43758.5453
        return raw - raw.rounded(.down)
    }

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let time: Double = timeline.date.timeIntervalSince(start)
                guard time >= 0, time <= Self.duration else { return }
                let fade: Double = min(1, (Self.duration - time) / 0.4)
                for index in 0..<Self.count {
                    let angle: Double = -Double.pi / 2 + (Self.unit(index, 1) - 0.5) * 2.6
                    let speed: Double = 150 + 230 * Self.unit(index, 2)
                    let dx: Double = cos(angle) * speed * time
                    let dy: Double = sin(angle) * speed * time + 0.5 * Self.gravity * time * time
                    let side = CGFloat(5 + 4 * Self.unit(index, 3))
                    let spin: Double = (Self.unit(index, 4) - 0.5) * 14 * time
                    var piece = context
                    piece.opacity = fade
                    piece.translateBy(x: size.width / 2 + CGFloat(dx), y: originY + CGFloat(dy))
                    piece.rotate(by: Angle.radians(spin))
                    let path: Path
                    if index % 2 == 0 {
                        path = Path(ellipseIn: CGRect(x: -side / 2, y: -side / 2, width: side, height: side))
                    } else {
                        path = Path(roundedRect: CGRect(x: -side / 2, y: -side * 0.3, width: side, height: side * 0.6),
                                    cornerRadius: 1.5)
                    }
                    piece.fill(path, with: .color(Self.colors[index % Self.colors.count]))
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Top of the "Session complete" screens: the night-track photo edge to edge, darkened at the top,
/// with the check mark, the title and one line of real figures over it.
struct SessionCompleteHero: View {
    let height: CGFloat
    /// Height of the status bar area the hero sits under.
    let topInset: CGFloat
    /// Height at the bottom that ends up under the content sheet.
    let overlap: CGFloat
    var eyebrow: String? = nil
    let dataLine: String
    let stage: Int

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var settled = false

    static let photoName = "photo_session_complete"
    private static let checkSize: CGFloat = 68
    /// Rough height of the check, the title and the line, used to place them.
    private static let textHeight: CGFloat = 180

    /// Distance from the top of the hero to the top of the check mark. The floor keeps the text
    /// clear of the status bar and the Dynamic Island whatever inset is reported.
    static func textTop(height: CGFloat, topInset: CGFloat, overlap: CGFloat) -> CGFloat {
        let inset = max(topInset, 54)
        let free = height - overlap - inset - textHeight
        return inset + max(8, free * 0.3)
    }

    /// Distance from the top of the hero to the centre of the check mark: the confetti start there.
    static func checkCentreY(height: CGFloat, topInset: CGFloat, overlap: CGFloat) -> CGFloat {
        textTop(height: height, topInset: topInset, overlap: overlap) + checkSize / 2
    }

    var body: some View {
        Theme.forest
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .overlay(alignment: .bottom) { photo }
            .clipped()
            .overlay { scrim }
            .overlay(alignment: .top) {
                texts
                    .padding(.horizontal, 24)
                    .padding(.top, Self.textTop(height: height, topInset: topInset, overlap: overlap))
            }
            .onAppear {
                guard !reduceMotion, !settled else { return }
                withAnimation(.easeOut(duration: 6)) {
                    settled = true
                }
            }
    }

    /// Anchored to the bottom, so the runners in the lower part of the picture are never cropped.
    /// Without the image in the catalog, the forest green background stays.
    @ViewBuilder private var photo: some View {
        if UIImage(named: Self.photoName) != nil {
            Image(Self.photoName)
                .resizable()
                .scaledToFill()
                .scaleEffect(reduceMotion || settled ? 1 : 1.08, anchor: .bottom)
                .accessibilityHidden(true)
        }
    }

    private var scrim: some View {
        LinearGradient(
            gradient: Gradient(stops: [
                Gradient.Stop(color: Color.black.opacity(0.68), location: 0),
                Gradient.Stop(color: Color.black.opacity(0.36), location: 0.55),
                Gradient.Stop(color: Color.black.opacity(0), location: 0.92),
            ]),
            startPoint: .top,
            endPoint: .bottom
        )
        .allowsHitTesting(false)
    }

    private var texts: some View {
        let lineShown = stage >= SessionCompleteStage.dataLine
        return VStack(spacing: 6) {
            SessionCompleteCheck(drawn: stage >= SessionCompleteStage.check, size: Self.checkSize)
                .padding(.bottom, 8)
            title
            VStack(spacing: 2) {
                Text(dataLine)
                    .font(.system(size: 18, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Color.white)
                if let eyebrow {
                    Text(eyebrow)
                        .font(.system(size: 14))
                        .foregroundStyle(Color.white.opacity(0.82))
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .multilineTextAlignment(.center)
            .opacity(lineShown ? 1 : 0)
            .offset(y: lineShown ? 0 : 10)
        }
        .shadow(color: Color.black.opacity(0.35), radius: 8, x: 0, y: 2)
        .frame(maxWidth: .infinity)
    }

    /// "Session complete", one word after the other, rising into place.
    private var title: some View {
        let words = ["Session", "complete"]
        let shown = stage >= SessionCompleteStage.title
        return HStack(spacing: 9) {
            ForEach(words.indices, id: \.self) { index in
                Text(words[index])
                    .font(.system(size: 36, weight: .bold))
                    .foregroundStyle(Color.white)
                    .opacity(shown ? 1 : 0)
                    .offset(y: shown ? 0 : 14)
                    .animation(reduceMotion ? nil : .easeOut(duration: 0.4).delay(Double(index) * 0.12),
                               value: shown)
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.7)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Session complete")
        .accessibilityAddTraits(.isHeader)
    }
}
