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

    private var isValid: Bool { distanceKm > 0 && distanceKm < 300 && durationSec > 0 }

    private var pacePreview: String {
        guard isValid else { return "Enter a distance and a duration." }
        let pace = Int(Double(durationSec) / distanceKm)
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
            }
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
        let run = RunRecord(workoutID: workout?.id,
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
