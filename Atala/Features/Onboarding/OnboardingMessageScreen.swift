import SwiftUI

/// Screen 20: how the user talks to the coach. The user asks: ready-made questions sit at the bottom
/// as chips, tapping one sends it, then the coach types and answers. One of them is a live example:
/// the user warns the coach, the coach proposes a change on a card, and the user applies it or keeps
/// the plan. The button to move on shows up after the coach's first answer.
/// A demo only: nothing is written to the store.
struct OnboardingMessageScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var entries: [OnboardingThreadEntry] = []
    /// Questions already sent: they leave the choices.
    @State private var asked: Set<Int> = []
    /// The question the coach is answering right now.
    @State private var activeTopic: Int?
    @State private var typing = false
    @State private var typingShowsAvatar = true
    /// The coach has said its opening line: the choices appear.
    @State private var opened = false
    /// The coach has given at least one answer: the user may move on.
    @State private var answered = false
    @State private var choice: OnboardingPlanChangeChoice?
    @State private var closingBusy = false
    @State private var closingShown = false

    private static let bottomID = "onboarding-thread-bottom"

    private var example: OnboardingPlanChangeExample {
        OnboardingPlanChangeExample.make(from: store.answers)
    }

    private var topics: [OnboardingChatTopic] {
        OnboardingChatTopic.all(example: example)
    }

    private var remaining: [OnboardingChatTopic] {
        topics.filter { !asked.contains($0.id) }
    }

    /// The coach is typing an answer or its closing line: questions wait.
    private var busy: Bool {
        activeTopic != nil || closingBusy
    }

    private var timeLabel: String {
        "Today \(Fmt.day(Date(), "HH:mm"))"
    }

    /// Changes every time something is added to the thread, to scroll to the newest message.
    private var scrollToken: String {
        "\(entries.count)-\(typing)-\(asked.count)-\(choice != nil)-\(answered)-\(opened)"
    }

    var body: some View {
        let left = remaining
        return VStack(spacing: 0) {
            OnboardingHeaderSpace()
            titleHeader
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
            if opened, !left.isEmpty {
                OnboardingQuestionChips(topics: left, enabled: !busy, onAsk: { ask($0) })
                    .transition(.opacity)
            }
            if answered {
                BottomBar {
                    PrimaryButton(title: "Continue") { nav.next(store) }
                }
                .transition(.opacity)
            }
        }
        .background(Color.white.ignoresSafeArea())
        .sensoryFeedback(.success, trigger: choice)
        .task {
            await playOpening()
        }
        .task(id: activeTopic) {
            await playAnswer()
        }
        .task(id: choice) {
            await playClosing()
        }
    }

    // MARK: Pieces

    private var titleHeader: some View {
        HStack(alignment: .center, spacing: 12) {
            CoachAvatar(coach: store.coach, size: 48)
            Text("Here's how you talk to \(store.coach.name)")
                .font(Theme.title(24))
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 14)
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
            ForEach(entries) { entry in
                switch entry.kind {
                case .user:
                    userBubble(entry.text)
                        .transition(Self.userEntrance)
                case .coach:
                    coachBubble(entry.text, showsAvatar: entry.showsAvatar)
                        .transition(Self.coachEntrance)
                case .card:
                    cardRow(demo)
                        .transition(Self.coachEntrance)
                }
            }
            if typing {
                typingRow
                    .transition(Self.coachEntrance)
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

    // MARK: Conversation

    private func append(_ kind: OnboardingThreadEntry.Kind, _ text: String, showsAvatar: Bool = false) {
        entries.append(OnboardingThreadEntry(id: entries.count, kind: kind, text: text, showsAvatar: showsAvatar))
    }

    /// Seconds the coach "types": the longer the message, the longer it takes.
    private static func typingTime(_ text: String) -> Double {
        min(1.6, 0.7 + Double(text.count) * 0.009)
    }

    /// The coach opens the conversation, then the choices appear; cancelled with the view (`.task`).
    @MainActor
    private func playOpening() async {
        guard !opened else { return }
        if reduceMotion {
            append(.coach, OnboardingChatTopic.opening, showsAvatar: true)
            opened = true
            return
        }
        await Self.wait(0.5)
        if Task.isCancelled { return }
        typingShowsAvatar = true
        withAnimation(Self.pop) { typing = true }
        await Self.wait(0.9)
        if Task.isCancelled {
            typing = false
            return
        }
        withAnimation(Self.pop) {
            typing = false
            append(.coach, OnboardingChatTopic.opening, showsAvatar: true)
        }
        await Self.wait(0.35)
        if Task.isCancelled { return }
        withAnimation(.easeOut(duration: 0.3)) {
            opened = true
        }
    }

    /// The user taps a choice: it becomes their bubble, and the coach starts answering.
    private func ask(_ topic: OnboardingChatTopic) {
        guard opened, !busy, !asked.contains(topic.id) else { return }
        if reduceMotion {
            asked.insert(topic.id)
            append(.user, topic.question)
        } else {
            withAnimation(Self.pop) {
                asked.insert(topic.id)
                append(.user, topic.question)
            }
        }
        activeTopic = topic.id
    }

    /// The coach types, then answers the question that was just asked (`.task(id: activeTopic)`).
    @MainActor
    private func playAnswer() async {
        guard let id = activeTopic, let topic = topics.first(where: { $0.id == id }) else { return }
        let count = topic.answers.count
        if reduceMotion {
            for index in 0..<count {
                let isLast = index == count - 1
                append(.coach, topic.answers[index], showsAvatar: isLast && !topic.opensPlanChange)
            }
            if topic.opensPlanChange { append(.card, "") }
            answered = true
            activeTopic = nil
            return
        }
        for index in 0..<count {
            let text = topic.answers[index]
            let isLast = index == count - 1
            let showsAvatar = isLast && !topic.opensPlanChange
            await Self.wait(0.45)
            if Task.isCancelled { return }
            typingShowsAvatar = showsAvatar
            withAnimation(Self.pop) { typing = true }
            await Self.wait(Self.typingTime(text))
            if Task.isCancelled {
                typing = false
                return
            }
            withAnimation(Self.pop) {
                typing = false
                append(.coach, text, showsAvatar: showsAvatar)
                // The live example: the change to accept or not comes with the coach's line.
                if isLast, topic.opensPlanChange { append(.card, "") }
            }
        }
        // From the first answer on, the user may move on or keep asking.
        if !answered {
            await Self.wait(0.4)
            if Task.isCancelled { return }
            withAnimation(.easeOut(duration: 0.3)) {
                answered = true
            }
        }
        activeTopic = nil
    }

    /// Resolves the card. Nothing is saved: the plan is only built at the end of the questionnaire.
    private func choose(_ value: OnboardingPlanChangeChoice) {
        guard choice == nil else { return }
        closingBusy = true
        if reduceMotion {
            choice = value
        } else {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                choice = value
            }
        }
    }

    /// After the choice: the coach's closing line (`.task(id: choice)`).
    @MainActor
    private func playClosing() async {
        guard choice != nil, !closingShown else { return }
        if reduceMotion {
            append(.coach, OnboardingChatTopic.closingLine, showsAvatar: true)
            closingShown = true
            closingBusy = false
            return
        }
        // Let the coach finish an answer it is still typing.
        while activeTopic != nil {
            await Self.wait(0.2)
            if Task.isCancelled { return }
        }
        await Self.wait(0.6)
        if Task.isCancelled { return }
        typingShowsAvatar = true
        withAnimation(Self.pop) { typing = true }
        await Self.wait(1.0)
        if Task.isCancelled {
            typing = false
            return
        }
        withAnimation(Self.pop) {
            typing = false
            append(.coach, OnboardingChatTopic.closingLine, showsAvatar: true)
            closingShown = true
            closingBusy = false
        }
    }
}

#Preview {
    OnboardingMessageScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
