import SwiftUI

/// One ready-made question the user can ask in the onboarding conversation, with the coach's answer.
struct OnboardingChatTopic: Identifiable, Equatable {
    let id: Int
    let question: String
    /// The coach's bubbles, in order.
    let answers: [String]
    /// The answer ends with the "Plan change" card (Apply / Keep my plan).
    let opensPlanChange: Bool

    static var opening: String { L("Ask me anything, any time. Try one of the questions below.") }
    static var proposalLine: String { L("No problem. Here is what I suggest:") }
    static var closingLine: String { L("That's how it works. You always have the last word.") }

    /// The questions offered, in the order of the choices. The last one is the live example: its
    /// wording follows the days the user ticked ("I can't run this Thursday").
    static func all(example: OnboardingPlanChangeExample) -> [OnboardingChatTopic] {
        [
            OnboardingChatTopic(id: 0, question: L("How should I warm up today?"), answers: [
                L("Five minutes of easy jogging, then leg swings, high knees and a few relaxed strides."),
                L("Before a hard session, add two short accelerations so your first rep doesn't come as a shock."),
            ], opensPlanChange: false),
            OnboardingChatTopic(id: 1, question: L("What if I miss a session?"), answers: [
                L("Nothing breaks. It's simply marked as missed, and the rest of your plan stays as it is."),
            ], opensPlanChange: false),
            OnboardingChatTopic(id: 2, question: example.userLine, answers: [
                proposalLine,
            ], opensPlanChange: true),
        ]
    }
}

/// One item of the conversation, in the order it was added.
struct OnboardingThreadEntry: Identifiable, Equatable {
    enum Kind: Equatable {
        case user, coach, card
    }

    let id: Int
    let kind: Kind
    let text: String
    /// The avatar is shown on the last bubble of a group of coach bubbles.
    let showsAvatar: Bool
}

/// Three breathing dots in a coach bubble, shown while the coach "types".
struct OnboardingTypingBubble: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reduceMotion)) { context in
            let time = context.date.timeIntervalSinceReferenceDate
            HStack(spacing: 5) {
                ForEach(0..<3, id: \.self) { dot in
                    Circle()
                        .fill(Theme.muted)
                        .frame(width: 8, height: 8)
                        .opacity(0.3 + 0.6 * max(0, sin(time * 5.5 - Double(dot) * 0.9)))
                }
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 38)
        .background(Color(hex: 0xE9E9EB), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityLabel(L("Typing"))
    }
}

/// The questions still to ask, as tappable chips above the bottom button. Tapping one sends it.
struct OnboardingQuestionChips: View {
    let topics: [OnboardingChatTopic]
    /// False while the coach is answering: the chips wait.
    let enabled: Bool
    let onAsk: (OnboardingChatTopic) -> Void

    var body: some View {
        VStack(alignment: .trailing, spacing: 8) {
            Text(L("Tap a question to ask it"))
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.muted)
            ForEach(topics) { topic in
                Button(action: {
                    Haptics.tap()
                    onAsk(topic)
                }) {
                    Text(topic.question)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.green)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .padding(.horizontal, 16)
                        .frame(minHeight: 44)
                        .background(Theme.greenTint, in: Capsule())
                        .overlay(Capsule().stroke(Theme.green.opacity(0.3), lineWidth: 1))
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityHint(L("Sends this question to your coach"))
            }
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 10)
        .padding(.bottom, 6)
        .opacity(enabled ? 1 : 0.45)
        .disabled(!enabled)
        .animation(.easeOut(duration: 0.2), value: enabled)
        .background(Color.white)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.hairline).frame(height: 1)
        }
    }
}
