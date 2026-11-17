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
        let usesTimes = plan.goal != .regular && plan.currentSeconds != nil
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

/// One thing the runner can count on, shown on the screen that used to hold sample reviews.
struct OnboardingPromise: Identifiable {
    let id: Int
    let symbol: String
    let title: String
    let text: String
}

/// Facts about how Atala works, in place of user reviews: the app has none to quote yet.
/// When real reviews exist, this is the screen to put them on.
enum OnboardingPromises {
    static let all: [OnboardingPromise] = [
        OnboardingPromise(id: 1, symbol: "calendar",
                          title: "A plan you can hold",
                          text: "Eight weeks at most, one session per day you ticked, strength and stretching included."),
        OnboardingPromise(id: 2, symbol: "arrow.uturn.backward",
                          title: "A missed session breaks nothing",
                          text: "It is marked as missed and the rest stays in place. You can still count it afterwards."),
        OnboardingPromise(id: 3, symbol: "hand.raised.fill",
                          title: "Nothing moves without your yes",
                          text: "Your coach proposes a change, you apply it or keep your plan."),
        OnboardingPromise(id: 4, symbol: "cross.case.fill",
                          title: "A coach, not a doctor",
                          text: "No diagnosis, ever. If something hurts, the advice is to see a health professional."),
    ]
}

/// Screen 23: what the runner can count on (no reviews are shown until there are real ones).
struct OnboardingReviewsScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        OnboardingScaffold(title: "What you can count on") {
            coachCard
            VStack(spacing: 10) {
                ForEach(OnboardingPromises.all) { promise in
                    HStack(alignment: .top, spacing: 14) {
                        IconTile(systemName: promise.symbol)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(promise.title)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(Theme.ink)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(promise.text)
                                .font(.system(size: 14))
                                .foregroundStyle(Theme.muted)
                                .lineSpacing(3)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .card()
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }

    private var coachCard: some View {
        HStack(spacing: 16) {
            CoachAvatar(coach: store.coach, size: 56)
            VStack(alignment: .leading, spacing: 4) {
                Text("\(store.coach.name)'s method")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Text("The same rules for every runner, whatever the coach you chose.")
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
