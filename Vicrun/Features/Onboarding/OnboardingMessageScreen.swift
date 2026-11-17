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

/// Screen 20: the coach explains missed sessions, as a message thread.
struct OnboardingMessageScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var visibleCount = 0

    private var timeLabel: String {
        "Today \(Fmt.day(Date(), "HH:mm"))"
    }

    var body: some View {
        OnboardingScaffold(buttonTitle: "Got it, let's go") {
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
                            .transition(.asymmetric(
                                insertion: .scale(scale: 0.85, anchor: line.fromUser ? .bottomTrailing : .bottomLeading)
                                    .combined(with: .opacity),
                                removal: .opacity
                            ))
                    }
                }
                if visibleCount >= OnboardingThreadLine.all.count {
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
            guard visibleCount == 0 else { return }
            for index in OnboardingThreadLine.all.indices {
                let pause: UInt64 = index == 0 ? 450_000_000 : 850_000_000
                try? await Task.sleep(nanoseconds: pause)
                if Task.isCancelled { return }
                withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                    visibleCount = index + 1
                }
            }
        }
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
