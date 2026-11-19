import SwiftUI

// PLACEHOLDER OFFER — the paywall is fake for now: no StoreKit, nothing is charged.
enum OnboardingPaywallOffer {
    static let trialDays = 3
    static let monthlyPrice = "€8.33"
    static let yearlyPrice = "€99.99"
}

/// Screen 28: invitation to the free trial. The headline is there from the start in light grey
/// and turns bold word by word; then the rest settles and the button comes last.
struct OnboardingTrialScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var emphasised = false
    /// 1 subtitle, 2-4 benefits, 5 button.
    @State private var stage = 0

    private static let stageDelays: [Double] = [0.15, 0.35, 0.18, 0.18, 0.45]

    private var benefits: [String] {
        let weeks = store.plan?.weeks.count ?? PlanEngine.maxWeeks
        return [
            "Your \(OnboardingCopy.weeks(weeks)) of plan, session by session",
            "\(store.coach.name) to explain every run and answer your questions",
            "Voice guidance during your runs",
        ]
    }

    var body: some View {
        let list = benefits
        return OnboardingScaffold(buttonsVisible: stage >= Self.stageDelays.count) {
            CoachAvatar(coach: store.coach, size: 96)
            OnboardingEmphasisText(text: "That's why we really want you to try Atala Pro for free.",
                                   size: 32,
                                   lineSpacing: 5,
                                   highlight: "for free.",
                                   onFinished: { emphasised = true })
                .accessibilityAddTraits(.isHeader)
            Text("So we can reach your goal together. \(OnboardingPaywallOffer.trialDays) days, every feature, nothing to pay today.")
                .font(.system(size: 17))
                .foregroundStyle(Color(hex: 0x5A5A5A))
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
                .onboardingStaged(0, stage: stage)
            VStack(alignment: .leading, spacing: 14) {
                ForEach(list.indices, id: \.self) { index in
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(Theme.green)
                            .accessibilityHidden(true)
                        Text(list[index])
                            .font(.system(size: 16))
                            .foregroundStyle(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .onboardingStaged(index + 1, stage: stage)
                }
            }
            .padding(.top, 6)
        }
        .onboardingStageSequence($stage, delays: Self.stageDelays, isActive: emphasised)
    }
}

private struct OnboardingTimelineItem: Identifiable {
    let id: Int
    let symbol: String
    let color: Color
    let title: String
    let text: String
}

/// Screen 29: how the free trial goes. The timeline builds itself: a node and its text, the line
/// grows down to the next node, and so on; the button comes last.
struct OnboardingTimelineScreen: View {
    /// Odd stages bring a node and its text, even stages grow the line under it; the last one
    /// shows the button.
    @State private var stage = 0

    private static let stageDelays: [Double] = [0.45, 0.4, 0.5, 0.4, 0.5, 0.55]

    private var items: [OnboardingTimelineItem] {
        let days = OnboardingPaywallOffer.trialDays
        return [
            OnboardingTimelineItem(id: 0, symbol: "lock.open.fill", color: Theme.green, title: "Today",
                                   text: "You unlock everything: your full plan, your coach, voice guidance."),
            OnboardingTimelineItem(id: 1, symbol: "bell.fill", color: Theme.yellow, title: "In \(days - 1) days",
                                   text: "We remind you that your trial ends the next day."),
            OnboardingTimelineItem(id: 2, symbol: "star.fill", color: Theme.orange, title: "In \(days) days",
                                   text: "Your subscription starts and you carry on with Atala. Cancel before and you pay nothing."),
        ]
    }

    var body: some View {
        let list = items
        return OnboardingScaffold(title: "How your free trial works",
                                  buttonsVisible: stage >= Self.stageDelays.count) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(list) { item in
                    row(item, isLast: item.id == list.count - 1)
                }
            }
            .padding(.top, 8)
        }
        .onboardingStageSequence($stage, delays: Self.stageDelays)
    }

    private func row(_ item: OnboardingTimelineItem, isLast: Bool) -> some View {
        let shown = stage > item.id * 2
        let lineGrown = stage > item.id * 2 + 1
        return HStack(alignment: .top, spacing: 16) {
            VStack(spacing: 0) {
                Circle()
                    .fill(item.color)
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: item.symbol)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(Color.white)
                    )
                    .scaleEffect(shown ? 1 : 0.5)
                    .opacity(shown ? 1 : 0)
                if !isLast {
                    Rectangle()
                        .fill(item.color.opacity(0.4))
                        .frame(width: 4)
                        .frame(maxHeight: .infinity)
                        .scaleEffect(x: 1, y: lineGrown ? 1 : 0, anchor: .top)
                }
            }
            .frame(width: 44)
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(item.title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Text(item.text)
                    .font(.system(size: 15))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.top, 2)
            .padding(.bottom, isLast ? 0 : 34)
            .opacity(shown ? 1 : 0)
            .offset(y: shown ? 0 : 10)
            Spacer(minLength: 0)
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// Screen 30: the reminder before the end of the trial, with a ringing bell. The title turns bold
/// word by word, then the subtitle fades in, then the button.
struct OnboardingReminderScreen: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var ringing = false
    @State private var emphasised = false
    /// 1 subtitle, 2 button.
    @State private var stage = 0

    private static let stageDelays: [Double] = [0.2, 0.6]

    var body: some View {
        OnboardingScaffold(buttonsVisible: stage >= Self.stageDelays.count) {
            VStack(spacing: 28) {
                Circle()
                    .fill(Theme.greenTint)
                    .frame(width: 168, height: 168)
                    .overlay(
                        Image(systemName: "bell.fill")
                            .font(.system(size: 68, weight: .semibold))
                            .foregroundStyle(Theme.green)
                            .rotationEffect(.degrees(reduceMotion ? 0 : (ringing ? 16 : -16)), anchor: .top)
                            .animation(.easeInOut(duration: 0.16).repeatForever(autoreverses: true), value: ringing)
                    )
                    .accessibilityHidden(true)
                OnboardingEmphasisText(text: "We'll remind you before your free trial ends",
                                       size: 28,
                                       centered: true,
                                       onFinished: { emphasised = true })
                    .accessibilityAddTraits(.isHeader)
                Text("No worries :) You get a reminder the day before, so you can try Atala with peace of mind.")
                    .font(.system(size: 17))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .lineSpacing(5)
                    .fixedSize(horizontal: false, vertical: true)
                    .onboardingStaged(0, stage: stage)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.top, 56)
        }
        .onboardingStageSequence($stage, delays: Self.stageDelays, isActive: emphasised)
        .onAppear { ringing = true }
    }
}

#Preview("Trial") {
    OnboardingTrialScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Timeline") {
    OnboardingTimelineScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Reminder") {
    OnboardingReminderScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
