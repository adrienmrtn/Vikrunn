import SwiftUI

/// The Coach tab: a conversation with the chosen coach. The coach only answers, never writes first.
@MainActor
struct CoachChatView: View {
    @EnvironmentObject private var store: AppStore
    @State private var draft: String = ""
    @State private var isSending: Bool = false
    @FocusState private var inputFocused: Bool

    private static let bottomID = "coach-chat-bottom"
    private static let prompts: [String] = [
        "What's my next session?",
        "I'm tired today, can we move my run?",
        "What pace should my easy runs be?",
    ]

    init() {}

    var body: some View {
        VStack(spacing: 0) {
            CoachChatHeader(coach: store.coach, runCount: store.runs.count, tone: $store.answers.tone)
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 12)
                .padding(.bottom, 10)
            messageList
            inputBar
        }
        .background(Color.white)
    }

    // MARK: Messages

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 10) {
                    banner
                    if store.messages.isEmpty {
                        CoachChatEmptyState(coachName: store.coach.name, prompts: Self.prompts) { prompt in
                            send(prompt)
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

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("Write to your coach", text: $draft, axis: .vertical)
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
                .accessibilityLabel("Message to your coach")
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
            .accessibilityLabel("Send")
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background(Color.white)
    }

    private func send(_ text: String) {
        let trimmed = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(2000))
        guard !trimmed.isEmpty, !isSending else { return }
        draft = ""
        store.messages.append(ChatMessage(role: .user, text: trimmed))
        isSending = true
        Task {
            let reply = await CoachService.send(trimmed, store: store)
            store.messages.append(reply)
            isSending = false
        }
    }
}

// MARK: - Header

/// Coach avatar with the online dot, name, and the tone picker.
struct CoachChatHeader: View {
    let coach: Coach
    let runCount: Int
    @Binding var tone: CoachTone

    var body: some View {
        HStack(alignment: .bottom, spacing: 12) {
            CoachAvatar(coach: coach, size: 52)
                .overlay(alignment: .bottomTrailing) {
                    Circle()
                        .fill(Theme.greenSoft)
                        .frame(width: 14, height: 14)
                        .overlay(Circle().stroke(Color.white, lineWidth: 2.5))
                        .accessibilityLabel("Online")
                }
            VStack(alignment: .leading, spacing: 2) {
                Text(coach.name)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            Spacer(minLength: 8)
            toneMenu
        }
    }

    private var subtitle: String {
        if runCount == 1 { return "Your coach · knows your 1 run" }
        if runCount > 1 { return "Your coach · knows your \(runCount) runs" }
        return "Your coach"
    }

    private var toneMenu: some View {
        Menu {
            ForEach(CoachTone.allCases) { option in
                Button {
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
            Text("Tone: \(tone.title)")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .fixedSize()
                .padding(.horizontal, 14)
                .frame(height: 44)
                .background(Color.white, in: Capsule())
                .overlay(Capsule().stroke(Color(hex: 0xCFCFCF), lineWidth: 1))
        }
        .accessibilityLabel("Coach tone: \(tone.title)")
    }
}

#Preview {
    CoachChatView()
        .environmentObject(AppStore.preview)
}
