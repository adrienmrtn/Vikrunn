import SwiftUI

// PLACEHOLDER OFFER — the paywall is fake for now: no StoreKit, nothing is charged.
enum OnboardingPaywallOffer {
    static let trialDays = 3
    static let monthlyPrice = "€8.33"
    static let yearlyPrice = "€99.99"
}

/// Screen 28: invitation to the free trial.
struct OnboardingTrialScreen: View {
    @EnvironmentObject private var store: AppStore

    private var benefits: [String] {
        let weeks = store.plan?.weeks.count ?? PlanEngine.maxWeeks
        return [
            "Your \(OnboardingCopy.weeks(weeks)) of plan, session by session",
            "\(store.coach.name) to explain every run and answer your questions",
            "Voice guidance during your runs",
        ]
    }

    private var headline: Text {
        Text("We want you to try Vicrun ")
            + Text("for free").foregroundColor(Theme.green)
            + Text(" for \(OnboardingPaywallOffer.trialDays) days")
    }

    var body: some View {
        OnboardingScaffold {
            CoachAvatar(coach: store.coach, size: 96)
            headline
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(Theme.ink)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text("Your plan, your coach, every feature. You pay nothing today.")
                .font(.system(size: 17))
                .foregroundStyle(Color(hex: 0x5A5A5A))
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
            VStack(alignment: .leading, spacing: 14) {
                ForEach(benefits, id: \.self) { benefit in
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(Theme.green)
                            .accessibilityHidden(true)
                        Text(benefit)
                            .font(.system(size: 16))
                            .foregroundStyle(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(.top, 6)
        }
    }
}

private struct OnboardingTimelineItem: Identifiable {
    let id: Int
    let symbol: String
    let color: Color
    let title: String
    let text: String
}

/// Screen 29: how the free trial goes.
struct OnboardingTimelineScreen: View {
    @EnvironmentObject private var store: AppStore

    private var items: [OnboardingTimelineItem] {
        let days = OnboardingPaywallOffer.trialDays
        return [
            OnboardingTimelineItem(id: 0, symbol: "lock.open.fill", color: Theme.green, title: "Today",
                                   text: "You unlock everything: your full plan, your coach, voice guidance."),
            OnboardingTimelineItem(id: 1, symbol: "bell.fill", color: Theme.yellow, title: "In \(days - 1) days",
                                   text: "We remind you that your trial ends the next day."),
            OnboardingTimelineItem(id: 2, symbol: "star.fill", color: Theme.orange, title: "In \(days) days",
                                   text: "Your subscription starts and you carry on with Vicrun. Cancel before and you pay nothing."),
        ]
    }

    var body: some View {
        OnboardingScaffold(title: "How your free trial works") {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(items) { item in
                    row(item, isLast: item.id == items.count - 1)
                }
            }
            .padding(.top, 8)
        }
    }

    private func row(_ item: OnboardingTimelineItem, isLast: Bool) -> some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(spacing: 0) {
                Circle()
                    .fill(item.color)
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: item.symbol)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(Color.white)
                    )
                if !isLast {
                    Rectangle()
                        .fill(item.color.opacity(0.4))
                        .frame(width: 4)
                        .frame(maxHeight: .infinity)
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
            Spacer(minLength: 0)
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// Screen 30: the reminder before the end of the trial, with a ringing bell.
struct OnboardingReminderScreen: View {
    @State private var ringing = false

    var body: some View {
        OnboardingScaffold {
            VStack(spacing: 28) {
                Circle()
                    .fill(Theme.greenTint)
                    .frame(width: 168, height: 168)
                    .overlay(
                        Image(systemName: "bell.fill")
                            .font(.system(size: 68, weight: .semibold))
                            .foregroundStyle(Theme.green)
                            .rotationEffect(.degrees(ringing ? 16 : -16), anchor: .top)
                            .animation(.easeInOut(duration: 0.16).repeatForever(autoreverses: true), value: ringing)
                    )
                    .accessibilityHidden(true)
                Text("We'll tell you before your trial ends")
                    .font(Theme.title(28))
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text("You get a reminder the day before. You have time to decide, with no surprise on your account.")
                    .font(.system(size: 17))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .lineSpacing(5)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.top, 56)
        }
        .onAppear { ringing = true }
    }
}

/// Screen 31: the paywall. Fake and non-blocking: the close button and the main button both move on.
struct OnboardingPaywallScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator

    private var offer: Text {
        Text("Try \(OnboardingPaywallOffer.trialDays) days for free,").foregroundColor(Theme.green).bold()
            + Text(" then \(OnboardingPaywallOffer.monthlyPrice) a month (\(OnboardingPaywallOffer.yearlyPrice) billed yearly)")
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: { nav.back(store) }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Back")
                Spacer()
                Button(action: { nav.next(store) }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Close")
            }
            .padding(.horizontal, 8)
            Spacer(minLength: 12)
            VStack(spacing: 24) {
                CoachAvatar(coach: store.coach, size: 72)
                offer
                    .font(.system(size: 22, weight: .medium))
                    .foregroundColor(Theme.ink)
                    .lineSpacing(6)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Prices shown as an example. Nothing is charged in this version.")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 28)
            Spacer(minLength: 12)
            HStack(spacing: 8) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 13))
                Text("Secured by the App Store")
                    .font(.system(size: 14))
            }
            .foregroundStyle(Theme.muted)
            .padding(.bottom, 14)
            Button(action: { nav.next(store) }) {
                VStack(spacing: 2) {
                    Text("Start my free trial")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Color.white)
                    Text("Easy to cancel, at any time")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.greenTint)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 68)
                .background(Theme.green, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(Theme.greenTint)
        }
        .background(Color.white.ignoresSafeArea())
    }
}

#Preview("Trial") {
    OnboardingTrialScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Paywall") {
    OnboardingPaywallScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
