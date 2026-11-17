import SwiftUI

/// Screen 5: gender.
struct OnboardingGenderScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        OnboardingScaffold(title: "You are…",
                           subtitle: "It helps me estimate your paces and adapt your plan.",
                           canContinue: store.answers.gender != nil) {
            VStack(spacing: 10) {
                ForEach(Gender.allCases) { gender in
                    OnboardingPlainOptionRow(title: gender.title, selected: store.answers.gender == gender) {
                        store.answers.gender = gender
                    }
                }
            }
            Text("This stays on your profile and is never shown to anyone.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
        }
    }
}

/// Screen 6: how long have you been running.
struct OnboardingExperienceScreen: View {
    @EnvironmentObject private var store: AppStore

    private func subtitle(_ experience: Experience) -> String {
        switch experience {
        case .starting: return "Everything is new, we start from scratch"
        case .underSixMonths: return "The basics are settling in"
        case .sixMonthsToTwoYears: return "You run regularly"
        case .overTwoYears: return "You know the drill"
        }
    }

    var body: some View {
        OnboardingScaffold(title: "How long have you been running?",
                           canContinue: store.answers.experience != nil) {
            VStack(spacing: 9) {
                ForEach(Experience.allCases) { experience in
                    OptionCard(title: experience.title,
                               subtitle: subtitle(experience),
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

    private func subtitle(_ volume: WeeklyVolume) -> String {
        switch volume {
        case .under10: return "A run or two, or nothing yet"
        case .from10to25: return "Two or three runs most weeks"
        case .over25: return "Running is already a habit"
        }
    }

    var body: some View {
        OnboardingScaffold(title: "How much do you run in a week?",
                           subtitle: "Think of a normal week these days, not your best one.",
                           canContinue: store.answers.weeklyVolume != nil) {
            VStack(spacing: 9) {
                ForEach(WeeklyVolume.allCases) { volume in
                    OptionCard(title: volume.title,
                               subtitle: subtitle(volume),
                               selected: store.answers.weeklyVolume == volume,
                               action: { store.answers.weeklyVolume = volume }) {
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

    static let none = "none"

    static let all: [OnboardingHealthOption] = [
        OnboardingHealthOption(id: none, title: "Nothing to report", subtitle: "I feel fine to start",
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
        if id == OnboardingHealthOption.none {
            flags = flags.contains(id) ? [] : [id]
        } else {
            flags.removeAll { $0 == OnboardingHealthOption.none }
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
                           subtitle: "Tick what applies to you today.",
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
            Text("I'm a coach, not a doctor: I never diagnose. If something hurts, see a health professional and I'll lighten your plan.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .lineSpacing(3)
        }
    }
}

/// Screen 10: "Here's what I see", built from the answers so far.
struct OnboardingMirrorScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator

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

    private var rows: [(String, String)] {
        [
            ("Level", level),
            ("Aerobic speed (VMA)", String(format: "%.1f km/h", vma)),
            ("Usual volume", "\((store.answers.weeklyVolume ?? .under10).title) a week"),
            ("Easy pace", "\(Fmt.pace(PlanEngine.paceFor(vma: vma, fraction: 0.65))) /km"),
        ]
    }

    var body: some View {
        OnboardingScaffold(buttonTitle: "That's me",
                           secondaryTitle: "Change something",
                           onSecondary: { nav.go(to: .experience, forward: false, store: store) }) {
            HStack(spacing: 12) {
                CoachAvatar(coach: store.coach, size: 52)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Here's what I see")
                        .font(Theme.title(24))
                        .foregroundStyle(Theme.ink)
                        .accessibilityAddTraits(.isHeader)
                    Text(source)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.green)
                }
            }
            estimateCard
            VStack(spacing: 0) {
                ForEach(rows, id: \.0) { row in
                    Divider().overlay(Theme.hairline)
                    HStack {
                        Text(row.0)
                            .font(.system(size: 15))
                            .foregroundStyle(Theme.muted)
                        Spacer(minLength: 12)
                        Text(row.1)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Theme.ink)
                    }
                    .frame(height: 52)
                }
                Divider().overlay(Theme.hairline)
            }
        }
    }

    private var estimateCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Your \(reference.title), if you ran it today")
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
            Text(Fmt.goalTime(PlanEngine.predictedSeconds(vma: vma, goal: reference) ?? 0))
                .font(.system(size: 48, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Theme.ink)
            Text("That's your starting point. We set your goal right after.")
                .font(.system(size: 14))
                .foregroundStyle(Theme.body)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 24)
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
