import SwiftUI

/// Screen 22: where the plan takes the user, computed by `PlanEngine` from the real answers.
struct OnboardingProjectionScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        let plan = PlanEngine.generate(answers: store.answers)
        OnboardingScaffold(title: title(plan)) {
            heroCard(plan)
            stats(plan)
            CoachNote(coach: store.coach, text: note(plan))
        }
    }

    // MARK: Wording

    private func gainSeconds(_ plan: TrainingPlan) -> Int {
        guard let today = plan.currentSeconds, let goal = plan.predictedSeconds else { return 0 }
        return max(0, today - goal)
    }

    private func title(_ plan: TrainingPlan) -> String {
        let weeks = OnboardingCopy.weeks(plan.weeks.count)
        if plan.goal == .regular {
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
        if plan.goal == .regular {
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
        let usesTimes = plan.goal != .regular && plan.currentSeconds != nil
        let todayValue = usesTimes ? Fmt.goalTime(plan.currentSeconds ?? 0) : Fmt.km(firstKm, decimals: 0)
        let endValue = usesTimes
            ? Fmt.goalTime(plan.predictedSeconds ?? plan.currentSeconds ?? 0)
            : Fmt.km(plan.peakKm, decimals: 0)
        return VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(usesTimes ? "Today" : "First week")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.greenOnDark)
                    Text(todayValue)
                        .font(.system(size: 26, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(Theme.greenOnDark)
                }
                Spacer(minLength: 8)
                Image(systemName: "arrow.right")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Theme.greenOnDark)
                    .padding(.bottom, 8)
                    .accessibilityHidden(true)
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(usesTimes ? endLabel(plan) : "Biggest week")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.greenOnDark)
                    Text(endValue)
                        .font(.system(size: 42, weight: .bold))
                        .monospacedDigit()
                        .minimumScaleFactor(0.6)
                        .lineLimit(1)
                        .foregroundStyle(Color.white)
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
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 22)
        .background(Theme.green, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    private func stats(_ plan: TrainingPlan) -> some View {
        let sessions = OnboardingCopy.sessionsPerWeek(store.answers)
        return HStack(spacing: 10) {
            statTile(symbol: "calendar", value: "\(plan.weeks.count)",
                     label: plan.weeks.count == 1 ? "week of plan" : "weeks of plan")
            statTile(symbol: "figure.run", value: "\(sessions)",
                     label: sessions == 1 ? "session a week" : "sessions a week")
            statTile(symbol: "chart.bar.fill", value: Fmt.km(plan.peakKm, decimals: 0),
                     label: "the biggest week")
        }
    }

    private func statTile(symbol: String, value: String, label: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: symbol)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Theme.green)
                .frame(height: 22)
                .accessibilityHidden(true)
            Text(value)
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
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

/// Screen 23: what runners say (placeholders for now).
struct OnboardingReviewsScreen: View {
    var body: some View {
        OnboardingScaffold(title: "What runners say") {
            ratingCard
            VStack(spacing: 10) {
                ForEach(OnboardingSampleReviews.reviews) { review in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(review.text)
                            .font(.system(size: 15))
                            .foregroundStyle(Theme.ink)
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(review.author)
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.muted)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .card()
                }
            }
        }
    }

    private var ratingCard: some View {
        HStack(spacing: 16) {
            Text(OnboardingSampleReviews.rating)
                .font(.system(size: 40, weight: .bold))
                .foregroundStyle(Theme.ink)
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 3) {
                    ForEach(0..<5, id: \.self) { _ in
                        Image(systemName: "star.fill")
                            .font(.system(size: 15))
                            .foregroundStyle(Theme.yellow)
                    }
                }
                .accessibilityHidden(true)
                Text(OnboardingSampleReviews.ratingCaption)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 18)
        .card(radius: 24)
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
