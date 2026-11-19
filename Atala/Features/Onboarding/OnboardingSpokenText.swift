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
    @State private var shown = 0
    @State private var finished = false

    private var words: [String] {
        text.split(separator: " ").map { String($0) }
    }

    var body: some View {
        flow(words)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(text)
            .task(id: isActive) {
                await speak()
            }
    }

    private func flow(_ list: [String]) -> some View {
        OnboardingWordFlowLayout(wordSpacing: size * 0.27, lineSpacing: lineSpacing) {
            ForEach(list.indices, id: \.self) { index in
                Text(list[index])
                    .font(.system(size: size, weight: weight))
                    .foregroundStyle(color)
                    .lineLimit(1)
                    .fixedSize()
                    .opacity(index < shown ? 1 : 0)
                    .offset(y: index < shown ? 0 : 8)
                    .blur(radius: index < shown ? 0 : 3)
            }
        }
    }

    /// Seconds before the next word: a natural rhythm, with a breath after punctuation.
    private static func pause(after word: String) -> Double {
        guard let last = word.last else { return 0.1 }
        if last == "." || last == "!" || last == "?" { return 0.31 }
        if last == "," || last == ":" || last == ";" { return 0.18 }
        return 0.1
    }

    @MainActor
    private func speak() async {
        guard isActive, !finished else { return }
        let list = words
        if reduceMotion || list.isEmpty {
            shown = list.count
            finished = true
            onFinished()
            return
        }
        if startDelay > 0 {
            try? await Task.sleep(nanoseconds: UInt64(startDelay * 1_000_000_000))
            if Task.isCancelled { return }
        }
        for index in list.indices {
            // Longer than the pause between two words: each word is still settling when the next starts.
            withAnimation(.easeOut(duration: 0.45)) {
                shown = index + 1
            }
            try? await Task.sleep(nanoseconds: UInt64(Self.pause(after: list[index]) * 1_000_000_000))
            if Task.isCancelled { return }
        }
        finished = true
        onFinished()
    }
}

#Preview {
    OnboardingSpokenText(text: "Hello Adrien! I'm Awa, and I'll be your coach all the way to your goal.",
                         size: 28, weight: .bold)
        .padding(24)
}
