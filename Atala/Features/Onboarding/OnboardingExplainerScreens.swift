import SwiftUI

/// Screen 4: the chosen coach says hello, word by word, over the photo background.
struct OnboardingHelloScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var greeted = false
    @State private var finished = false

    private var speech: String {
        L("I'm %@, and I'll be with you all the way to your goal. Tell me about your running and we'll build your plan together.",
          store.coach.name)
    }

    var body: some View {
        OnboardingTapScreen(hintColor: Color.white, hintShadow: true, canContinue: finished, pose: .wave, backdrop: {
            Color(hex: 0x2F5A2A)
                .overlay {
                    Image("bg_hello")
                        .resizable()
                        .scaledToFill()
                }
        }, top: {
            VStack(alignment: .leading, spacing: 8) {
                OnboardingSpokenText(text: L("Nice to meet you, %@.", store.firstName),
                                     size: 28, weight: .bold, color: Theme.ink, lineSpacing: 2,
                                     startDelay: OnboardingRevealTiming.speech,
                                     onFinished: { greeted = true })
                OnboardingSpokenText(text: speech,
                                     size: 16, weight: .regular, color: Color(hex: 0x333333), lineSpacing: 5,
                                     isActive: greeted,
                                     onFinished: { finished = true })
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                Color.white.opacity(0.95),
                in: UnevenRoundedRectangle(topLeadingRadius: 24, bottomLeadingRadius: 8,
                                           bottomTrailingRadius: 24, topTrailingRadius: 24)
            )
            .shadow(color: Color(hex: 0x14280A).opacity(0.18), radius: 12, x: 0, y: 8)
        })
    }
}

/// Screen 11: full-screen green moment before the goal questions.
struct OnboardingGoalIntroScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var introduced = false
    @State private var finished = false

    var body: some View {
        OnboardingTapScreen(lightContent: true, canContinue: finished, pose: .present, backdrop: {
            Theme.green
                .overlay(alignment: .bottom) {
                    Circle()
                        .fill(Color(hex: 0x2A6E5B))
                        .frame(width: 630, height: 630)
                        .offset(y: 240)
                }
        }, top: {
            VStack(alignment: .leading, spacing: 14) {
                OnboardingSpokenText(text: L("I know where you're starting from, %@.", store.firstName),
                                     size: 14, weight: .semibold, color: Theme.greenOnDark, lineSpacing: 3,
                                     startDelay: OnboardingRevealTiming.speech,
                                     onFinished: { introduced = true })
                OnboardingSpokenText(text: L("Now it's time to set your goal."),
                                     size: 38, weight: .bold, color: Color.white, lineSpacing: 2,
                                     isActive: introduced,
                                     onFinished: { finished = true })
            }
            .padding(.top, 20)
        })
    }
}

/// Screen 16: the coach answers the "why".
struct OnboardingCoachReplyScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var replied = false
    @State private var finished = false

    private var motivation: Motivation { store.answers.motivation ?? .prove }

    private var chip: String {
        switch motivation {
        case .athletic: return L("You want to get in better shape")
        case .prove: return L("You want to prove to yourself you can do it")
        case .record: return L("You want to beat your record")
        case .health: return L("You want to take care of your health")
        case .clearHead: return L("You want to clear your head")
        case .together: return L("You want to run with people you love")
        }
    }

    private var detail: String {
        let answers = store.answers
        let goal = answers.goal ?? .regular
        let habit = L("A running habit is built session after session. I'll be there for each one.")
        if goal.isOpenEnded {
            return habit
        }
        // One full sentence per goal: the article and the agreements change with the noun.
        if let target = answers.targetSeconds {
            let time = Fmt.goalTime(target)
            switch goal {
            case .fiveK: return L("A 5K in %@ is built session after session. I'll be there for each one.", time)
            case .tenK: return L("A 10K in %@ is built session after session. I'll be there for each one.", time)
            case .half: return L("A half marathon in %@ is built session after session. I'll be there for each one.", time)
            case .regular, .shape: return habit
            }
        }
        switch goal {
        case .fiveK: return L("A 5K is built session after session. I'll be there for each one.")
        case .tenK: return L("A 10K is built session after session. I'll be there for each one.")
        case .half: return L("A half marathon is built session after session. I'll be there for each one.")
        case .regular, .shape: return habit
        }
    }

    var body: some View {
        OnboardingTapScreen(hintColor: Theme.orangeDeep, canContinue: finished, pose: .thumbsUp, backdrop: {
            Theme.orangeTint
                .overlay(alignment: .bottom) {
                    Circle()
                        .fill(Color(hex: 0xF6D3BD))
                        .frame(width: 630, height: 630)
                        .offset(y: 240)
                }
        }, top: {
            VStack(alignment: .leading, spacing: 14) {
                Text(chip)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.white, in: Capsule())
                OnboardingSpokenText(text: motivation.coachReply,
                                     size: 34, weight: .bold, color: Theme.ink, lineSpacing: 2,
                                     startDelay: OnboardingRevealTiming.speech,
                                     onFinished: { replied = true })
                OnboardingSpokenText(text: detail,
                                     size: 17, weight: .regular, color: Color(hex: 0x4A3A30), lineSpacing: 5,
                                     isActive: replied,
                                     onFinished: { finished = true })
            }
            .padding(.top, 20)
        })
    }
}

#Preview("Hello") {
    OnboardingHelloScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Goal intro") {
    OnboardingGoalIntroScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Coach reply") {
    OnboardingCoachReplyScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
