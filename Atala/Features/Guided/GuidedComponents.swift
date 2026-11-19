import SwiftUI
import UIKit

/// An exercise picture from the asset catalog, or a neutral symbol when the image is missing.
/// Which coach performs the exercises. Set from the root view whenever the chosen coach changes.
/// Coach 1 uses the base images (`exercise_<id>_<frame>`); the others use `exercise_c<coach>_<id>_<frame>`,
/// falling back to the base image when one is missing.
enum GuidedExerciseCoach {
    static var coachID: Int = 1

    static func image(named name: String) -> UIImage? {
        if coachID != 1, name.hasPrefix("exercise_") {
            let personal = "exercise_c\(coachID)_" + name.dropFirst("exercise_".count)
            if let image = UIImage(named: personal) { return image }
        }
        return UIImage(named: name)
    }
}

struct GuidedExerciseImage: View {
    let name: String
    var symbol: String = "figure.cooldown"

    var body: some View {
        if let image = GuidedExerciseCoach.image(named: name) {
            Image(uiImage: image)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
        } else {
            GeometryReader { geo in
                Image(systemName: symbol)
                    .font(.system(size: max(12, min(geo.size.width, geo.size.height) * 0.42), weight: .regular))
                    .foregroundStyle(Theme.greenSoft)
                    .frame(width: geo.size.width, height: geo.size.height)
            }
        }
    }
}

/// Small rounded picture of an exercise, for lists.
struct GuidedExerciseThumb: View {
    let info: ExerciseInfo
    var size: CGFloat = 56

    var body: some View {
        GuidedExerciseImage(name: info.imageStart, symbol: info.kind.symbol)
            .padding(size * 0.08)
            .frame(width: size, height: size)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// The two frames of an exercise, alternating in a loop: start, deep position, back.
struct GuidedExerciseAnimation: View {
    let info: ExerciseInfo
    var playing: Bool = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var deep = false

    var body: some View {
        ZStack {
            GuidedExerciseImage(name: info.imageStart, symbol: info.kind.symbol)
                .opacity(deep ? 0 : 1)
            GuidedExerciseImage(name: info.imageEnd, symbol: info.kind.symbol)
                .opacity(deep ? 1 : 0)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(info.name): \(info.summary)")
        .task(id: loopKey) {
            await loop()
        }
    }

    private var loopKey: String { "\(info.id)-\(playing)" }

    @MainActor
    private func loop() async {
        deep = false
        guard playing else { return }
        let tempo = info.tempo
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: Self.nanoseconds(tempo.startHold))
            if Task.isCancelled { return }
            show(deep: true, fade: tempo.fade)
            try? await Task.sleep(nanoseconds: Self.nanoseconds(tempo.fade + tempo.deepHold))
            if Task.isCancelled { return }
            show(deep: false, fade: tempo.fade)
            try? await Task.sleep(nanoseconds: Self.nanoseconds(tempo.fade))
        }
    }

    @MainActor
    private func show(deep value: Bool, fade: Double) {
        if reduceMotion {
            deep = value
        } else {
            withAnimation(.easeInOut(duration: fade)) { deep = value }
        }
    }

    private static func nanoseconds(_ seconds: Double) -> UInt64 {
        UInt64(max(0.05, seconds) * 1_000_000_000)
    }
}

/// Circular countdown with a large number in the middle.
struct GuidedTimerRing: View {
    /// 0...1, share of the time already spent.
    let progress: Double
    let text: String
    var caption: String = "seconds"
    var tint: Color = Theme.green
    var size: CGFloat = 132

    var body: some View {
        ZStack {
            Circle()
                .stroke(Theme.hairline, lineWidth: 10)
            Circle()
                .trim(from: 0, to: CGFloat(min(1, max(0, 1 - progress))))
                .stroke(tint, style: StrokeStyle(lineWidth: 10, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.2), value: progress)
            VStack(spacing: 0) {
                Text(text)
                    .font(.system(size: size * 0.36, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                Text(caption)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.muted)
            }
            .padding(.horizontal, 16)
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(text) \(caption) left")
    }
}

/// One segment per exercise: done, current (taller) and to come.
struct GuidedProgressBar: View {
    let count: Int
    let currentIndex: Int
    var complete: Bool = false

    var body: some View {
        HStack(alignment: .center, spacing: 4) {
            ForEach(0..<max(1, count), id: \.self) { index in
                let isCurrent = !complete && index == currentIndex
                let isDone = complete || index < currentIndex
                Capsule()
                    .fill(isCurrent ? Theme.green : (isDone ? Theme.greenSoft : Color(hex: 0xD9D9D6)))
                    .opacity(isDone ? 0.55 : 1)
                    .frame(maxWidth: .infinity)
                    .frame(height: isCurrent ? 10 : 6)
            }
        }
        .frame(height: 10)
        .animation(.easeInOut(duration: 0.25), value: currentIndex)
        .accessibilityHidden(true)
    }
}

/// Small rounded label ("Left leg", "Set 2 of 3").
struct GuidedChip: View {
    let text: String
    var foreground: Color = Theme.green
    var background: Color = Color.white

    var body: some View {
        Text(text)
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(foreground)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(background, in: Capsule())
    }
}

#Preview {
    VStack(spacing: 24) {
        GuidedProgressBar(count: 5, currentIndex: 1)
        GuidedExerciseAnimation(info: ExerciseCatalog.all[1])
            .frame(height: 220)
        GuidedTimerRing(progress: 0.4, text: "27")
        GuidedChip(text: "Left leg", background: Theme.greenTint)
    }
    .padding(20)
}
