import SwiftUI

/// Entering a run after the fact (treadmill, forgotten phone). Present as a sheet.
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

    private var pacePreview: String {
        if distanceKm <= 0 || durationSec <= 0 { return "Enter a distance and a duration." }
        if distanceKm < 0.1 { return "The distance is too short to be saved." }
        if distanceKm > 100 { return "That is more than 100 km. Check the distance." }
        if durationSec < 60 { return "The duration must be at least one minute." }
        if pace < 120 { return "That is \(Fmt.pace(pace)) per km, faster than anyone runs. Check the distance and the duration." }
        if pace > 1800 { return "That is \(Fmt.pace(pace)) per km. Check the distance and the duration." }
        return "That is \(Fmt.pace(pace)) per km."
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("Date", selection: $date, in: ...Date(), displayedComponents: [.date, .hourAndMinute])
                    HStack {
                        Text("Distance")
                        Spacer()
                        TextField("0.0", text: $distanceText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(maxWidth: 110)
                            .focused($distanceFocused)
                            .accessibilityLabel("Distance in kilometres")
                        Text("km")
                            .foregroundStyle(Theme.muted)
                    }
                } header: {
                    Text(workout?.title ?? "Run")
                } footer: {
                    Text("Ran on a treadmill? Enter the distance and time shown on the machine.")
                }

                Section {
                    durationPickers
                } header: {
                    Text("Duration")
                } footer: {
                    Text(pacePreview)
                }

                Section {
                    Picker("How was it?", selection: $feeling) {
                        Text("Not set").tag(Optional<Feeling>.none)
                        ForEach(Feeling.allCases) { option in
                            Text(option.title).tag(Optional<Feeling>.some(option))
                        }
                    }
                } footer: {
                    Text("Optional. It helps your coach tune the next sessions.")
                }
            }
            .navigationTitle("Add a run")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .fontWeight(.semibold)
                        .disabled(!isValid)
                }
                // The decimal pad has no return key.
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") { distanceFocused = false }
                        .fontWeight(.semibold)
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
    }

    private var durationPickers: some View {
        HStack(spacing: 0) {
            durationWheel("h", selection: $hours, range: 0..<10)
            durationWheel("min", selection: $minutes, range: 0..<60)
            durationWheel("s", selection: $seconds, range: 0..<60)
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
        dismiss()
    }
}

#Preview {
    ManualRunEntryView(workout: AppStore.preview.nextRun)
        .environmentObject(AppStore.preview)
}
