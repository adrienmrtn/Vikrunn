import SwiftUI
import UIKit

extension ProAccess {
    /// Where the user stands in the free week: day 1 to 7 and the days left after today.
    /// Nil once the week is over, or before a plan exists.
    static func freeWeekProgress(plan: TrainingPlan?, now: Date = Date()) -> (day: Int, daysLeft: Int)? {
        guard let end = freeWeekEnd(plan: plan) else { return nil }
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: now)
        guard today < end else { return nil }
        let left = calendar.dateComponents([.day], from: today, to: end).day ?? 0
        let day = min(7, max(1, 8 - left))
        return (day, max(0, left - 1))
    }

    /// Sessions of the plan that are still ahead and behind the lock.
    static func lockedSessionCount(plan: TrainingPlan?) -> Int {
        guard let plan, !isPro else { return 0 }
        return plan.allWorkouts.filter { $0.status == .planned && isLocked($0, plan: plan) }.count
    }
}

/// Small gold "PRO" mark shown next to the name of a subscriber.
struct ProChip: View {
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "crown.fill")
                .font(.system(size: 10, weight: .bold))
            Text("PRO")
                .font(.system(size: 11, weight: .bold))
                .kerning(0.8)
        }
        .foregroundStyle(Theme.forest)
        .padding(.horizontal, 9)
        .padding(.vertical, 5)
        .background(Theme.yellow, in: Capsule())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Atala Pro member")
    }
}

/// Today, for people without Atala Pro: the free week as seven steps, then what waits behind the lock.
struct ProJourneyCard: View {
    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var showPaywall = false

    var body: some View {
        if !purchases.isPro, store.plan != nil {
            Button {
                Haptics.tap()
                showPaywall = true
            } label: {
                content
            }
            .buttonStyle(.plain)
            .proPaywall(isPresented: $showPaywall)
        }
    }

    @ViewBuilder private var content: some View {
        let locked = ProAccess.lockedSessionCount(plan: store.plan)
        if let progress = ProAccess.freeWeekProgress(plan: store.plan) {
            freeWeek(day: progress.day, daysLeft: progress.daysLeft, locked: locked)
        } else {
            weekOver(locked: locked)
        }
    }

    private func freeWeekMessage(daysLeft: Int, locked: Int) -> String {
        let waiting = "\(sessions(locked)) of your plan unlock with Atala Pro."
        if daysLeft == 0 { return "Last free day. " + waiting }
        let days = daysLeft == 1 ? "1 free day" : "\(daysLeft) free days"
        return days + " left. Then " + waiting
    }

    private func freeWeek(day: Int, daysLeft: Int, locked: Int) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("YOUR FREE WEEK")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(0.8)
                    .foregroundStyle(Theme.greenOnDark)
                Spacer(minLength: 8)
                Text("Day \(day) of 7")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.white)
            }
            // Seven steps: the days behind are gold, today is white, the lock waits at the end.
            HStack(spacing: 6) {
                ForEach(1...7, id: \.self) { step in
                    Capsule()
                        .fill(step < day ? Theme.yellow : (step == day ? Color.white : Color.white.opacity(0.22)))
                        .frame(height: 8)
                }
                Image(systemName: "lock.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.yellow)
            }
            .accessibilityHidden(true)
            Text(freeWeekMessage(daysLeft: daysLeft, locked: locked))
                .font(.system(size: 14))
                .foregroundStyle(Color.white)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            unlockLabel
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [Theme.green, Theme.forest], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 22, style: .continuous)
        )
        .accessibilityElement(children: .combine)
    }

    private func weekOver(locked: Int) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Circle()
                    .fill(Theme.yellow)
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: "lock.fill")
                            .font(.system(size: 19, weight: .bold))
                            .foregroundStyle(Theme.forest)
                    )
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Your free week is done")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Color.white)
                    Text(locked > 0
                         ? "\(sessions(locked)) are waiting for you."
                         : "Your coach and your plan are waiting for you.")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.greenOnDark)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            unlockLabel
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [Theme.green, Theme.forest], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 22, style: .continuous)
        )
        .accessibilityElement(children: .combine)
    }

    private func sessions(_ count: Int) -> String {
        count == 1 ? "1 session" : "\(count) sessions"
    }

    private var unlockLabel: some View {
        HStack(spacing: 8) {
            Image(systemName: "crown.fill")
                .font(.system(size: 13, weight: .bold))
            Text("Unlock Atala Pro")
                .font(.system(size: 15, weight: .bold))
        }
        .foregroundStyle(Theme.forest)
        .frame(maxWidth: .infinity)
        .frame(height: 44)
        .background(Color.white, in: Capsule())
    }
}

/// Remembers whether the "Welcome to Atala Pro" moment was already played for this subscription.
enum ProWelcomeState {
    private static let key = "atala.pro.welcomed"

    static var wasPlayed: Bool {
        get { UserDefaults.standard.bool(forKey: key) }
        set { UserDefaults.standard.set(newValue, forKey: key) }
    }
}

/// Plays the welcome once when the user becomes a subscriber, wherever that happened
/// (paywall, gift, restore).
struct ProWelcomePresenter: ViewModifier {
    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .fullScreenCover(isPresented: $shown) {
                ProWelcomeView()
                    .environmentObject(store)
            }
            .task(id: purchases.isPro) {
                guard purchases.isPro else {
                    // Not subscribed (any more): the next subscription is celebrated again.
                    ProWelcomeState.wasPlayed = false
                    return
                }
                guard !ProWelcomeState.wasPlayed else { return }
                // The paywall or the gift is still closing: wait until nothing is on screen.
                for _ in 0..<8 {
                    try? await Task.sleep(nanoseconds: 800_000_000)
                    if Task.isCancelled { return }
                    if !GiftOfferPresenter.somethingIsPresented { break }
                }
                guard !GiftOfferPresenter.somethingIsPresented else { return }
                ProWelcomeState.wasPlayed = true
                shown = true
            }
    }
}

private struct ProUnlockedItem: Identifiable {
    let id: Int
    let symbol: String
    let title: String
}

/// "Welcome to Atala Pro": the crown lands, then everything that just unlocked ticks in, one by one.
struct ProWelcomeView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 0 nothing, 1 the crown, 2 the title, then one stage per item, then the button.
    @State private var stage = 0

    private var items: [ProUnlockedItem] {
        let weeks = store.plan?.weeks.count ?? PlanEngine.maxWeeks
        return [
            ProUnlockedItem(id: 0, symbol: "calendar", title: "All \(OnboardingCopy.weeks(weeks)) of your plan"),
            ProUnlockedItem(id: 1, symbol: "waveform", title: "\(store.coach.name)'s voice on every run"),
            ProUnlockedItem(id: 2, symbol: "bubble.left.and.bubble.right.fill", title: "Your coach, without limits"),
            ProUnlockedItem(id: 3, symbol: "figure.cooldown", title: "Guided strength and stretching"),
            ProUnlockedItem(id: 4, symbol: "chart.line.uptrend.xyaxis", title: "Every detail of your runs"),
        ]
    }

    var body: some View {
        let list = items
        return ZStack {
            LinearGradient(colors: [Theme.green, Theme.forest], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                Spacer(minLength: 24)
                crown
                VStack(spacing: 6) {
                    Text("Welcome to Atala Pro,")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(Theme.greenOnDark)
                    Text(store.firstName)
                        .font(.system(size: 38, weight: .bold))
                        .foregroundStyle(Color.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }
                .padding(.top, 18)
                .opacity(stage >= 2 ? 1 : 0)
                .offset(y: stage >= 2 ? 0 : 14)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isHeader)
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(list) { item in
                        row(item, unlocked: stage >= item.id + 3)
                    }
                }
                .padding(.horizontal, 28)
                .padding(.top, 28)
                Spacer(minLength: 24)
                Button {
                    Haptics.confirm()
                    dismiss()
                } label: {
                    Text("Let's run")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Theme.forest)
                        .frame(maxWidth: .infinity)
                        .frame(height: 58)
                        .background(Color.white, in: Capsule())
                }
                .buttonStyle(.plain)
                .padding(.horizontal, Theme.screenPadding)
                .padding(.bottom, 12)
                .opacity(stage >= list.count + 3 ? 1 : 0)
            }
        }
        .task {
            await play(count: list.count)
        }
    }

    private var crown: some View {
        ZStack {
            ForEach(0..<12, id: \.self) { index in
                Capsule()
                    .fill(Theme.yellow.opacity(0.85))
                    .frame(width: 5, height: stage >= 1 ? 24 : 4)
                    .offset(y: stage >= 1 ? -98 : -56)
                    .rotationEffect(.degrees(Double(index) * 30))
                    .opacity(stage >= 1 ? 1 : 0)
            }
            Circle()
                .fill(Theme.yellow)
                .frame(width: 132, height: 132)
                .shadow(color: .black.opacity(0.25), radius: 16, x: 0, y: 10)
            Image(systemName: "crown.fill")
                .font(.system(size: 60, weight: .bold))
                .foregroundStyle(Theme.forest)
        }
        .frame(height: 230)
        .scaleEffect(stage >= 1 ? 1 : 0.3)
        .opacity(stage >= 1 ? 1 : 0)
        .accessibilityHidden(true)
    }

    /// A locked line that turns into a ticked one when its turn comes.
    private func row(_ item: ProUnlockedItem, unlocked: Bool) -> some View {
        HStack(spacing: 14) {
            Circle()
                .fill(unlocked ? Theme.yellow : Color.white.opacity(0.14))
                .frame(width: 40, height: 40)
                .overlay(
                    Image(systemName: unlocked ? item.symbol : "lock.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(unlocked ? Theme.forest : Color.white.opacity(0.5))
                )
                .scaleEffect(unlocked ? 1 : 0.86)
            Text(item.title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(unlocked ? Color.white : Color.white.opacity(0.45))
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
            Image(systemName: "checkmark")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.yellow)
                .opacity(unlocked ? 1 : 0)
        }
        .accessibilityElement(children: .combine)
    }

    private static func wait(_ seconds: Double) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    @MainActor
    private func play(count: Int) async {
        guard stage == 0 else { return }
        if reduceMotion {
            stage = count + 3
            return
        }
        await Self.wait(0.25)
        Haptics.success()
        withAnimation(.spring(response: 0.5, dampingFraction: 0.5)) { stage = 1 }
        await Self.wait(0.55)
        withAnimation(.easeOut(duration: 0.4)) { stage = 2 }
        await Self.wait(0.5)
        for index in 0..<count {
            Haptics.soft()
            withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) { stage = index + 3 }
            await Self.wait(0.38)
        }
        withAnimation(.easeOut(duration: 0.4)) { stage = count + 3 }
    }
}

#Preview("Welcome") {
    ProWelcomeView()
        .environmentObject(AppStore.preview)
}

#Preview("Journey") {
    ProJourneyCard()
        .environmentObject(AppStore.preview)
        .padding()
}
