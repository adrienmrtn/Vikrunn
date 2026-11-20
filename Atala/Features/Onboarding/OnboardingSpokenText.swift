import SwiftUI

/// Lays words out like a left-aligned paragraph, so each word can be animated on its own.
struct OnboardingWordFlowLayout: Layout {
    var wordSpacing: CGFloat
    var lineSpacing: CGFloat
    /// Centres every line instead of aligning it on the leading edge.
    var centered: Bool = false

    private func arrange(width: CGFloat, subviews: Subviews) -> (points: [CGPoint], size: CGSize) {
        var points: [CGPoint] = []
        var lineOfWord: [Int] = []
        var lineWidths: [CGFloat] = [0]
        var x: CGFloat = 0
        var y: CGFloat = 0
        var lineHeight: CGFloat = 0
        var widest: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width + 0.5 {
                x = 0
                y += lineHeight + lineSpacing
                lineHeight = 0
                lineWidths.append(0)
            }
            points.append(CGPoint(x: x, y: y))
            lineOfWord.append(lineWidths.count - 1)
            lineWidths[lineWidths.count - 1] = x + size.width
            widest = max(widest, x + size.width)
            x += size.width + wordSpacing
            lineHeight = max(lineHeight, size.height)
        }
        if centered {
            for index in points.indices {
                points[index].x += (widest - lineWidths[lineOfWord[index]]) / 2
            }
        }
        return (points, CGSize(width: widest, height: y + lineHeight))
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(width: proposal.width ?? .infinity, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let points = arrange(width: bounds.width, subviews: subviews).points
        for (index, subview) in subviews.enumerated() where index < points.count {
            subview.place(at: CGPoint(x: bounds.minX + points[index].x, y: bounds.minY + points[index].y),
                          anchor: .topLeading,
                          proposal: .unspecified)
        }
    }
}

/// A translated sentence whose emphasised part sits between two asterisks, as in
/// "Atala is *not just a coach.*": the sentence without the marks, and the part to highlight.
/// Without exactly two asterisks, the whole sentence comes back and nothing is highlighted.
enum OnboardingMarkedSentence {
    static func parse(_ marked: String) -> (text: String, highlight: String?) {
        let parts: [String] = marked.components(separatedBy: "*")
        guard parts.count == 3 else { return (parts.joined(), nil) }
        let phrase: String = parts[1].trimmingCharacters(in: .whitespaces)
        return (parts.joined(), phrase.isEmpty ? nil : phrase)
    }
}

/// How a word arrives in the texts that reveal themselves: it fades in while rising a few points.
/// Every word gets its own delay up front, so the rhythm is set by the animation system and
/// stays perfectly even (no timer between two words).
enum OnboardingWordReveal {
    /// How far below its place a word starts.
    static let rise: CGFloat = 7
    /// Time a word takes to settle. Longer than the gap between two words: they overlap softly.
    static let duration: Double = 0.6
    /// After the last word has started, how long before the text counts as said.
    static let settle: Double = 0.38

    /// Quick start, long soft landing.
    static func animation(delay: Double) -> Animation {
        Animation.timingCurve(0.22, 0.61, 0.36, 1, duration: duration).delay(max(0, delay))
    }

    static func nanoseconds(_ seconds: Double) -> UInt64 {
        UInt64(max(0, seconds) * 1_000_000_000)
    }
}

/// Shrinks the texts said by the coach on small phones, so the full-screen coach keeps its room.
private struct OnboardingTextScaleKey: EnvironmentKey {
    static let defaultValue: CGFloat = 1
}

extension EnvironmentValues {
    /// 1 on regular phones, a little less on the smallest ones (set by `OnboardingTapScreen`).
    var onboardingTextScale: CGFloat {
        get { self[OnboardingTextScaleKey.self] }
        set { self[OnboardingTextScaleKey.self] = newValue }
    }
}

/// Text that appears word by word, as if the coach were speaking. The full paragraph is laid out
/// from the start, so nothing around it moves. `onFinished` is called once, after the last word.
/// With Reduce Motion the text shows at once and `onFinished` is called immediately.
struct OnboardingSpokenText: View {
    let text: String
    var size: CGFloat = 17
    var weight: Font.Weight = .regular
    var color: Color = Theme.ink
    var lineSpacing: CGFloat = 4
    /// The coach starts speaking when this becomes true (used to chain several sentences).
    var isActive: Bool = true
    var startDelay: Double = 0
    var onFinished: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.onboardingTextScale) private var textScale
    @State private var revealed = false
    @State private var finished = false

    /// The size actually drawn: `size`, shrunk on small phones.
    private var scaledSize: CGFloat { size * textScale }

    private var words: [String] {
        text.split(separator: " ").map { String($0) }
    }

    /// Seconds before the next word: a steady reading pace, with a breath after punctuation.
    private static func pause(after word: String) -> Double {
        guard let last = word.last else { return 0.09 }
        if last == "." || last == "!" || last == "?" { return 0.26 }
        if last == "," || last == ":" || last == ";" { return 0.16 }
        return 0.09
    }

    /// When each word starts, counted from the moment the coach starts speaking.
    private static func starts(for list: [String], after startDelay: Double) -> [Double] {
        var result: [Double] = []
        var time: Double = max(0, startDelay)
        for word in list {
            result.append(time)
            time += pause(after: word)
        }
        return result
    }

    var body: some View {
        let list: [String] = words
        let starts: [Double] = Self.starts(for: list, after: startDelay)
        return OnboardingWordFlowLayout(wordSpacing: scaledSize * 0.27, lineSpacing: lineSpacing * textScale) {
            ForEach(list.indices, id: \.self) { index in
                word(list[index], start: index < starts.count ? starts[index] : 0)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
        .task(id: isActive) {
            await speak()
        }
    }

    private func word(_ value: String, start: Double) -> some View {
        let animation: Animation? = reduceMotion ? nil : OnboardingWordReveal.animation(delay: start)
        return Text(value)
            .font(.system(size: scaledSize, weight: weight))
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize()
            .opacity(revealed ? 1 : 0)
            .offset(y: revealed ? 0 : OnboardingWordReveal.rise)
            .animation(animation, value: revealed)
    }

    @MainActor
    private func speak() async {
        guard isActive, !finished else { return }
        let list: [String] = words
        if reduceMotion || list.isEmpty {
            revealed = true
            finished = true
            onFinished()
            return
        }
        // One change: every word then follows its own delay.
        revealed = true
        let lastStart: Double = Self.starts(for: list, after: startDelay).last ?? 0
        try? await Task.sleep(nanoseconds: OnboardingWordReveal.nanoseconds(lastStart + OnboardingWordReveal.settle))
        if Task.isCancelled { return }
        finished = true
        onFinished()
    }
}

#Preview {
    OnboardingSpokenText(text: "Hello Adrien! I'm Awa, and I'll be your coach all the way to your goal.",
                         size: 28, weight: .bold)
        .padding(24)
}
