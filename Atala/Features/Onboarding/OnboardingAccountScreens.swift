import SwiftUI

/// Screen 24: summary of the answers, then the sign-in options sheet.
struct OnboardingSignInScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @State private var showOptions = false
    @State private var isRestoring = false
    @State private var signInError: String?

    private struct Row: Identifiable {
        let id: String
        let label: String
        let symbol: String
        let value: String
    }

    private var rows: [Row] {
        let answers = store.answers
        var list: [Row] = [Row(id: "Goal", label: L("Goal"), symbol: "target", value: OnboardingCopy.goalHeadline(answers))]
        if answers.hasRace, let date = answers.raceDate {
            list.append(Row(id: "Race", label: L("Race"), symbol: "flag.checkered", value: Fmt.day(date, "EEEE d MMMM")))
        }
        list.append(Row(id: "Rhythm", label: L("Rhythm"), symbol: "calendar",
                        value: OnboardingCopy.sessions(OnboardingCopy.sessionsPerWeek(answers))))
        list.append(Row(id: "Coach", label: L("Coach"), symbol: "person.fill",
                        value: L("%@, %@ tone", store.coach.name, answers.tone.title.midSentence)))
        return list
    }

    var body: some View {
        OnboardingScaffold(onContinue: { showOptions = true }) {
            CoachAvatar(coach: store.coach, size: 72)
            VStack(alignment: .leading, spacing: 8) {
                Text(L("It's all here, %@", store.firstName))
                    .font(Theme.title(30))
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text(L("I have what I need to build your plan. Let's put it somewhere safe first."))
                    .font(.system(size: 16))
                    .foregroundStyle(Color(hex: 0x5A5A5A))
                    .lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
            }
            summaryCard
            Text(L("One account is enough to find your plan everywhere."))
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity, alignment: .center)
        }
        .sheet(isPresented: $showOptions) {
            SignInOptionsSheet { account in
                showOptions = false
                finishSignIn(account)
            }
        }
        .alert(L("We couldn't open your account"), isPresented: signInErrorShown) {
            Button(L("OK"), role: .cancel) {}
        } message: {
            Text(signInError ?? L("Please try again."))
        }
    }

    private var signInErrorShown: Binding<Bool> {
        Binding(
            get: { signInError != nil },
            set: { shown in
                if !shown { signInError = nil }
            }
        )
    }

    /// An account that already holds a plan gets it back and opens the app. A new account carries on:
    /// the plan is built next and saved to the account.
    private func finishSignIn(_ account: Account) {
        guard !isRestoring else { return }
        isRestoring = true
        Task { @MainActor in
            let outcome = await AuthSignInFlow.complete(account, store: store, policy: .preferAccount)
            isRestoring = false
            switch outcome {
            case .restored, .needsChoice:
                break // the root view switches to the app
            case .continued:
                nav.next(store)
            case .failed(let failure):
                Haptics.error()
                signInError = failure.userMessage
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
                    Text(row.label)
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

/// Screen 25: staged loading while the plan is generated. The ring fills in one continuous,
/// eased movement (its value is computed from the clock at every frame, never stepped), the
/// checklist follows it, and the screen rests a moment on the finished ring before moving on.
struct OnboardingLoadingScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the ring started to fill. nil until the screen is on.
    @State private var startedAt: Date?
    /// The header, ring and lines have faded in.
    @State private var appeared = false
    /// The ring is full: the screen settles.
    @State private var completed = false

    /// Seconds the ring takes to fill.
    private static let fillDuration: Double = 4.8
    /// Seconds spent on the finished ring before the next screen.
    private static let restDuration: Double = 0.9

    private var stages: [String] {
        let answers = store.answers
        let sessions = OnboardingCopy.sessionsPerWeek(answers)
        var list: [String] = [
            L("Reading your answers"),
            // No pace talk for someone who does not run yet.
            OnboardingNavigator.isNotRunningYet(answers) ? L("Planning a gentle start") : L("Working out your paces"),
            sessions == 1 ? L("Placing your session of the week") : L("Placing your %@ sessions a week", sessions),
            L("Adding strength and stretching"),
        ]
        if answers.hasRace, let date = answers.raceDate, !answers.goal.isOpenEnded {
            list.append(L("Easing off before %@", Fmt.day(date, "d MMMM")))
        } else {
            list.append(L("Balancing your %@ weeks", PlanEngine.maxWeeks))
        }
        return list
    }

    private var subtitle: String {
        let answers = store.answers
        var text = OnboardingCopy.goalHeadline(answers)
        if answers.hasRace, let date = answers.raceDate, !answers.goal.isOpenEnded {
            text += " · \(Fmt.day(date, "d MMMM"))"
        }
        return text
    }

    /// 0...1 at `date`: slow start, steady middle, soft landing. Always moving forward.
    private func progress(at date: Date) -> Double {
        if completed { return 1 }
        guard let startedAt else { return 0 }
        let elapsed: Double = date.timeIntervalSince(startedAt)
        let t: Double = min(max(elapsed / Self.fillDuration, 0), 1)
        let eased: Double = t * t * (3 - 2 * t)
        return 0.3 * t + 0.7 * eased
    }

    var body: some View {
        GeometryReader { geo in
            // Small phones (iPhone SE): the ring, the gaps and the lines shrink so everything fits.
            let scale: CGFloat = ScreenFit.scale(geo.size.height, reference: 700, minimum: 0.75)
            TimelineView(.animation) { context in
                content(progress: progress(at: context.date), scale: scale)
            }
        }
        .padding(.horizontal, 28)
        .padding(.top, 48)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.white.ignoresSafeArea())
        .task {
            await run()
        }
    }

    @MainActor
    private func run() async {
        store.generatePlan()
        if reduceMotion {
            appeared = true
        } else {
            withAnimation(.easeOut(duration: 0.5)) {
                appeared = true
            }
        }
        if startedAt == nil { startedAt = Date() }
        try? await Task.sleep(nanoseconds: UInt64(Self.fillDuration * 1_000_000_000))
        if Task.isCancelled { return }
        withAnimation(.easeInOut(duration: 0.4)) {
            completed = true
        }
        Haptics.success()
        try? await Task.sleep(nanoseconds: UInt64(Self.restDuration * 1_000_000_000))
        if Task.isCancelled { return }
        nav.next(store)
    }

    private func content(progress: Double, scale: CGFloat) -> some View {
        let percent: Int = Int((progress * 100).rounded())
        return VStack(spacing: 26 * scale) {
            BrandLogo(style: .full, height: 22)
                .foregroundStyle(Theme.forest)
                .opacity(appeared ? 1 : 0)
            ring(progress: progress, percent: percent, size: 180 * scale)
                .opacity(appeared ? 1 : 0)
                .scaleEffect(appeared ? 1 : 0.94)
            VStack(spacing: 6) {
                Text("\(percent) %")
                    .font(.system(size: 15, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.green)
                    .accessibilityHidden(true)
                Text(completed ? L("Your plan is ready") : L("I'm building your plan"))
                    .font(Theme.title(26))
                    .foregroundStyle(Theme.ink)
                    .contentTransition(.opacity)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text(subtitle)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .multilineTextAlignment(.center)
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 8)
            stageList(progress: progress, rowHeight: max(42, 50 * scale))
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity)
    }

    private func ring(progress: Double, percent: Int, size: CGFloat) -> some View {
        ZStack {
            Circle()
                .stroke(Theme.hairline, lineWidth: 8)
            Circle()
                .trim(from: 0, to: CGFloat(min(max(progress, 0.002), 1)))
                .stroke(Theme.green, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                .rotationEffect(.degrees(-90))
            CoachAvatar(coach: store.coach, size: size * 136 / 180)
                // A small breath when the ring closes.
                .scaleEffect(completed && !reduceMotion ? 1.04 : 1)
                .animation(.spring(response: 0.5, dampingFraction: 0.6), value: completed)
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Plan being built, %@ percent", percent))
    }

    private func stageList(progress: Double, rowHeight: CGFloat) -> some View {
        let items: [String] = stages
        let count: Double = Double(max(items.count, 1))
        return VStack(spacing: 0) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, text in
                let done: Bool = progress >= Double(index + 1) / count
                let active: Bool = !done && progress >= Double(index) / count
                // Each line fades in a little after the one above it.
                let entrance: Animation? = reduceMotion ? nil : Animation.easeOut(duration: 0.45).delay(0.15 + 0.09 * Double(index))
                VStack(spacing: 0) {
                    Divider().overlay(Theme.hairline)
                    HStack(spacing: 12) {
                        stageMark(done: done, active: active)
                        // One weight for every state: the line never changes width.
                        Text(text)
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(done || active ? Theme.ink : Theme.faint)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .animation(.easeInOut(duration: 0.35), value: done || active)
                        Spacer(minLength: 0)
                    }
                    .frame(height: rowHeight)
                }
                .opacity(appeared ? 1 : 0)
                .offset(y: appeared ? 0 : 10)
                .animation(entrance, value: appeared)
            }
            Divider().overlay(Theme.hairline)
                .opacity(appeared ? 1 : 0)
        }
    }

    /// Empty circle, then tinted while its step is running, then the tick grows into it. The
    /// three marks stay in place, so nothing is inserted or removed.
    private func stageMark(done: Bool, active: Bool) -> some View {
        ZStack {
            Circle()
                .stroke(active ? Theme.green : Theme.controlBorder, lineWidth: 2)
                .frame(width: 20, height: 20)
                .opacity(done ? 0 : 1)
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 22))
                .foregroundStyle(Theme.green)
                .scaleEffect(done ? 1 : 0.5)
                .opacity(done ? 1 : 0)
        }
        .frame(width: 22, height: 22)
        .animation(.spring(response: 0.38, dampingFraction: 0.72), value: done)
        .animation(.easeInOut(duration: 0.3), value: active)
        .accessibilityHidden(true)
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
