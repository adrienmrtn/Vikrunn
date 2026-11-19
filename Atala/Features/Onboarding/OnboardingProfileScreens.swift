import SwiftUI

/// Screen 5: gender.
struct OnboardingGenderScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        OnboardingScaffold(title: "You are…",
                           canContinue: store.answers.gender != nil) {
            VStack(spacing: 10) {
                ForEach(Gender.allCases) { gender in
                    OnboardingPlainOptionRow(title: gender.title, selected: store.answers.gender == gender) {
                        store.answers.gender = gender
                    }
                }
            }
        }
    }
}

/// Screen 6: how long have you been running.
struct OnboardingExperienceScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        OnboardingScaffold(title: "How long have you been running?",
                           canContinue: store.answers.experience != nil) {
            VStack(spacing: 9) {
                ForEach(Experience.allCases) { experience in
                    OptionCard(title: experience.title,
                               selected: store.answers.experience == experience,
                               action: { store.answers.experience = experience }) {
                        VignetteImage(name: experience.vignette)
                    }
                }
            }
        }
    }
}

/// Screen 7: current weekly volume.
struct OnboardingVolumeScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        OnboardingScaffold(title: "How much do you run in a week?",
                           canContinue: store.answers.weeklyVolume != nil) {
            VStack(spacing: 9) {
                ForEach(WeeklyVolume.allCases) { volume in
                    OptionCard(title: volume.title,
                               selected: store.answers.weeklyVolume == volume,
                               action: {
                                   store.answers.weeklyVolume = volume
                                   // The recent-result question is skipped for someone who does not run yet.
                                   if volume == .notYet { store.answers.recentResult = nil }
                               }) {
                        VignetteImage(name: volume.vignette)
                    }
                }
            }
        }
    }
}

/// One health flag stored in `OnboardingAnswers.healthFlags`.
private struct OnboardingHealthOption: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let symbol: String
    let tint: Color
    let background: Color

    static let nothingID = "none"

    static let all: [OnboardingHealthOption] = [
        OnboardingHealthOption(id: nothingID, title: "Nothing to report", subtitle: "I feel fine to start",
                               symbol: "checkmark", tint: Theme.green, background: Theme.greenTint),
        OnboardingHealthOption(id: "pain", title: "A pain or an injury right now",
                               subtitle: "See a professional first, we start gently",
                               symbol: "bandage.fill", tint: Theme.orangeDeep, background: Theme.orangeTint),
        OnboardingHealthOption(id: "heart", title: "A heart or breathing condition",
                               subtitle: "Check with your doctor before starting",
                               symbol: "heart.fill", tint: Theme.orangeDeep, background: Theme.orangeTint),
        OnboardingHealthOption(id: "break", title: "Back after a long break",
                               subtitle: "We rebuild step by step",
                               symbol: "arrow.counterclockwise", tint: Theme.green, background: Theme.greenTint),
    ]
}

/// Screen 9: health. The coach never diagnoses, it only starts more gently.
struct OnboardingHealthScreen: View {
    @EnvironmentObject private var store: AppStore

    private func toggle(_ id: String) {
        var flags = store.answers.healthFlags
        if id == OnboardingHealthOption.nothingID {
            flags = flags.contains(id) ? [] : [id]
        } else {
            flags.removeAll { $0 == OnboardingHealthOption.nothingID }
            if flags.contains(id) {
                flags.removeAll { $0 == id }
            } else {
                flags.append(id)
            }
        }
        store.answers.healthFlags = flags
    }

    var body: some View {
        OnboardingScaffold(title: "Anything about your health I should know?",
                           canContinue: !store.answers.healthFlags.isEmpty) {
            VStack(spacing: 9) {
                ForEach(OnboardingHealthOption.all) { option in
                    OptionCard(title: option.title,
                               subtitle: option.subtitle,
                               selected: store.answers.healthFlags.contains(option.id),
                               multi: true,
                               action: { toggle(option.id) }) {
                        IconTile(systemName: option.symbol, tint: option.tint, background: option.background)
                    }
                }
            }
        }
    }
}

/// Screen 10: "Here's what I see", built from the answers so far.
struct OnboardingMirrorScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @State private var stage = 0

    private var vma: Double { PlanEngine.estimateVMA(store.answers) }

    private var reference: GoalType {
        (store.answers.experience ?? .starting) == .starting ? .fiveK : .tenK
    }

    private var source: String {
        if let result = store.answers.recentResult {
            return "Based on your \(OnboardingCopy.distanceName(result.distanceKm)) in \(Fmt.goalTime(result.seconds))"
        }
        return "Based on your answers"
    }

    private var level: String {
        switch store.answers.experience ?? .starting {
        case .starting: return "Beginner"
        case .underSixMonths: return "Getting started"
        case .sixMonthsToTwoYears: return "Intermediate"
        case .overTwoYears: return "Experienced"
        }
    }

    private var usualVolume: String {
        let volume = store.answers.weeklyVolume ?? .under10
        return volume == .notYet ? "Not running yet" : "\(volume.title) a week"
    }

    private var easyPace: String {
        "\(Fmt.pace(PlanEngine.paceFor(vma: vma, fraction: 0.65))) /km"
    }

    /// Header, hero card, the two tiles, the volume line, then the buttons.
    private static let stageDelays: [Double] = [0.3, 0.35, 0.35, 0.25, 0.4]

    var body: some View {
        OnboardingScaffold(buttonTitle: "That's me",
                           buttonsVisible: stage >= Self.stageDelays.count,
                           secondaryTitle: "Change something",
                           onSecondary: { nav.go(to: .experience, forward: false, store: store) }) {
            HStack(spacing: 12) {
                CoachAvatar(coach: store.coach, size: 52)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Here's what I see")
                        .font(Theme.title(26))
                        .foregroundStyle(Theme.ink)
                        .accessibilityAddTraits(.isHeader)
                    Text(source)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.green)
                }
            }
            .onboardingStaged(0, stage: stage)
            estimateCard
                .onboardingStaged(1, stage: stage)
            HStack(spacing: 12) {
                tile(symbol: "bolt.fill", tint: Theme.orangeDeep, background: Theme.orangeTint,
                     value: String(format: "%.1f km/h", vma), label: "Aerobic speed (VMA)")
                tile(symbol: "figure.run", tint: Theme.green, background: Theme.greenTint,
                     value: easyPace, label: "Easy pace")
            }
            .onboardingStaged(2, stage: stage)
            HStack(spacing: 12) {
                IconTile(systemName: "calendar", tint: Theme.yellowDeep, background: Theme.yellowTint, size: 40)
                Text("Usual volume")
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.muted)
                Spacer(minLength: 8)
                Text(usualVolume)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .padding(14)
            .frame(maxWidth: .infinity)
            .card(radius: 20)
            .accessibilityElement(children: .combine)
            .onboardingStaged(3, stage: stage)
        }
        .onboardingStageSequence($stage, delays: Self.stageDelays)
    }

    /// The headline figure, white on green, with the level as a chip.
    private var estimateCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                Text("YOUR \(reference.title.uppercased()) TODAY")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(0.8)
                    .foregroundStyle(Theme.greenOnDark)
                Spacer(minLength: 8)
                Text(level)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.forest)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.white, in: Capsule())
            }
            OnboardingCountingValue(value: stage > 1 ? Double(PlanEngine.predictedSeconds(vma: vma, goal: reference) ?? 0) : 0,
                                    format: { Fmt.goalTime($0) })
                .font(.system(size: 60, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Color.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .animation(.easeOut(duration: 0.9), value: stage)
            Text("Your starting point. Your goal comes next.")
                .font(.system(size: 15))
                .foregroundStyle(Theme.greenOnDark)
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [Theme.green, Theme.forest], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
        .shadow(color: Theme.green.opacity(0.25), radius: 16, x: 0, y: 10)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Your \(reference.title) today: \(Fmt.goalTime(PlanEngine.predictedSeconds(vma: vma, goal: reference) ?? 0)). Level: \(level).")
    }

    private func tile(symbol: String, tint: Color, background: Color, value: String, label: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            IconTile(systemName: symbol, tint: tint, background: background, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(value)
                    .font(.system(size: 22, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(label)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 22)
        .accessibilityElement(children: .combine)
    }
}

#Preview("Experience") {
    OnboardingExperienceScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Mirror") {
    OnboardingMirrorScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
