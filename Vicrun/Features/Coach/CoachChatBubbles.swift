import SwiftUI

/// One message of the conversation: user bubble on the right, coach bubble (and its proposal) on the left.
struct CoachChatMessageRow: View {
    let message: ChatMessage
    let coach: Coach

    var body: some View {
        if message.role == .user {
            HStack(spacing: 0) {
                Spacer(minLength: 48)
                Text(message.text)
                    .font(.system(size: 15))
                    .foregroundStyle(Color.white)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(Theme.ink, in: UnevenRoundedRectangle(topLeadingRadius: 20, bottomLeadingRadius: 20,
                                                                      bottomTrailingRadius: 6, topTrailingRadius: 20,
                                                                      style: .continuous))
            }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .bottom, spacing: 8) {
                    CoachAvatar(coach: coach, size: 30)
                    Text(message.text)
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.ink)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(Theme.bubble, in: UnevenRoundedRectangle(topLeadingRadius: 20, bottomLeadingRadius: 6,
                                                                             bottomTrailingRadius: 20, topTrailingRadius: 20,
                                                                             style: .continuous))
                    Spacer(minLength: 32)
                }
                if let proposal = message.proposal {
                    CoachChatProposalCard(messageID: message.id, proposal: proposal)
                }
            }
        }
    }
}

/// Three pulsing dots next to the coach's avatar while the answer is on its way.
struct CoachChatTypingRow: View {
    let coach: Coach
    @State private var pulsing: Bool = false

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            CoachAvatar(coach: coach, size: 30)
            HStack(spacing: 5) {
                ForEach(0..<3, id: \.self) { index in
                    Circle()
                        .fill(Theme.muted)
                        .frame(width: 7, height: 7)
                        .opacity(pulsing ? 1 : 0.3)
                        .animation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true).delay(Double(index) * 0.2),
                                   value: pulsing)
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 44)
            .background(Theme.bubble, in: UnevenRoundedRectangle(topLeadingRadius: 20, bottomLeadingRadius: 6,
                                                                 bottomTrailingRadius: 20, topTrailingRadius: 20,
                                                                 style: .continuous))
            Spacer(minLength: 0)
        }
        .onAppear { pulsing = true }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(coach.name) is writing")
    }
}

/// Shown before the first message: a short invitation and three suggested questions.
struct CoachChatEmptyState: View {
    let coachName: String
    let prompts: [String]
    let onPick: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Ask \(coachName) anything")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.ink)
            Text("Your plan, a session, your paces, a busy week or tired legs. Write whenever you need.")
                .font(.system(size: 14))
                .foregroundStyle(Theme.muted)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 8) {
                ForEach(prompts, id: \.self) { prompt in
                    Button(action: { onPick(prompt) }) {
                        HStack(spacing: 10) {
                            Text(prompt)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(Theme.ink)
                                .multilineTextAlignment(.leading)
                            Spacer(minLength: 8)
                            Image(systemName: "arrow.up.right")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(Theme.muted)
                        }
                        .padding(.horizontal, 16)
                        .frame(minHeight: 48)
                        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
        .padding(.bottom, 4)
    }
}

#Preview {
    VStack(spacing: 10) {
        CoachChatMessageRow(message: ChatMessage(role: .user, text: "Slept badly. Do I keep the intervals tonight?"),
                            coach: Coach.all[0])
        CoachChatMessageRow(message: ChatMessage(role: .coach, text: "Let's not force it. Here is what I suggest."),
                            coach: Coach.all[0])
        CoachChatTypingRow(coach: Coach.all[0])
        CoachChatEmptyState(coachName: "Awa", prompts: ["What's my next session?"], onPick: { _ in })
    }
    .padding()
    .environmentObject(AppStore.preview)
}
