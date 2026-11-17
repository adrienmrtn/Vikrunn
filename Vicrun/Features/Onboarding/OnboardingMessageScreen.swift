import SwiftUI

private struct OnboardingThreadLine: Identifiable {
    let id: Int
    let fromUser: Bool
    let text: String
    /// The avatar is shown on the last bubble of a group of coach bubbles.
    let showsAvatar: Bool

    static let all: [OnboardingThreadLine] = [
        OnboardingThreadLine(id: 0, fromUser: true, text: "What if I miss a session?", showsAvatar: false),
        OnboardingThreadLine(id: 1, fromUser: false,
                             text: "Nothing breaks. It's simply marked as missed, and the rest of your plan stays as it is.",
                             showsAvatar: false),
        OnboardingThreadLine(id: 2, fromUser: false,
                             text: "You ran it after all, on a treadmill for instance? Tell me afterwards and it counts.",
                             showsAvatar: true),
        OnboardingThreadLine(id: 3, fromUser: true, text: "And if I know in advance?", showsAvatar: false),
        OnboardingThreadLine(id: 4, fromUser: false,
                             text: "Tell me before, and I'll suggest another day. Nothing moves until you say yes.",
                             showsAvatar: true),
    ]
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
        .accessibilityLabel("Typing")
    }
}

/// Screen 20: the coach explains missed sessions, as a message thread that plays like a real conversation.
struct OnboardingMessageScreen: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visibleCount = 0
    @State private var typing = false
    @State private var finished = false

    private var timeLabel: String {
        "Today \(Fmt.day(Date(), "HH:mm"))"
    }

    /// The avatar follows the typing dots when the message they announce carries it.
    private var typingShowsAvatar: Bool {
        let lines = OnboardingThreadLine.all
        return visibleCount < lines.count && lines[visibleCount].showsAvatar
    }

    var body: some View {
        OnboardingScaffold(buttonTitle: "Got it, let's go", buttonsVisible: finished) {
            VStack(spacing: 4) {
                CoachAvatar(coach: store.coach, size: 52)
                Text("\(store.coach.name), your coach")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.ink)
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, 12)
            .overlay(alignment: .bottom) {
                Rectangle().fill(Theme.hairline).frame(height: 1)
            }
            VStack(spacing: 6) {
                Text(timeLabel)
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.muted)
                    .padding(.bottom, 2)
                ForEach(OnboardingThreadLine.all) { line in
                    if line.id < visibleCount {
                        bubble(line)
                            .transition(line.fromUser ? Self.userEntrance : Self.coachEntrance)
                    }
                }
                if typing {
                    typingRow
                        .transition(Self.coachEntrance)
                }
                if finished {
                    Text("Read")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.muted)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .transition(.opacity)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .task {
            await play()
        }
    }

    /// User messages pop in from the right, coach messages grow from their tail.
    private static var userEntrance: AnyTransition {
        AnyTransition.asymmetric(
            insertion: AnyTransition.move(edge: .trailing).combined(with: .opacity),
            removal: AnyTransition.opacity
        )
    }

    private static var coachEntrance: AnyTransition {
        AnyTransition.asymmetric(
            insertion: AnyTransition.scale(scale: 0.85, anchor: .bottomLeading).combined(with: .opacity),
            removal: AnyTransition.opacity
        )
    }

    private static let pop = Animation.spring(response: 0.4, dampingFraction: 0.78)

    private static func wait(_ seconds: Double) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    /// Plays the conversation once; cancelled with the view (`.task`).
    @MainActor
    private func play() async {
        let lines = OnboardingThreadLine.all
        guard !finished else { return }
        if reduceMotion {
            visibleCount = lines.count
            finished = true
            return
        }
        for index in lines.indices where index >= visibleCount {
            let line = lines[index]
            if line.fromUser {
                await Self.wait(index == 0 ? 0.6 : 1.0)
                if Task.isCancelled { return }
            } else {
                await Self.wait(0.45)
                if Task.isCancelled { return }
                withAnimation(Self.pop) { typing = true }
                // The longer the message, the longer the coach "types".
                await Self.wait(min(1.6, 0.7 + Double(line.text.count) * 0.009))
                if Task.isCancelled {
                    typing = false
                    return
                }
            }
            withAnimation(Self.pop) {
                typing = false
                visibleCount = index + 1
            }
        }
        await Self.wait(0.5)
        if Task.isCancelled { return }
        withAnimation(.easeOut(duration: 0.3)) {
            finished = true
        }
    }

    private var typingRow: some View {
        HStack(alignment: .bottom, spacing: 8) {
            if typingShowsAvatar {
                CoachAvatar(coach: store.coach, size: 30)
            } else {
                Color.clear.frame(width: 30, height: 30)
            }
            OnboardingTypingBubble()
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder private func bubble(_ line: OnboardingThreadLine) -> some View {
        if line.fromUser {
            Text(line.text)
                .font(.system(size: 16))
                .foregroundStyle(Color.white)
                .multilineTextAlignment(.leading)
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(
                    Theme.green,
                    in: UnevenRoundedRectangle(topLeadingRadius: 20, bottomLeadingRadius: 20,
                                               bottomTrailingRadius: 6, topTrailingRadius: 20)
                )
                .frame(maxWidth: 270, alignment: .trailing)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .padding(.top, 6)
        } else {
            HStack(alignment: .bottom, spacing: 8) {
                if line.showsAvatar {
                    CoachAvatar(coach: store.coach, size: 30)
                } else {
                    Color.clear.frame(width: 30, height: 30)
                }
                Text(line.text)
                    .font(.system(size: 16))
                    .foregroundStyle(Theme.ink)
                    .multilineTextAlignment(.leading)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 9)
                    .background(
                        Color(hex: 0xE9E9EB),
                        in: UnevenRoundedRectangle(topLeadingRadius: 20,
                                                   bottomLeadingRadius: line.showsAvatar ? 6 : 20,
                                                   bottomTrailingRadius: 20, topTrailingRadius: 20)
                    )
                    .frame(maxWidth: 270, alignment: .leading)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

#Preview {
    OnboardingMessageScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
