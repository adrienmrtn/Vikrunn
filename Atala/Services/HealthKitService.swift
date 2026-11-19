import Foundation
import HealthKit

/// Reads past runs (and their average heart rate) from Apple Health. Nothing is written to Health in this version.
final class HealthKitService {
    static let shared = HealthKitService()

    private let healthStore: HKHealthStore?

    /// A running workout reduced to plain values.
    private struct ImportedRun: Sendable {
        var start: Date
        var durationSec: Int
        var distanceM: Double
        var avgHeartRate: Int?
    }

    /// Start dates of Health runs the user deleted from the history, so they are not imported again.
    private static let ignoredKey = "atala.health.ignoredStarts"
    /// Minimum time between two automatic imports (launch, return to the foreground).
    private static let automaticInterval: TimeInterval = 15 * 60
    private var lastAutomaticImport: Date?
    private var isImporting = false

    private init() {
        healthStore = HKHealthStore.isHealthDataAvailable() ? HKHealthStore() : nil
    }

    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    /// Asks to read workouts and heart rate. True when the request went through (Health never tells whether reading was allowed).
    func requestAuthorization() async -> Bool {
        guard let healthStore else { return false }
        let readTypes: Set<HKObjectType> = [HKObjectType.workoutType(), HKQuantityType(.heartRate)]
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
        guard isAvailable, !isImporting else { return 0 }
        isImporting = true
        defer { isImporting = false }
        guard await requestAuthorization() else { return 0 }
        let imported = await fetchRecentRuns(days: 60)
        let ignored: [Double] = Self.ignoredStarts()
        var added = 0
        for item in imported {
            let start: Double = item.start.timeIntervalSince1970
            if ignored.contains(where: { abs($0 - start) < 60 }) { continue }
            let alreadyThere = store.runs.contains { run in
                Self.isSameRun(run, start: item.start, durationSec: item.durationSec, distanceM: item.distanceM)
            }
            if alreadyThere { continue }
            let run = RunRecord(title: "Run",
                                date: item.start,
                                source: .health,
                                distanceM: item.distanceM,
                                durationSec: item.durationSec,
                                avgHeartRate: item.avgHeartRate)
            store.addRun(run)
            added += 1
        }
        return added
    }

    /// Import on launch and when the app comes back, when the user turned the import on.
    /// Quiet: no permission sheet is shown here unless Health was never asked, and it runs at most
    /// once every quarter of an hour.
    @MainActor
    func importIfDue(into store: AppStore) async -> Int {
        guard store.settings.healthImportEnabled, isAvailable else { return 0 }
        if let last = lastAutomaticImport, Date().timeIntervalSince(last) < Self.automaticInterval { return 0 }
        lastAutomaticImport = Date()
        return await importRecentRuns(into: store)
    }

    /// Call when the user deletes a run that came from Health, so the next import leaves it out.
    func ignoreRun(startingAt date: Date) {
        var list: [Double] = Self.ignoredStarts()
        list.append(date.timeIntervalSince1970)
        // Imports only look 60 days back: older entries are useless.
        let limit: Double = Date().timeIntervalSince1970 - 90 * 86_400
        list = list.filter { $0 >= limit }
        UserDefaults.standard.set(list, forKey: Self.ignoredKey)
    }

    private static func ignoredStarts() -> [Double] {
        UserDefaults.standard.array(forKey: ignoredKey) as? [Double] ?? []
    }

    /// The same run seen twice: recorded here and by a watch, or already imported.
    /// Either it starts within a minute with a similar distance, or the two overlap for most of their time.
    private static func isSameRun(_ run: RunRecord, start: Date, durationSec: Int, distanceM: Double) -> Bool {
        let sameStart = abs(run.date.timeIntervalSince(start)) < 60
        let tolerance = max(150, distanceM * 0.05)
        if sameStart && abs(run.distanceM - distanceM) <= tolerance { return true }
        let runLength = Double(max(60, run.durationSec))
        let otherLength = Double(max(60, durationSec))
        let overlapStart = max(run.date, start)
        let overlapEnd = min(run.date.addingTimeInterval(runLength), start.addingTimeInterval(otherLength))
        let overlap = overlapEnd.timeIntervalSince(overlapStart)
        return overlap > 0.5 * min(runLength, otherLength)
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
                    result.append(ImportedRun(start: workout.startDate, durationSec: seconds, distanceM: metres,
                                              avgHeartRate: HealthKitService.averageHeartRate(of: workout)))
                }
                continuation.resume(returning: result)
            }
            healthStore.execute(query)
        }
    }

    /// Average and highest heart rate recorded in Health between two dates (a watch worn during a run
    /// tracked with the phone). Nil when Health has nothing for that time. Asks for nothing: call it
    /// only when the user already turned the Health import on.
    func heartRate(from start: Date, to end: Date) async -> (average: Int, peak: Int)? {
        guard let healthStore, end > start else { return nil }
        let type = HKQuantityType(.heartRate)
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: [])
        let unit = HKUnit.count().unitDivided(by: HKUnit.minute())
        return await withCheckedContinuation { (continuation: CheckedContinuation<(average: Int, peak: Int)?, Never>) in
            let query = HKStatisticsQuery(quantityType: type,
                                          quantitySamplePredicate: predicate,
                                          options: [.discreteAverage, .discreteMax]) { _, statistics, _ in
                guard let average = statistics?.averageQuantity()?.doubleValue(for: unit),
                      average > 20, average < 260 else {
                    continuation.resume(returning: nil)
                    return
                }
                let peak = statistics?.maximumQuantity()?.doubleValue(for: unit) ?? average
                continuation.resume(returning: (average: Int(average.rounded()), peak: Int(max(average, peak).rounded())))
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

    /// Average heart rate of the workout in beats per minute, when the watch recorded one.
    private static func averageHeartRate(of workout: HKWorkout) -> Int? {
        let type = HKQuantityType(.heartRate)
        guard let average = workout.statistics(for: type)?.averageQuantity() else { return nil }
        let unit = HKUnit.count().unitDivided(by: HKUnit.minute())
        let bpm = average.doubleValue(for: unit)
        guard bpm > 20, bpm < 260 else { return nil }
        return Int(bpm.rounded())
    }
}
