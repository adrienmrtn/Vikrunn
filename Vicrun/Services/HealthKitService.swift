import Foundation
import HealthKit

/// Reads past runs from Apple Health. Nothing is written to Health in this version.
final class HealthKitService {
    static let shared = HealthKitService()

    private let healthStore: HKHealthStore?

    /// A running workout reduced to plain values.
    private struct ImportedRun: Sendable {
        var start: Date
        var durationSec: Int
        var distanceM: Double
    }

    private init() {
        healthStore = HKHealthStore.isHealthDataAvailable() ? HKHealthStore() : nil
    }

    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    /// Asks to read workouts. True when the request went through (Health never tells whether reading was allowed).
    func requestAuthorization() async -> Bool {
        guard let healthStore else { return false }
        let readTypes: Set<HKObjectType> = [HKObjectType.workoutType()]
        let shareTypes: Set<HKSampleType> = []
        do {
            try await healthStore.requestAuthorization(toShare: shareTypes, read: readTypes)
            return true
        } catch {
            return false
        }
    }

    /// Imports the runs of the last 60 days that are not in the app yet. Returns how many were added.
    @MainActor
    func importRecentRuns(into store: AppStore) async -> Int {
        guard isAvailable else { return 0 }
        guard await requestAuthorization() else { return 0 }
        let imported = await fetchRecentRuns(days: 60)
        var added = 0
        for item in imported {
            let alreadyThere = store.runs.contains { run in
                let sameStart = abs(run.date.timeIntervalSince(item.start)) < 60
                let tolerance = max(150, item.distanceM * 0.05)
                let similarDistance = abs(run.distanceM - item.distanceM) <= tolerance
                return sameStart && similarDistance
            }
            if alreadyThere { continue }
            let run = RunRecord(title: "Run",
                                date: item.start,
                                source: .health,
                                distanceM: item.distanceM,
                                durationSec: item.durationSec)
            store.addRun(run)
            added += 1
        }
        return added
    }

    private func fetchRecentRuns(days: Int) async -> [ImportedRun] {
        guard let healthStore else { return [] }
        let end = Date()
        let start = Calendar.current.date(byAdding: .day, value: -days, to: end) ?? end.addingTimeInterval(-Double(days) * 86_400)
        let running = HKQuery.predicateForWorkouts(with: .running)
        let recent = HKQuery.predicateForSamples(withStart: start, end: end, options: .strictStartDate)
        let predicate = NSCompoundPredicate(andPredicateWithSubpredicates: [running, recent])
        let newestFirst = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)

        return await withCheckedContinuation { (continuation: CheckedContinuation<[ImportedRun], Never>) in
            let query = HKSampleQuery(sampleType: HKObjectType.workoutType(),
                                      predicate: predicate,
                                      limit: HKObjectQueryNoLimit,
                                      sortDescriptors: [newestFirst]) { _, samples, _ in
                var result: [ImportedRun] = []
                for sample in samples ?? [] {
                    guard let workout = sample as? HKWorkout else { continue }
                    let metres = HealthKitService.distance(of: workout)
                    let seconds = Int(workout.duration.rounded())
                    guard metres > 0, seconds > 0 else { continue }
                    result.append(ImportedRun(start: workout.startDate, durationSec: seconds, distanceM: metres))
                }
                continuation.resume(returning: result)
            }
            healthStore.execute(query)
        }
    }

    private static func distance(of workout: HKWorkout) -> Double {
        let type = HKQuantityType(.distanceWalkingRunning)
        if let sum = workout.statistics(for: type)?.sumQuantity() {
            return sum.doubleValue(for: HKUnit.meter())
        }
        return 0
    }
}
