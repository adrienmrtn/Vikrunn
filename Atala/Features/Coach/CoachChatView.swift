import SwiftUI

/// The Coach tab: a conversation with the chosen coach. The coach only answers, never writes first.
/// Talking to the coach is part of Atala Pro: without it the conversation is shown blurred, behind
/// the padlock card that opens the paywall. The tab bar stays in use.
@MainActor
struct CoachChatView: View {
    @EnvironmentObject private var store: AppStore
    @State private var draft: String = ""
    @State private var isSending: Bool = false
    @ObservedObject private var purchases = PurchaseService.shared
    @FocusState private var inputFocused: Bool
    /// Asked once, before the first message leaves for the AI provider (App Store guideline 5.1.2).
    @AppStorage("coachAIConsent") private var aiConsent: Bool = false
    /// The message waiting for that answer.
    @State private var pendingText: String?

    private static let bottomID = "coach-chat-bottom"

    init() {}

    /// Suggestions built from the current plan: the first ones lead to a plan change the user can apply.
    private var prompts: [String] {
        CoachLocalProposals.suggestedPrompts(store: store)
    }

    var body: some View {
        NavigationStack {
            chat
                .proLocked(symbol: "bubble.left.and.bubble.right.fill",
                           title: L("Unlock your coach"),
                           message: L("Ask %@ anything, any time: your plan, a session, tired legs or a busy week. The answers are made for you.", store.coach.name))
                .navigationTitle(store.coach.name)
                .navigationBarTitleDisplayMode(.large)
                .task(id: purchases.isPro) {
                    // Signed in with an empty conversation: bring back the one saved in the account.
                    if purchases.isPro, store.messages.isEmpty, !isSending {
                        await CoachService.restoreHistory(store: store)
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        if purchases.isPro {
                            CoachChatToneMenu(tone: $store.answers.tone)
                        }
                    }
                }
        }
    }

    /// The conversation and its input field: what Atala Pro unlocks.
    private var chat: some View {
        messageList
            .background(Color.white)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                bottomBar
            }
            .alert(L("Your coach uses AI"), isPresented: consentShown) {
                Button(L("Not now"), role: .cancel) { pendingText = nil }
                Button(L("Continue")) {
                    aiConsent = true
                    let text = pendingText
                    pendingText = nil
                    if let text { send(text) }
                }
            } message: {
                Text(L("To answer you, Atala sends your messages, your first name, your plan and your recent runs to our AI provider (Google Gemini, via fal.ai). They are only used to write the replies, never for advertising."))
            }
    }

    private var consentShown: Binding<Bool> {
        Binding(get: { pendingText != nil }, set: { if !$0 { pendingText = nil } })
    }

    // MARK: Messages

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 10) {
                    CoachChatHeader(coach: store.coach, runCount: store.runs.count)
                    banner
                    if store.messages.isEmpty {
                        if purchases.isPro {
                            CoachChatEmptyState(coach: store.coach, prompts: prompts) { prompt in
                                send(prompt)
                            }
                        } else {
                            CoachChatSample(coach: store.coach)
                        }
                    }
                    ForEach(store.messages) { message in
                        CoachChatMessageRow(message: message, coach: store.coach)
                    }
                    if isSending {
                        CoachChatTypingRow(coach: store.coach)
                    }
                    Color.clear
                        .frame(height: 1)
                        .id(Self.bottomID)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 4)
                .padding(.bottom, 8)
            }
            .scrollDismissesKeyboard(.interactively)
            .onAppear { scrollToBottom(proxy, animated: false) }
            .onChange(of: store.messages.count) { _, _ in scrollToBottom(proxy, animated: true) }
            .onChange(of: isSending) { _, _ in scrollToBottom(proxy, animated: true) }
            .onChange(of: inputFocused) { _, focused in
                if focused { scrollToBottom(proxy, animated: true) }
            }
        }
    }

    private var banner: some View {
        Color.clear
            .frame(height: 104)
            .frame(maxWidth: .infinity)
            .overlay(
                Image("illu_coach")
                    .resizable()
                    .scaledToFill()
            )
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .accessibilityHidden(true)
            .padding(.bottom, 4)
    }

    private func scrollToBottom(_ proxy: ScrollViewProxy, animated: Bool) {
        Task { @MainActor in
            // Let the new row lay out first.
            try? await Task.sleep(nanoseconds: 60_000_000)
            if animated {
                withAnimation(.easeOut(duration: 0.25)) {
                    proxy.scrollTo(Self.bottomID, anchor: .bottom)
                }
            } else {
                proxy.scrollTo(Self.bottomID, anchor: .bottom)
            }
        }
    }

    // MARK: Input

    private var canSend: Bool {
        !isSending && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Quick suggestions (once the conversation has started) above the input field, pinned at the bottom.
    private var bottomBar: some View {
        VStack(spacing: 0) {
            if !store.messages.isEmpty && !isSending && draft.isEmpty {
                CoachChatQuickPrompts(prompts: prompts) { prompt in
                    send(prompt)
                }
            }
            inputBar
        }
        .background(Color.white)
    }

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField(L("Write to your coach"), text: $draft, axis: .vertical)
                .lineLimit(1...4)
                .font(.system(size: 15))
                .foregroundStyle(Theme.ink)
                .focused($inputFocused)
                .padding(.horizontal, 18)
                .padding(.vertical, 13)
                .frame(minHeight: 48)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(Color(hex: 0xCFCFCF), lineWidth: 1)
                )
                .accessibilityLabel(L("Message to your coach"))
            Button(action: { send(draft) }) {
                Image(systemName: "arrow.up")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color.white)
                    .frame(width: 48, height: 48)
                    .background(Theme.green, in: Circle())
                    .opacity(canSend ? 1 : 0.4)
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
            .accessibilityLabel(L("Send"))
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background(Color.white)
    }

    private func send(_ text: String) {
        let trimmed = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(2000))
        // Talking to the coach is part of Atala Pro (the screen is locked without it).
        guard purchases.isPro, !trimmed.isEmpty, !isSending else { return }
        // Nothing goes to the AI provider before the user has agreed to it (the draft stays in the field).
        guard aiConsent else {
            pendingText = trimmed
            return
        }
        Haptics.tap()
        draft = ""
        store.messages.append(ChatMessage(role: .user, text: trimmed))
        isSending = true
        Task {
            let reply = await CoachService.send(trimmed, store: store)
            store.messages.append(reply)
            isSending = false
            // The coach's reply has arrived.
            Haptics.soft()
        }
    }
}

// MARK: - Header

/// First row of the conversation, under the navigation title: avatar with the online dot and a caption.
struct CoachChatHeader: View {
    let coach: Coach
    let runCount: Int

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            CoachAvatar(coach: coach, size: 44)
                .overlay(alignment: .bottomTrailing) {
                    Circle()
                        .fill(Theme.greenSoft)
                        .frame(width: 13, height: 13)
                        .overlay(Circle().stroke(Color.white, lineWidth: 2.5))
                        .accessibilityLabel(L("Online"))
                }
            VStack(alignment: .leading, spacing: 2) {
                Text(coach.trait)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 4)
    }

    private var subtitle: String {
        if runCount == 1 { return L("Your coach · knows your %@ run", runCount) }
        if runCount > 1 { return L("Your coach · knows your %@ runs", runCount) }
        return L("Your coach")
    }
}

/// Without Atala Pro and with no conversation yet: a short example of an exchange, seen blurred
/// behind the padlock card. Never sent anywhere, never saved.
struct CoachChatSample: View {
    let coach: Coach

    var body: some View {
        VStack(spacing: 10) {
            CoachChatMessageRow(message: ChatMessage(role: .user, text: L("My legs feel heavy today. Should I still run?")),
                                coach: coach)
            CoachChatMessageRow(message: ChatMessage(role: .coach, text: L("Thanks for telling me. Swap today for an easy 20 minutes, and we move your intervals to Thursday. Does that work for you?")),
                                coach: coach)
            CoachChatMessageRow(message: ChatMessage(role: .user, text: L("Perfect. And what pace for my long run on Sunday?")),
                                coach: coach)
            CoachChatMessageRow(message: ChatMessage(role: .coach, text: L("Slow enough to talk the whole way. I will tell you when to hold back and when to push.")),
                                coach: coach)
        }
        .accessibilityHidden(true)
    }
}

/// Tone picker shown in the navigation bar.
struct CoachChatToneMenu: View {
    @Binding var tone: CoachTone

    var body: some View {
        Menu {
            ForEach(CoachTone.allCases) { option in
                Button {
                    if option != tone { Haptics.select() }
                    tone = option
                } label: {
                    if option == tone {
                        Label(option.title, systemImage: "checkmark")
                    } else {
                        Text(option.title)
                    }
                }
            }
        } label: {
            Text(L("Tone: %@", tone.title))
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Theme.ink)
        }
        .accessibilityLabel(L("Coach tone: %@", tone.title))
    }
}

#Preview {
    CoachChatView()
        .environmentObject(AppStore.preview)
}
