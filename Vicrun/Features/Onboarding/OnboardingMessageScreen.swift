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
/// It ends with a live example: the user warns the coach, the coach proposes a change on a card, and the
/// user applies it or keeps the plan. A demo only: nothing is written to the store.
struct OnboardingMessageScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visibleCount = 0
    @State private var typing = false
    /// 0 nothing yet, 1 the user's "I can't run this …", 2 the coach's answer and its card.
    @State private var demoStep = 0
    @State private var choice: OnboardingPlanChangeChoice?
    @State private var closingShown = false
    @State private var finished = false

    private static let bottomID = "onboarding-thread-bottom"
    private static let closingLine = "That's how it works. You always have the last word."
    private static let proposalLine = "No problem. Here is what I suggest:"

    private var example: OnboardingPlanChangeExample {
        OnboardingPlanChangeExample.make(from: store.answers)
    }

    private var timeLabel: String {
        "Today \(Fmt.day(Date(), "HH:mm"))"
    }

    /// The avatar follows the typing dots when the message they announce carries it.
    private var typingShowsAvatar: Bool {
        let lines = OnboardingThreadLine.all
        if visibleCount < lines.count { return lines[visibleCount].showsAvatar }
        return choice != nil
    }

    /// Changes every time something is added to the thread, to scroll to the newest message.
    private var scrollToken: String {
        "\(visibleCount)-\(typing)-\(demoStep)-\(choice != nil)-\(closingShown)-\(finished)"
    }

    var body: some View {
        VStack(spacing: 0) {
            OnboardingHeaderSpace()
            coachHeader
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 10)
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 0) {
                        thread
                        Color.clear
                            .frame(height: 1)
                            .id(Self.bottomID)
                    }
                    .padding(.horizontal, Theme.screenPadding)
                    .padding(.top, 14)
                    .padding(.bottom, 16)
                    .frame(maxWidth: .infinity)
                }
                .scrollBounceBehavior(.basedOnSize)
                .scrollIndicators(.hidden)
                .onChange(of: scrollToken) { _, _ in
                    scrollToBottom(proxy)
                }
            }
            BottomBar {
                PrimaryButton(title: "Got it, let's go") { nav.next(store) }
            }
            .opacity(finished ? 1 : 0)
            .allowsHitTesting(finished)
            .accessibilityHidden(!finished)
            .animation(.easeOut(duration: 0.35), value: finished)
        }
        .background(Color.white.ignoresSafeArea())
        .sensoryFeedback(.success, trigger: choice)
        .task {
            await play()
        }
        .task(id: choice) {
            await playClosing()
        }
    }

    // MARK: Pieces

    private var coachHeader: some View {
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
    }

    private var thread: some View {
        let demo = example
        return VStack(spacing: 6) {
            Text(timeLabel)
                .font(.system(size: 11))
                .foregroundStyle(Theme.muted)
                .padding(.bottom, 2)
            ForEach(OnboardingThreadLine.all) { line in
                if line.id < visibleCount {
                    if line.fromUser {
                        userBubble(line.text)
                            .transition(Self.userEntrance)
                    } else {
                        coachBubble(line.text, showsAvatar: line.showsAvatar)
                            .transition(Self.coachEntrance)
                    }
                }
            }
            if demoStep >= 1 {
                userBubble(demo.userLine)
                    .transition(Self.userEntrance)
            }
            if demoStep >= 2 {
                coachBubble(Self.proposalLine, showsAvatar: false)
                    .transition(Self.coachEntrance)
                cardRow(demo)
                    .transition(Self.coachEntrance)
            }
            if closingShown {
                coachBubble(Self.closingLine, showsAvatar: true)
                    .padding(.top, 6)
                    .transition(Self.coachEntrance)
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

    private func cardRow(_ demo: OnboardingPlanChangeExample) -> some View {
        HStack(alignment: .bottom, spacing: 8) {
            // Once answered, the avatar moves down to the coach's closing line.
            if choice != nil {
                Color.clear.frame(width: 30, height: 30)
            } else {
                CoachAvatar(coach: store.coach, size: 30)
            }
            OnboardingPlanChangeCard(example: demo, choice: choice, onChoose: { choose($0) })
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 2)
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

    private func userBubble(_ text: String) -> some View {
        Text(text)
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
    }

    private func coachBubble(_ text: String, showsAvatar: Bool) -> some View {
        HStack(alignment: .bottom, spacing: 8) {
            if showsAvatar {
                CoachAvatar(coach: store.coach, size: 30)
            } else {
                Color.clear.frame(width: 30, height: 30)
            }
            Text(text)
                .font(.system(size: 16))
                .foregroundStyle(Theme.ink)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(
                    Color(hex: 0xE9E9EB),
                    in: UnevenRoundedRectangle(topLeadingRadius: 20,
                                               bottomLeadingRadius: showsAvatar ? 6 : 20,
                                               bottomTrailingRadius: 20, topTrailingRadius: 20)
                )
                .frame(maxWidth: 270, alignment: .leading)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Motion

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

    /// Keeps the newest message (and the card's buttons) on screen, once the layout has settled.
    private func scrollToBottom(_ proxy: ScrollViewProxy) {
        let animated = !reduceMotion
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 60_000_000)
            if animated {
                withAnimation(.easeOut(duration: 0.3)) {
                    proxy.scrollTo(Self.bottomID, anchor: .bottom)
                }
            } else {
                proxy.scrollTo(Self.bottomID, anchor: .bottom)
            }
        }
    }

    /// Plays the conversation once, up to the card; cancelled with the view (`.task`).
    @MainActor
    private func play() async {
        let lines = OnboardingThreadLine.all
        guard demoStep < 2 else { return }
        if reduceMotion {
            visibleCount = lines.count
            demoStep = 2
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
        // The live example: the user warns the coach, the coach answers with a change to accept or not.
        if demoStep < 1 {
            await Self.wait(1.1)
            if Task.isCancelled { return }
            withAnimation(Self.pop) { demoStep = 1 }
        }
        await Self.wait(0.45)
        if Task.isCancelled { return }
        withAnimation(Self.pop) { typing = true }
        await Self.wait(1.2)
        if Task.isCancelled {
            typing = false
            return
        }
        withAnimation(Self.pop) {
            typing = false
            demoStep = 2
        }
    }

    /// Resolves the card. Nothing is saved: the plan is only built at the end of the questionnaire.
    private func choose(_ value: OnboardingPlanChangeChoice) {
        guard choice == nil, demoStep >= 2 else { return }
        if reduceMotion {
            choice = value
        } else {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                choice = value
            }
        }
    }

    /// After the choice: the coach's closing line, then the button.
    @MainActor
    private func playClosing() async {
        guard choice != nil, !finished else { return }
        if reduceMotion {
            closingShown = true
            finished = true
            return
        }
        if !closingShown {
            await Self.wait(0.6)
            if Task.isCancelled { return }
            withAnimation(Self.pop) { typing = true }
            await Self.wait(1.0)
            if Task.isCancelled {
                typing = false
                return
            }
            withAnimation(Self.pop) {
                typing = false
                closingShown = true
            }
        }
        await Self.wait(0.5)
        if Task.isCancelled { return }
        withAnimation(.easeOut(duration: 0.3)) {
            finished = true
        }
    }
}

#Preview {
    OnboardingMessageScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
