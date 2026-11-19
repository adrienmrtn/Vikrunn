import SwiftUI

/// Headline that writes itself: each word fades in and rises a few points, one after the other at
/// an even pace, in its final bold form. Every word has its place from the start (the paragraph
/// is laid out whole before anything shows), so nothing reflows while it appears. One phrase can
/// be coloured. Different from `OnboardingSpokenText` only in its rhythm and look: an even pace
/// for a headline, where the spoken text breathes at punctuation.
/// `onFinished` is called once, after the last word. With Reduce Motion the sentence is there at
/// once and `onFinished` is called immediately.
struct OnboardingEmphasisText: View {
    /// The sentence. A phrase written between two `*` ("try Atala *for free* today") is the
    /// highlighted one: the markers are not shown, and translations keep them around their own words.
    let text: String
    var size: CGFloat = 28
    var lineSpacing: CGFloat = 4
    var centered: Bool = false
    /// Words of this phrase (first occurrence) take `highlightColor`. Not needed, and ignored,
    /// when `text` carries `*` markers.
    var highlight: String? = nil
    var highlightColor: Color = Theme.green
    var emphasisWeight: Font.Weight = .bold
    var emphasisColor: Color = Theme.ink
    /// Seconds between two words.
    var wordInterval: Double = 0.085
    /// The sentence starts when this becomes true (used to chain several sentences).
    var isActive: Bool = true
    var startDelay: Double = 0.32
    var onFinished: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var revealed = false
    @State private var finished = false

    private var interval: Double {
        max(0.04, wordInterval)
    }

    /// `text` cut at its `*` markers: before, highlighted, after. nil without a pair of markers.
    private var marked: [String]? {
        let parts: [String] = text.components(separatedBy: "*")
        return parts.count == 3 ? parts : nil
    }

    /// The sentence without its markers.
    private var plainText: String {
        text.replacingOccurrences(of: "*", with: "")
    }

    private static func split(_ value: String) -> [String] {
        value.split(separator: " ").map { String($0) }
    }

    private var words: [String] {
        guard let marked else { return Self.split(plainText) }
        return Self.split(marked[0]) + Self.split(marked[1]) + Self.split(marked[2])
    }

    /// Indices of the words of `highlight` inside `list`.
    private func highlightRange(in list: [String]) -> Range<Int> {
        if let marked {
            let start = Self.split(marked[0]).count
            return start..<(start + Self.split(marked[1]).count)
        }
        guard let highlight else { return 0..<0 }
        let phrase = highlight.split(separator: " ").map { String($0) }
        guard !phrase.isEmpty, phrase.count <= list.count else { return 0..<0 }
        for start in 0...(list.count - phrase.count) {
            if Array(list[start..<(start + phrase.count)]) == phrase {
                return start..<(start + phrase.count)
            }
        }
        return 0..<0
    }

    var body: some View {
        let list: [String] = words
        let coloured: Range<Int> = highlightRange(in: list)
        return OnboardingWordFlowLayout(wordSpacing: size * 0.25, lineSpacing: lineSpacing, centered: centered) {
            ForEach(list.indices, id: \.self) { index in
                word(list[index], index: index, highlighted: coloured.contains(index))
            }
        }
        .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(plainText)
        .task(id: isActive) {
            await reveal()
        }
    }

    /// The word sits in its final place and form from the start; only its opacity and a small
    /// vertical offset change, so the layout never moves.
    private func word(_ value: String, index: Int, highlighted: Bool) -> some View {
        let start: Double = max(0, startDelay) + Double(index) * interval
        let animation: Animation? = reduceMotion ? nil : OnboardingWordReveal.animation(delay: start)
        return Text(value)
            .font(.system(size: size, weight: emphasisWeight))
            .foregroundStyle(highlighted ? highlightColor : emphasisColor)
            .lineLimit(1)
            .fixedSize()
            .opacity(revealed ? 1 : 0)
            .offset(y: revealed ? 0 : OnboardingWordReveal.rise)
            .animation(animation, value: revealed)
    }

    @MainActor
    private func reveal() async {
        guard isActive, !finished else { return }
        let count: Int = words.count
        if reduceMotion || count == 0 {
            revealed = true
            finished = true
            onFinished()
            return
        }
        // One change: every word then follows its own delay.
        revealed = true
        let lastStart: Double = max(0, startDelay) + Double(count - 1) * interval
        try? await Task.sleep(nanoseconds: OnboardingWordReveal.nanoseconds(lastStart + OnboardingWordReveal.settle))
        if Task.isCancelled { return }
        finished = true
        onFinished()
    }
}

#Preview {
    OnboardingEmphasisText(text: "We want you to try Atala for free for 3 days",
                           size: 32, highlight: "for free")
        .padding(24)
}
