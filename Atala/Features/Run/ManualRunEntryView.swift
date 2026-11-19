import SwiftUI

/// Entering a run after the fact (treadmill, forgotten phone). Present as a sheet.
/// A short form: when, how far, how long. The pace is worked out live, and anything that stops the
/// run from being saved is explained in words next to it.
struct ManualRunEntryView: View {
    let workout: Workout?

    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    @State private var date: Date
    @State private var distanceText: String
    @State private var hours: Int
    @State private var minutes: Int
    @State private var seconds: Int
    @State private var feeling: Feeling?
    @FocusState private var distanceFocused: Bool

    init(workout: Workout?) {
        self.workout = workout
        let now = Date()
        var initialDate = now
        var initialDistance = ""
        var initialMinutes = 30
        if let workout {
            // A past session is prefilled on its own day, at midday.
            let midday = Calendar.plan.date(byAdding: .hour, value: 12, to: Calendar.plan.startOfDay(for: workout.date)) ?? now
            initialDate = min(midday, now)
            if workout.distanceKm > 0 { initialDistance = String(format: "%.1f", workout.distanceKm) }
            initialMinutes = max(1, workout.durationMin)
        }
        _date = State(initialValue: initialDate)
        _distanceText = State(initialValue: initialDistance)
        _hours = State(initialValue: initialMinutes / 60)
        _minutes = State(initialValue: initialMinutes % 60)
        _seconds = State(initialValue: 0)
        _feeling = State(initialValue: nil)
    }

    private var distanceKm: Double {
        let cleaned = distanceText.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)
        return Double(cleaned) ?? 0
    }

    private var durationSec: Int { hours * 3600 + minutes * 60 + seconds }

    /// Seconds per kilometre of what was typed. 0 when it cannot be computed yet.
    private var pace: Int {
        guard distanceKm > 0, durationSec > 0 else { return 0 }
        return Int(Double(durationSec) / distanceKm)
    }

    /// Faster than 2:00 per km or slower than 30:00 per km is a typing mistake, not a run.
    private var paceIsPlausible: Bool { pace >= 120 && pace <= 1800 }

    private var isValid: Bool {
        distanceKm >= 0.1 && distanceKm <= 100 && durationSec >= 60 && paceIsPlausible && date <= Date()
    }

    /// What stops the run from being saved, in plain words. Nil when everything is fine.
    private var problem: String? {
        if distanceKm <= 0 || durationSec <= 0 { return nil }
        if distanceKm < 0.1 { return L("The distance is too short to be saved.") }
        if distanceKm > 100 { return L("That is more than 100 km. Check the distance.") }
        if durationSec < 60 { return L("The duration must be at least one minute.") }
        if pace < 120 { return L("That is %@ per km, faster than anyone runs. Check the distance and the duration.", Fmt.pace(pace)) }
        if pace > 1800 { return L("That is %@ per km. Check the distance and the duration.", Fmt.pace(pace)) }
        return nil
    }

    /// The planned session this run will tick off, if any.
    private var sessionToComplete: Workout? {
        guard let workout, let current = store.workout(id: workout.id), current.status != .done else { return nil }
        return current
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    intro
                    dateCard
                    distanceCard
                    durationCard
                    paceBanner
                    feelingSection
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 8)
                .padding(.bottom, 20)
            }
            .scrollIndicators(.hidden)
            .background(Color.white.ignoresSafeArea())
            .safeAreaInset(edge: .bottom, spacing: 0) {
                BottomBar {
                    // Saving plays its own success haptic.
                    PrimaryButton(title: L("Save run"), enabled: isValid, haptic: nil, action: save)
                }
            }
            .navigationTitle(L("Add a run"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("Cancel")) {
                        Haptics.tap()
                        dismiss()
                    }
                }
                // The decimal pad has no return key.
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button(L("Done")) { distanceFocused = false }
                        .fontWeight(.semibold)
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
    }

    // MARK: Sections

    private var intro: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L("Ran on a treadmill? Enter the distance and time shown on the machine."))
                .font(.system(size: 15))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
            if let session = sessionToComplete {
                HStack(spacing: 10) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.green)
                        .accessibilityHidden(true)
                    Text(L("Counts as your session: %@", session.title))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.green)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Theme.greenTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
        }
    }

    private var dateCard: some View {
        HStack(spacing: 12) {
            IconTile(systemName: "calendar", size: 40)
            DatePicker(L("Date"), selection: $date, in: ...Date(), displayedComponents: [.date, .hourAndMinute])
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .tint(Theme.green)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private var distanceCard: some View {
        HStack(alignment: .center, spacing: 12) {
            IconTile(systemName: "ruler", size: 40)
            Text(L("Distance"))
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.ink)
            Spacer(minLength: 8)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                TextField("0.0", text: $distanceText)
                    .font(.system(size: 32, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 120)
                    .focused($distanceFocused)
                    .accessibilityLabel(L("Distance in kilometres"))
                Text("km")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.muted)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(selected: distanceFocused)
        .contentShape(Rectangle())
        .onTapGesture { distanceFocused = true }
    }

    private var durationCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 12) {
                IconTile(systemName: "stopwatch", size: 40)
                Text(L("Duration"))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                Text(Fmt.duration(durationSec))
                    .font(.system(size: 32, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .accessibilityHidden(true)
            }
            durationPickers
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    /// The pace worked out live from what is typed, or what is wrong with it.
    @ViewBuilder
    private var paceBanner: some View {
        if let problem {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.orangeDeep)
                    .accessibilityHidden(true)
                Text(problem)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.orangeDeep)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.orangeTint, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        } else if pace > 0 {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(L("Average pace"))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.green)
                Spacer(minLength: 8)
                Text(Fmt.pace(pace))
                    .font(.system(size: 32, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.green)
                Text("/km")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.green)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.greenTint, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L("That is %@ per km.", Fmt.pace(pace)))
        } else {
            Text(L("Enter a distance and a duration to see your pace."))
                .font(.system(size: 14))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
    }

    private var feelingSection: some View {
        let columns: [GridItem] = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(L("How was it?"))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                Text(L("Optional"))
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
            }
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(Feeling.allCases) { option in
                    feelingChip(option)
                }
            }
            Text(L("It helps your coach tune the next sessions."))
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, 6)
    }

    private func feelingChip(_ option: Feeling) -> some View {
        let selected: Bool = feeling == option
        return Button(action: {
            Haptics.select()
            feeling = selected ? nil : option
        }) {
            HStack(spacing: 10) {
                Circle()
                    .fill(RunFeelingStyle.background(option))
                    .frame(width: 34, height: 34)
                    .overlay(
                        Image(systemName: RunFeelingStyle.symbol(option))
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(RunFeelingStyle.tint(option))
                    )
                    .accessibilityHidden(true)
                Text(option.title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
            .card(selected: selected, radius: 16)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var durationPickers: some View {
        HStack(spacing: 0) {
            durationWheel(L("h"), selection: $hours, range: 0..<10)
            durationWheel(L("min"), selection: $minutes, range: 0..<60)
            durationWheel(L("s"), selection: $seconds, range: 0..<60)
        }
        .frame(height: 120)
    }

    private func durationWheel(_ unit: String, selection: Binding<Int>, range: Range<Int>) -> some View {
        Picker(unit, selection: selection) {
            ForEach(range, id: \.self) { value in
                Text("\(value) \(unit)").tag(value)
            }
        }
        .pickerStyle(.wheel)
        .frame(maxWidth: .infinity)
        .clipped()
    }

    private func save() {
        guard isValid else { return }
        distanceFocused = false
        // A session already done keeps its run: this one is saved as a plain run.
        var workoutID: UUID? = workout?.id
        if let id = workoutID, let current = store.workout(id: id), current.status == .done {
            workoutID = nil
        }
        let run = RunRecord(workoutID: workoutID,
                            title: workout?.title ?? "Run",
                            date: date,
                            source: .manual,
                            distanceM: distanceKm * 1000,
                            durationSec: durationSec,
                            feeling: feeling)
        store.addRun(run)
        Haptics.success()
        dismiss()
    }
}

#Preview {
    ManualRunEntryView(workout: AppStore.preview.nextRun)
        .environmentObject(AppStore.preview)
}
