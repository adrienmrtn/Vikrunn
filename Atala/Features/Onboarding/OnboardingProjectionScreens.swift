import SwiftUI

/// Screen 22: where the plan takes the user, computed by `PlanEngine` from the real answers.
struct OnboardingProjectionScreen: View {
    @EnvironmentObject private var store: AppStore
    /// 1 hero card and empty chart, 2 times count while the curve draws, 3 verdict, 4-6 stat tiles,
    /// 7 coach note, 8 button.
    @State private var stage = 0

    private static let stageDelays: [Double] = [0.35, 0.45, 1.45, 0.35, 0.16, 0.16, 0.45, 0.5]
    private static let counting = Animation.easeInOut(duration: 1.1)

    var body: some View {
        let plan = PlanEngine.generate(answers: store.answers)
        OnboardingScaffold(title: title(plan), buttonsVisible: stage >= Self.stageDelays.count) {
            heroCard(plan)
                .onboardingStaged(0, stage: stage)
            chart(plan)
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
            return "\(weeks) to make running a habit"
        }
        let gain = gainSeconds(plan)
        if gain >= 60 {
            let minutes = gain / 60
            return "You can gain \(minutes) \(minutes == 1 ? "minute" : "minutes") in \(weeks)"
        }
        if gain > 0 {
            return "You can gain \(gain) seconds in \(weeks)"
        }
        return "\(weeks) to be ready for your \(OnboardingCopy.goalNoun(plan.goal))"
    }

    private func endLabel(_ plan: TrainingPlan) -> String {
        if let race = plan.raceDate {
            return "On \(Fmt.day(race, "d MMMM"))"
        }
        return "In \(OnboardingCopy.weeks(plan.weeks.count))"
    }

    private func verdict(_ plan: TrainingPlan) -> String {
        if plan.goal.isOpenEnded {
            return "A steady build-up, never more than you can absorb"
        }
        if let target = plan.targetSeconds, let aimed = plan.predictedSeconds, aimed > target {
            return "Ambitious: I aim for \(Fmt.goalTime(aimed)) first"
        }
        return "Realistic goal for your level"
    }

    private func note(_ plan: TrainingPlan) -> String {
        let start: String
        if let result = store.answers.recentResult {
            start = "I start from your \(OnboardingCopy.distanceName(result.distanceKm)) in \(Fmt.goalTime(result.seconds))"
        } else {
            start = "I start from what you told me about your running"
        }
        return "\(start) and build up gently, with strength and stretching every week."
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
                    Text(usesTimes ? "Today" : "First week")
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
                    Text(usesTimes ? endLabel(plan) : "Biggest week")
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

    /// The curve from today's level to the goal: it draws itself while the big number counts.
    private func chart(_ plan: TrainingPlan) -> some View {
        let usesTimes = !plan.goal.isOpenEnded && plan.currentSeconds != nil
        let startValue: String
        let goalValue: String
        if usesTimes {
            let today = plan.currentSeconds ?? 0
            startValue = Fmt.goalTime(today)
            goalValue = Fmt.goalTime(plan.predictedSeconds ?? today)
        } else {
            startValue = Fmt.km(plan.weeks.first?.plannedKm ?? 0, decimals: 0)
            goalValue = Fmt.km(plan.peakKm, decimals: 0)
        }
        return OnboardingProjectionChart(weekCount: plan.weeks.count,
                                         startCaption: usesTimes ? "Today" : "First week",
                                         startValue: startValue,
                                         goalCaption: usesTimes ? "Goal" : "Biggest week",
                                         goalValue: goalValue,
                                         drawn: stage >= 2)
    }

    private func stats(_ plan: TrainingPlan) -> some View {
        let sessions = OnboardingCopy.sessionsPerWeek(store.answers)
        return HStack(spacing: 10) {
            statTile(index: 3, symbol: "calendar", target: Double(plan.weeks.count),
                     format: { "\($0)" },
                     label: plan.weeks.count == 1 ? "week of plan" : "weeks of plan")
            statTile(index: 4, symbol: "figure.run", target: Double(sessions),
                     format: { "\($0)" },
                     label: sessions == 1 ? "session a week" : "sessions a week")
            statTile(index: 5, symbol: "chart.bar.fill", target: plan.peakKm,
                     format: { Fmt.km(Double($0), decimals: 0) },
                     label: "the biggest week")
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
}

// SAMPLE REVIEWS — replace with real ones before release
enum OnboardingSampleReviews {
    static let rating = "4.8"
    static let ratingCaption = "Sample rating, not a real App Store score"
    static let reviews: [OnboardingReview] = [
        OnboardingReview(id: 1,
                         text: "Sample review. A runner explains in two or three lines what the plan changed for them.",
                         author: "Sample Runner A · placeholder"),
        OnboardingReview(id: 2,
                         text: "Sample review. A runner tells how the coach helped during a difficult week.",
                         author: "Sample Runner B · placeholder"),
        OnboardingReview(id: 3,
                         text: "Sample review. A runner says what they liked about the coach's explanations.",
                         author: "Sample Runner C · placeholder"),
    ]
}

/// Screen 23: what runners say (sample content until real reviews are collected).
struct OnboardingReviewsScreen: View {
    /// 1 rating, then one stage per review, then the button.
    @State private var stage = 0

    private static var delays: [Double] {
        [0.15] + Array(repeating: 0.3, count: OnboardingSampleReviews.reviews.count) + [0.35]
    }

    var body: some View {
        OnboardingScaffold(title: "Runners love their Atala coach",
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

    private var ratingHeader: some View {
        VStack(spacing: 6) {
            HStack(alignment: .center, spacing: 10) {
                Image(systemName: "laurel.leading")
                    .font(.system(size: 40, weight: .regular))
                    .foregroundStyle(Theme.yellow)
                    .accessibilityHidden(true)
                Text(OnboardingSampleReviews.rating)
                    .font(.system(size: 52, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Image(systemName: "laurel.trailing")
                    .font(.system(size: 40, weight: .regular))
                    .foregroundStyle(Theme.yellow)
                    .accessibilityHidden(true)
            }
            stars(size: 18)
            Text(OnboardingSampleReviews.ratingCaption)
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 22)
        .padding(.horizontal, 16)
        .background(Theme.yellowTint, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private func initials(_ author: String) -> String {
        let letters = author.split(separator: " ").prefix(2).compactMap { $0.first }
        return String(letters).uppercased()
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
                    .frame(width: 32, height: 32)
                    .overlay(
                        Text(initials(review.author))
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Theme.green)
                    )
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
