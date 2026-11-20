import SwiftUI
import UIKit

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
        .accessibilityLabel(L("Atala Pro member"))
    }
}

/// One thing Atala Pro adds, as shown on the card for people without it.
private struct ProUpsellFeature: Identifiable {
    let id: Int
    let symbol: String
    let title: String
}

/// For people without Atala Pro: a compact card with what Pro adds and one button to the paywall.
/// Hidden for subscribers. It sits below the session of the day, never above it.
struct ProUpsellCard: View {
    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var showPaywall = false

    private var features: [ProUpsellFeature] {
        [
            ProUpsellFeature(id: 0, symbol: "bubble.left.and.bubble.right.fill", title: L("Talk to your coach any time")),
            ProUpsellFeature(id: 1, symbol: "waveform", title: L("%@'s voice in your ears", store.coach.name)),
            ProUpsellFeature(id: 2, symbol: "chart.line.uptrend.xyaxis", title: L("Your progress and predictions")),
            ProUpsellFeature(id: 3, symbol: "list.bullet.rectangle", title: L("Every detail of your runs")),
        ]
    }

    var body: some View {
        if !purchases.isPro, store.plan != nil {
            Button {
                Haptics.tap()
                showPaywall = true
            } label: {
                content
            }
            .buttonStyle(.plain)
            .accessibilityHint(L("Shows what Atala Pro includes"))
            .proPaywall(isPresented: $showPaywall)
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center, spacing: 10) {
                Circle()
                    .fill(Theme.yellow)
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: "crown.fill")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(Theme.forest)
                    )
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Atala Pro")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Color.white)
                    Text(L("Go further with your coach"))
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.greenOnDark)
                        .lineLimit(2)
                        .minimumScaleFactor(0.85)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 6)
                Text(L("Discover"))
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.forest)
                    .lineLimit(1)
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .background(Color.white, in: Capsule())
                    .fixedSize()
            }
            VStack(alignment: .leading, spacing: 8) {
                ForEach(features) { feature in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Image(systemName: feature.symbol)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Theme.yellow)
                            .frame(width: 20)
                            .accessibilityHidden(true)
                        Text(feature.title)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Color.white)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [Theme.green, Theme.forest], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
        )
        .contentShape(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
        .accessibilityElement(children: .combine)
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
    /// When the main screen appeared: the welcome never shows over the launch splash.
    @State private var mountedAt = Date()

    func body(content: Content) -> some View {
        content
            .fullScreenCover(isPresented: $shown) {
                ProWelcomeView()
                    .environmentObject(store)
            }
            .task(id: purchases.isPro) {
                // The flag is reset by PurchaseService once the store confirms there is no
                // subscription: `isPro` is also false while the store has not answered yet.
                guard purchases.isPro, !ProWelcomeState.wasPlayed else { return }
                let early = GiftOfferSchedule.delay - Date().timeIntervalSince(mountedAt)
                if early > 0 {
                    try? await Task.sleep(nanoseconds: UInt64(early * 1_000_000_000))
                    if Task.isCancelled { return }
                }
                // Never over the App Store rating request, made a moment after arriving.
                await ReviewPromptState.waitBeforeOffer()
                if Task.isCancelled { return }
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
        [
            ProUnlockedItem(id: 0, symbol: "bubble.left.and.bubble.right.fill", title: L("Your coach, any time")),
            ProUnlockedItem(id: 1, symbol: "waveform", title: L("%@'s voice on every run", store.coach.name)),
            ProUnlockedItem(id: 2, symbol: "chart.line.uptrend.xyaxis", title: L("Your progress and predictions")),
            ProUnlockedItem(id: 3, symbol: "list.bullet.rectangle", title: L("Every detail of your runs")),
        ]
    }

    var body: some View {
        let list = items
        return ZStack {
            LinearGradient(colors: [Theme.green, Theme.forest], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                Spacer(minLength: 24)
                // Shrinks on small phones so the list and the button always fit.
                ShrinkToFit(height: 230, minHeight: 130) {
                    crown
                }
                .layoutPriority(1)
                VStack(spacing: 6) {
                    Text(L("Welcome to Atala Pro,"))
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
                    Text(L("Let's run"))
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
                .lineLimit(2)
                .minimumScaleFactor(0.8)
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

#Preview("Pro card") {
    ProUpsellCard()
        .environmentObject(AppStore.preview)
        .padding()
}
