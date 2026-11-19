import SwiftUI

/// Bounds and steps of the time pickers, per race distance.
enum OnboardingTimeRules {
    static func range(km: Double) -> ClosedRange<Int> {
        if km < 7.5 { return (12 * 60)...(70 * 60) }
        if km < 15 { return (25 * 60)...(140 * 60) }
        return (60 * 60)...(215 * 60)
    }

    static func clamp(_ seconds: Int, km: Double) -> Int {
        let bounds = range(km: km)
        return min(max(seconds, bounds.lowerBound), bounds.upperBound)
    }

    static func defaultSeconds(km: Double) -> Int {
        if km < 7.5 { return 30 * 60 }
        if km < 15 { return 60 * 60 }
        return 130 * 60
    }

    static func step(km: Double) -> Int {
        if km < 7.5 { return 5 }
        if km < 15 { return 10 }
        return 30
    }
}

/// Screen 8: a recent result (distance and time), or none.
struct OnboardingRecentScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator

    @State private var distanceKm: Double = 10
    @State private var seconds: Int = OnboardingTimeRules.defaultSeconds(km: 10)
    @State private var edited = false
    @State private var showWheel = false

    private var distances: [(String, Double)] { [("5 km", 5), ("10 km", 10), (L("Half"), 21.0975)] }

    var body: some View {
        OnboardingScaffold(title: L("A recent result?"),
                           subtitle: L("A race or an all-out run from the last 3 months."),
                           canContinue: edited) {
            distancePicker
            OnboardingTimeCard(seconds: seconds,
                               caption: edited
                                   ? L("that's %@ per km", OnboardingCopy.pacePerKm(seconds: seconds, km: distanceKm))
                                   : L("tap to set your time"),
                               stepSeconds: OnboardingTimeRules.step(km: distanceKm),
                               dimmed: !edited,
                               onChange: { setTime($0) },
                               onTapTime: { showWheel = true })
            Button(L("I don't have one")) {
                Haptics.tap()
                store.answers.recentResult = nil
                edited = false
                nav.next(store)
            }
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(Theme.ink)
            .underline()
            .frame(maxWidth: .infinity)
            .frame(height: 44)
        }
        .onAppear(perform: load)
        .sheet(isPresented: $showWheel) {
            OnboardingTimeWheelSheet(title: L("Your time"), showsHours: distanceKm > 15,
                                     initialSeconds: seconds, onDone: { setTime($0) })
        }
    }

    private var distancePicker: some View {
        HStack(spacing: 0) {
            ForEach(distances, id: \.0) { item in
                let selected = abs(item.1 - distanceKm) < 0.5
                Button(action: {
                    Haptics.select()
                    setDistance(item.1)
                }) {
                    Text(item.0)
                        .font(.system(size: 15, weight: selected ? .semibold : .medium))
                        .foregroundStyle(Theme.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(selected ? Color.white : Color.clear)
                                .shadow(color: .black.opacity(selected ? 0.1 : 0), radius: 2, x: 0, y: 1)
                        )
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(4)
        .background(Color(hex: 0xF1F1EF), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func load() {
        guard let result = store.answers.recentResult else { return }
        distanceKm = result.distanceKm
        seconds = result.seconds
        edited = true
    }

    private func setDistance(_ km: Double) {
        guard abs(km - distanceKm) > 0.5 else { return }
        let previousKm = max(1, distanceKm)
        withAnimation(.easeInOut(duration: 0.2)) { distanceKm = km }
        if edited {
            // Keep the same pace when switching distance, as a sensible starting point.
            let pace = Double(seconds) / previousKm
            seconds = OnboardingTimeRules.clamp(Int((pace * km).rounded()), km: km)
            store.answers.recentResult = RecentResult(distanceKm: km, seconds: seconds)
        } else {
            seconds = OnboardingTimeRules.defaultSeconds(km: km)
        }
    }

    private func setTime(_ value: Int) {
        seconds = OnboardingTimeRules.clamp(value, km: distanceKm)
        edited = true
        store.answers.recentResult = RecentResult(distanceKm: distanceKm, seconds: seconds)
    }
}

/// Screen 14: target time for the goal distance.
struct OnboardingTargetScreen: View {
    @EnvironmentObject private var store: AppStore

    /// nil until the user picks one of the two modes.
    @State private var precise: Bool?
    @State private var seconds: Int = 0
    @State private var showWheel = false

    private var goal: GoalType { store.answers.goal ?? .tenK }
    private var km: Double { goal.distanceKm ?? 10 }
    private var todaySeconds: Int {
        PlanEngine.predictedSeconds(vma: PlanEngine.estimateVMA(store.answers), goal: goal)
            ?? OnboardingTimeRules.defaultSeconds(km: km)
    }

    private var note: String {
        let pace = OnboardingCopy.pacePerKm(seconds: seconds, km: km)
        let time: String = Fmt.goalTime(seconds)
        let distance: String = Fmt.km(km)
        let reachable = Int(Double(todaySeconds) * 0.95)
        if seconds < reachable {
            return L("%@ is %@ per km for %@. That's ambitious from where you are today (around %@). I keep it as your goal and train you towards %@ first, so you stay safe.", time, pace, distance, Fmt.goalTime(todaySeconds), Fmt.goalTime(reachable))
        }
        if seconds <= todaySeconds {
            return L("%@ is %@ per km for %@. A real step up from today, and within reach.", time, pace, distance)
        }
        return L("%@ is %@ per km for %@. Comfortable for your level: a good way to enjoy the day.", time, pace, distance)
    }

    var body: some View {
        OnboardingScaffold(title: L("What time are you aiming for?"),
                           canContinue: precise != nil) {
            HStack(spacing: 10) {
                modeButton(L("Just finish"), isOn: precise == false) { choose(false) }
                modeButton(L("A precise time"), isOn: precise == true) { choose(true) }
            }
            if precise == true {
                OnboardingTimeCard(seconds: seconds,
                                   caption: L("for your %@", OnboardingCopy.goalNoun(goal)),
                                   stepSeconds: km > 15 ? 60 : 15,
                                   onChange: { setTime($0) },
                                   onTapTime: { showWheel = true })
                CoachNote(coach: store.coach, text: note)
            } else if precise == false {
                CoachNote(coach: store.coach,
                          text: L("No clock to chase. I'll get you to the finish line feeling good, and that's a goal in itself."))
            }
        }
        .onAppear(perform: load)
        .sheet(isPresented: $showWheel) {
            OnboardingTimeWheelSheet(title: L("Your target"), showsHours: km > 15,
                                     initialSeconds: seconds, onDone: { setTime($0) })
        }
    }

    private func modeButton(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: {
            Haptics.select()
            action()
        }) {
            Text(title)
                .font(.system(size: 15, weight: isOn ? .semibold : .medium))
                .foregroundStyle(Theme.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(isOn ? Theme.ink : Theme.controlBorder, lineWidth: isOn ? 1.5 : 1)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    private func load() {
        if let target = store.answers.targetSeconds {
            seconds = OnboardingTimeRules.clamp(target, km: km)
            precise = true
        } else {
            // Start from today's level, rounded down to the minute.
            seconds = OnboardingTimeRules.clamp(todaySeconds / 60 * 60, km: km)
        }
    }

    private func choose(_ wantsTime: Bool) {
        withAnimation(.easeInOut(duration: 0.25)) { precise = wantsTime }
        store.answers.targetSeconds = wantsTime ? seconds : nil
    }

    private func setTime(_ value: Int) {
        seconds = OnboardingTimeRules.clamp(value, km: km)
        store.answers.targetSeconds = seconds
    }
}

#Preview("Recent") {
    OnboardingRecentScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Target") {
    OnboardingTargetScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
