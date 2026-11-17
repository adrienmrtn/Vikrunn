import Foundation

enum SampleData {
    static var answers: OnboardingAnswers {
        var a = OnboardingAnswers()
        a.firstName = "Adrien"
        a.coachID = 1
        a.gender = .man
        a.experience = .sixMonthsToTwoYears
        a.weeklyVolume = .from10to25
        a.recentResult = RecentResult(distanceKm: 10, seconds: 49 * 60 + 10)
        a.goal = .half
        a.hasRace = true
        a.raceDate = Calendar.plan.date(byAdding: .day, value: 52, to: Date())
        a.targetSeconds = 105 * 60
        a.motivation = .prove
        a.runDays = [1, 2, 4, 7]
        a.longRunDay = 7
        a.tone = .kind
        return a
    }

    /// A few past runs so that Progress and the history are not empty.
    static func runs(for plan: TrainingPlan?) -> [RunRecord] {
        let calendar = Calendar.plan
        let easy = plan?.paces.easy ?? 350
        let samples: [(Int, Double, String)] = [(-2, 6.1, "Easy run"), (-4, 8.4, "6 × 800 m"), (-6, 12.2, "Long run"), (-9, 5.6, "Easy run"), (-11, 7.9, "Tempo")]
        return samples.map { offset, km, title in
            RunRecord(title: title,
                      date: calendar.date(byAdding: .day, value: offset, to: Date()) ?? Date(),
                      source: .gps, distanceM: km * 1000,
                      durationSec: Int(km * Double(easy - 12)), avgHeartRate: 148, feeling: .good)
        }
    }
}
