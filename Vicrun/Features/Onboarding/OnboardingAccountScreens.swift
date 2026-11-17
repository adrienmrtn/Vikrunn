import SwiftUI

/// Screen 24: summary of the answers, then the sign-in options sheet.
struct OnboardingSignInScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @State private var showOptions = false

    private struct Row: Identifiable {
        let id: String
        let symbol: String
        let value: String
    }

    private var rows: [Row] {
        let answers = store.answers
        var list: [Row] = [Row(id: "Goal", symbol: "target", value: OnboardingCopy.goalHeadline(answers))]
        if answers.hasRace, let date = answers.raceDate {
            list.append(Row(id: "Race", symbol: "flag.checkered", value: Fmt.day(date, "EEEE d MMMM")))
        }
        list.append(Row(id: "Rhythm", symbol: "calendar",
                        value: OnboardingCopy.sessions(OnboardingCopy.sessionsPerWeek(answers))))
        list.append(Row(id: "Coach", symbol: "person.fill",
                        value: "\(store.coach.name), \(answers.tone.title.lowercased()) tone"))
        return list
    }

    var body: some View {
        OnboardingScaffold(onContinue: { showOptions = true }) {
            CoachAvatar(coach: store.coach, size: 72)
            VStack(alignment: .leading, spacing: 8) {
                Text("It's all here, \(store.firstName)")
                    .font(Theme.title(30))
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text("I have what I need to build your plan. Let's put it somewhere safe first.")
                    .font(.system(size: 16))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
            }
            summaryCard
            Text("One account is enough to find your plan everywhere.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity, alignment: .center)
        }
        .sheet(isPresented: $showOptions) {
            SignInOptionsSheet { account in
                store.account = account
                showOptions = false
                nav.next(store)
            }
        }
    }

    private var summaryCard: some View {
        VStack(spacing: 0) {
            ForEach(rows) { row in
                if row.id != rows.first?.id {
                    Divider().overlay(Theme.cardBorder)
                }
                HStack(spacing: 12) {
                    Image(systemName: row.symbol)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.green)
                        .frame(width: 22)
                        .accessibilityHidden(true)
                    Text(row.id)
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.muted)
                    Spacer(minLength: 8)
                    Text(row.value)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
                .frame(height: 56)
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 6)
        .card(radius: 24)
    }
}

/// Screen 25: staged loading while the plan is generated.
struct OnboardingLoadingScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @State private var progress: Double = 0

    private var stages: [String] {
        let answers = store.answers
        let sessions = OnboardingCopy.sessionsPerWeek(answers)
        var list = [
            "Reading your answers",
            "Working out your paces",
            sessions == 1 ? "Placing your session of the week" : "Placing your \(sessions) sessions a week",
            "Adding strength and stretching",
        ]
        if answers.hasRace, let date = answers.raceDate, answers.goal != .regular {
            list.append("Easing off before \(Fmt.day(date, "d MMMM"))")
        } else {
            list.append("Balancing your \(PlanEngine.maxWeeks) weeks")
        }
        return list
    }

    private var subtitle: String {
        let answers = store.answers
        var text = OnboardingCopy.goalHeadline(answers)
        if answers.hasRace, let date = answers.raceDate, answers.goal != .regular {
            text += " · \(Fmt.day(date, "d MMMM"))"
        }
        return text
    }

    var body: some View {
        VStack(spacing: 26) {
            ring
            VStack(spacing: 6) {
                Text("\(Int((progress * 100).rounded())) %")
                    .font(.system(size: 15, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.green)
                Text("I'm building your plan")
                    .font(Theme.title(26))
                    .foregroundStyle(Theme.ink)
                    .accessibilityAddTraits(.isHeader)
                Text(subtitle)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.muted)
            }
            .multilineTextAlignment(.center)
            stageList
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 28)
        .padding(.top, 48)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.white.ignoresSafeArea())
        .task {
            store.generatePlan()
            let ticks = 80
            for tick in 1...ticks {
                try? await Task.sleep(nanoseconds: 55_000_000)
                if Task.isCancelled { return }
                withAnimation(.linear(duration: 0.06)) {
                    progress = Double(tick) / Double(ticks)
                }
            }
            try? await Task.sleep(nanoseconds: 450_000_000)
            if Task.isCancelled { return }
            nav.next(store)
        }
    }

    private var ring: some View {
        ZStack {
            Circle()
                .stroke(Theme.hairline, lineWidth: 8)
            Circle()
                .trim(from: 0, to: max(0.01, progress))
                .stroke(Theme.green, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                .rotationEffect(.degrees(-90))
            CoachAvatar(coach: store.coach, size: 136)
        }
        .frame(width: 180, height: 180)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Plan being built, \(Int((progress * 100).rounded())) percent")
    }

    private var stageList: some View {
        let items = stages
        return VStack(spacing: 0) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, text in
                let done = progress >= Double(index + 1) / Double(items.count)
                let active = !done && progress >= Double(index) / Double(items.count)
                Divider().overlay(Theme.hairline)
                HStack(spacing: 12) {
                    stageMark(done: done)
                    Text(text)
                        .font(.system(size: 15, weight: active ? .semibold : .regular))
                        .foregroundStyle(done || active ? Theme.ink : Theme.muted)
                    Spacer(minLength: 0)
                }
                .frame(height: 50)
            }
            Divider().overlay(Theme.hairline)
        }
    }

    @ViewBuilder private func stageMark(done: Bool) -> some View {
        if done {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 22))
                .foregroundStyle(Theme.green)
                .frame(width: 22, height: 22)
                .transition(.scale.combined(with: .opacity))
        } else {
            Circle()
                .stroke(Theme.controlBorder, lineWidth: 2)
                .frame(width: 22, height: 22)
        }
    }
}

#Preview("Sign in") {
    OnboardingSignInScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Loading") {
    OnboardingLoadingScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
