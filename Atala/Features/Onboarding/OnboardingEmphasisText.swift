import SwiftUI

/// Sentence that is fully visible from the start in a light grey weight, then turns bold and dark
/// word by word, like a highlight sweeping through it. Different from `OnboardingSpokenText`, where
/// the words appear: here nothing appears or moves, every word keeps the room of its bold form.
/// `onFinished` is called once, after the last word. With Reduce Motion the sentence is bold at
/// once and `onFinished` is called immediately.
struct OnboardingEmphasisText: View {
    let text: String
    var size: CGFloat = 28
    var lineSpacing: CGFloat = 4
    var centered: Bool = false
    /// Words of this phrase (first occurrence) take `highlightColor` once emphasised.
    var highlight: String? = nil
    var highlightColor: Color = Theme.green
    var restingWeight: Font.Weight = .light
    var restingColor: Color = Theme.faint
    var emphasisWeight: Font.Weight = .bold
    var emphasisColor: Color = Theme.ink
    /// Seconds between two words.
    var wordInterval: Double = 0.125
    var startDelay: Double = 0.32
    var onFinished: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var lit = 0
    @State private var finished = false

    private var words: [String] {
        text.split(separator: " ").map { String($0) }
    }

    /// Indices of the words of `highlight` inside `list`.
    private func highlightRange(in list: [String]) -> Range<Int> {
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
        let list = words
        let coloured = highlightRange(in: list)
        return OnboardingWordFlowLayout(wordSpacing: size * 0.25, lineSpacing: lineSpacing, centered: centered) {
            ForEach(list.indices, id: \.self) { index in
                word(list[index], emphasised: index < lit, highlighted: coloured.contains(index))
            }
        }
        .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
        .task {
            await sweep()
        }
    }

    /// The bold copy gives the word its size; the light copy sits on top of it and fades away.
    private func word(_ value: String, emphasised: Bool, highlighted: Bool) -> some View {
        Text(value)
            .font(.system(size: size, weight: emphasisWeight))
            .foregroundStyle(highlighted ? highlightColor : emphasisColor)
            .lineLimit(1)
            .fixedSize()
            .opacity(emphasised ? 1 : 0)
            .overlay(alignment: .leading) {
                Text(value)
                    .font(.system(size: size, weight: restingWeight))
                    .foregroundStyle(restingColor)
                    .lineLimit(1)
                    .fixedSize()
                    .opacity(emphasised ? 0 : 1)
            }
    }

    @MainActor
    private func sweep() async {
        guard !finished else { return }
        let count = words.count
        if reduceMotion || count == 0 {
            lit = count
            finished = true
            onFinished()
            return
        }
        if startDelay > 0 {
            try? await Task.sleep(nanoseconds: UInt64(startDelay * 1_000_000_000))
            if Task.isCancelled { return }
        }
        let interval = UInt64(max(0.05, wordInterval) * 1_000_000_000)
        for index in 0..<count {
            withAnimation(.easeOut(duration: 0.2)) {
                lit = index + 1
            }
            try? await Task.sleep(nanoseconds: interval)
            if Task.isCancelled { return }
        }
        finished = true
        onFinished()
    }
}

#Preview {
    OnboardingEmphasisText(text: "We want you to try Atala for free for 3 days",
                           size: 32, highlight: "for free")
        .padding(24)
}
