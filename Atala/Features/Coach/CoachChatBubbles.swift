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
        .accessibilityLabel(L("%@ is writing", coach.name))
    }
}

/// Shown before the first message: a short invitation and suggested messages, sent as soon as they are tapped.
struct CoachChatEmptyState: View {
    let coach: Coach
    let prompts: [String]
    let onPick: (String) -> Void

    private var coachName: String { coach.name }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .bottom, spacing: 12) {
                VStack(alignment: .leading, spacing: 10) {
                    Text(L("Ask %@ anything", coachName))
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Theme.ink)
                    Text(L("Your plan, a session, your paces, a busy week or tired legs. Write whenever you need."))
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.muted)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                CoachPoseView(coach: coach, pose: .present)
                    .frame(width: 84, height: 132)
                    .accessibilityHidden(true)
            }
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "calendar.badge.clock")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.green)
                    .accessibilityHidden(true)
                Text(L("%@ can suggest a change to your plan. Nothing moves until you tap Apply.", coachName))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.green)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.greenTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .padding(.top, 2)
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

/// Suggested messages kept within reach above the input field. Tapping one sends it.
struct CoachChatQuickPrompts: View {
    let prompts: [String]
    let onPick: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(prompts, id: \.self) { prompt in
                    Button(action: { onPick(prompt) }) {
                        Text(prompt)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Theme.ink)
                            .lineLimit(1)
                            .padding(.horizontal, 14)
                            .frame(height: 40)
                            .background(Theme.surface, in: Capsule())
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, Theme.screenPadding)
        }
        .padding(.top, 8)
    }
}

#Preview {
    VStack(spacing: 10) {
        CoachChatMessageRow(message: ChatMessage(role: .user, text: "Slept badly. Do I keep the intervals tonight?"),
                            coach: Coach.all[0])
        CoachChatMessageRow(message: ChatMessage(role: .coach, text: "Let's not force it. Here is what I suggest."),
                            coach: Coach.all[0])
        CoachChatTypingRow(coach: Coach.all[0])
        CoachChatEmptyState(coach: Coach.all[0], prompts: ["What's my next session?"], onPick: { _ in })
    }
    .padding()
    .environmentObject(AppStore.preview)
}
