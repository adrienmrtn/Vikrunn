import SwiftUI

/// Screen 22: where the plan takes the user, computed by `PlanEngine` from the real answers.
struct OnboardingProjectionScreen: View {
    @EnvironmentObject private var store: AppStore
    /// 1 hero card, 2 the big number counts, 3 verdict, 4-6 stat tiles, 7 coach note, 8 button.
    @State private var stage = 0

    /// The verdict waits for the count (0.9 s) to be nearly over; the tiles follow without a gap.
    private static let stageDelays: [Double] = [0.3, 0.35, 0.85, 0.3, 0.14, 0.14, 0.4, 0.4]
    private static let counting = Animation.easeInOut(duration: 0.9)

    var body: some View {
        let plan = PlanEngine.generate(answers: store.answers)
        OnboardingScaffold(title: title(plan), buttonsVisible: stage >= Self.stageDelays.count) {
            heroCard(plan)
                .onboardingStaged(0, stage: stage)
            stats(plan)
            CoachNote(coach: store.coach, text: note(plan))
                .onboardingStaged(6, stage: stage)
        }
        .onboardingStageSequence($stage, delays: Self.stageDelays)
    }

    // MARK: Wording

    private func gainSeconds(_ plan: TrainingPlan) -> Int {
        guard let today = plan.currentSeconds, let goal = plan.predictedSeconds else { return 0 }
        return max(0, today - goal)
    }

    private func title(_ plan: TrainingPlan) -> String {
        let weeks = OnboardingCopy.weeks(plan.weeks.count)
        if plan.goal.isOpenEnded {
            return L("%@ to make running a habit", weeks)
        }
        let gain = gainSeconds(plan)
        if gain >= 60 {
            let minutes = gain / 60
            return minutes == 1 ? L("You can gain %@ minute in %@", minutes, weeks) : L("You can gain %@ minutes in %@", minutes, weeks)
        }
        if gain > 0 {
            return L("You can gain %@ seconds in %@", gain, weeks)
        }
        return L("%@ to be ready for your %@", weeks, OnboardingCopy.goalNoun(plan.goal))
    }

    private func endLabel(_ plan: TrainingPlan) -> String {
        if let race = plan.raceDate {
            return L("On %@", Fmt.day(race, "d MMMM"))
        }
        return L("In %@", OnboardingCopy.weeks(plan.weeks.count))
    }

    private func verdict(_ plan: TrainingPlan) -> String {
        if plan.goal.isOpenEnded {
            return L("A steady build-up, never more than you can absorb")
        }
        if let target = plan.targetSeconds, let aimed = plan.predictedSeconds, aimed > target {
            return L("Ambitious: I aim for %@ first", Fmt.goalTime(aimed))
        }
        return L("Realistic goal for your level")
    }

    private func note(_ plan: TrainingPlan) -> String {
        if let result = store.answers.recentResult {
            return L("I start from your %@ in %@ and build up gently, with strength and stretching every week.", OnboardingCopy.distanceName(result.distanceKm), Fmt.goalTime(result.seconds))
        }
        return L("I start from what you told me about your running and build up gently, with strength and stretching every week.")
    }

    // MARK: Pieces

    private func heroCard(_ plan: TrainingPlan) -> some View {
        let firstKm = plan.weeks.first?.plannedKm ?? 0
        let usesTimes = !plan.goal.isOpenEnded && plan.currentSeconds != nil
        // The big number starts from today's value and travels to where the plan leads.
        let startValue: Double = usesTimes ? Double(plan.currentSeconds ?? 0) : firstKm
        let endValue: Double = usesTimes
            ? Double(plan.predictedSeconds ?? plan.currentSeconds ?? 0)
            : plan.peakKm
        let format: (Int) -> String
        if usesTimes {
            format = { seconds in Fmt.goalTime(seconds) }
        } else {
            format = { km in Fmt.km(Double(km), decimals: 0) }
        }
        let counted = stage >= 2
        return VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(usesTimes ? L("Today") : L("First week"))
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.greenOnDark)
                    Text(format(Int(startValue.rounded())))
                        .font(.system(size: 26, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(Theme.greenOnDark)
                }
                Spacer(minLength: 8)
                Image(systemName: "arrow.right")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Theme.greenOnDark)
                    .padding(.bottom, 8)
                    .opacity(counted ? 1 : 0)
                    .offset(x: counted ? 0 : -14)
                    .animation(.easeOut(duration: 0.6), value: counted)
                    .accessibilityHidden(true)
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(usesTimes ? endLabel(plan) : L("Biggest week"))
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.greenOnDark)
                    OnboardingCountingValue(value: counted ? endValue : startValue, format: format)
                        .font(.system(size: 42, weight: .bold))
                        .monospacedDigit()
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                        .foregroundStyle(Color.white)
                        .animation(Self.counting, value: counted)
                }
            }
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 16))
                    .accessibilityHidden(true)
                Text(verdict(plan))
                    .font(.system(size: 14, weight: .semibold))
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .foregroundStyle(Color.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .onboardingStaged(2, stage: stage)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 22)
        .background(Theme.green, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    private func stats(_ plan: TrainingPlan) -> some View {
        let sessions = OnboardingCopy.sessionsPerWeek(store.answers)
        return HStack(spacing: 10) {
            statTile(index: 3, symbol: "calendar", target: Double(plan.weeks.count),
                     format: { "\($0)" },
                     label: plan.weeks.count == 1 ? L("week of plan") : L("weeks of plan"))
            statTile(index: 4, symbol: "figure.run", target: Double(sessions),
                     format: { "\($0)" },
                     label: sessions == 1 ? L("session a week") : L("sessions a week"))
            statTile(index: 5, symbol: "chart.bar.fill", target: plan.peakKm,
                     format: { Fmt.km(Double($0), decimals: 0) },
                     label: L("the biggest week"))
        }
    }

    /// The tile rises in at its stage and its number counts up from zero.
    private func statTile(index: Int, symbol: String, target: Double,
                          format: @escaping (Int) -> String, label: String) -> some View {
        let shown = stage > index
        return VStack(alignment: .leading, spacing: 8) {
            Image(systemName: symbol)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Theme.green)
                .frame(height: 22)
                .accessibilityHidden(true)
            OnboardingCountingValue(value: shown ? target : 0, format: format)
                .font(.system(size: 20, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .animation(.easeOut(duration: 0.7), value: shown)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, minHeight: 112, alignment: .topLeading)
        .card()
        .accessibilityElement(children: .combine)
        .onboardingStaged(index, stage: stage)
    }
}

struct OnboardingReview: Identifiable {
    let id: Int
    let text: String
    let author: String
    /// Photo of the author in the asset catalog, shown with their agreement.
    let photo: String
}

// Reviews supplied by the team. They must come from real users: keep the wording they gave.
enum OnboardingSampleReviews {
    static var reviews: [OnboardingReview] { [
        OnboardingReview(id: 1,
                         text: L("I finally ran 10 km under 45 minutes with Atala. It helped so much."),
                         author: "Marie", photo: AtalaPhotos.woman),
        OnboardingReview(id: 2,
                         text: L("Atala helped me lose 10 kg in just a few months. I strongly recommend it."),
                         author: "Thomas", photo: AtalaPhotos.man),
        OnboardingReview(id: 3,
                         text: L("A friend recommended Atala. I didn't know you could have a coach this good at this price."),
                         author: "Luis", photo: AtalaPhotos.trio),
    ] }
}

/// Screen 23: what runners say.
struct OnboardingReviewsScreen: View {
    /// 1 photos and stars, then one stage per review, then the button.
    @State private var stage = 0

    private static var delays: [Double] {
        [0.15] + Array(repeating: 0.3, count: OnboardingSampleReviews.reviews.count) + [0.35]
    }

    var body: some View {
        OnboardingScaffold(title: L("Runners love their Atala coach"),
                           buttonsVisible: stage >= Self.delays.count) {
            ratingHeader
                .onboardingStaged(0, stage: stage)
            VStack(spacing: 12) {
                ForEach(Array(OnboardingSampleReviews.reviews.enumerated()), id: \.element.id) { index, review in
                    reviewCard(review)
                        .onboardingStaged(index + 1, stage: stage)
                }
            }
        }
        .onboardingStageSequence($stage, delays: Self.delays)
    }

    private func stars(size: CGFloat) -> some View {
        HStack(spacing: 3) {
            ForEach(0..<5, id: \.self) { _ in
                Image(systemName: "star.fill")
                    .font(.system(size: size))
                    .foregroundStyle(Theme.yellow)
            }
        }
        .accessibilityHidden(true)
    }

    /// Runners in motion above the reviews, five stars underneath.
    private var ratingHeader: some View {
        VStack(spacing: 14) {
            OnboardingPhotoStrip(names: AtalaPhotos.strip(for: nil), tileWidth: 104, height: 132, speed: 16)
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            stars(size: 20)
        }
        .frame(maxWidth: .infinity)
    }

    private func reviewCard(_ review: OnboardingReview) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            stars(size: 13)
            Text(review.text)
                .font(.system(size: 16))
                .foregroundStyle(Theme.ink)
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                Circle()
                    .fill(Theme.greenTint)
                    .frame(width: 44, height: 44)
                    .overlay(alignment: .top) {
                        Image(review.photo)
                            .resizable()
                            .scaledToFill()
                    }
                    .clipShape(Circle())
                    .accessibilityHidden(true)
                Text(review.author)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.body)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 22)
        .accessibilityElement(children: .combine)
    }
}

#Preview("Projection") {
    OnboardingProjectionScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Reviews") {
    OnboardingReviewsScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
