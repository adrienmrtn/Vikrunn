import Foundation

/// Rule-based plan generator. Deterministic: the same answers always give the same plan.
/// Method: VMA (maximal aerobic speed) estimate, then target paces as fractions of VMA.
enum PlanEngine {
    static let maxWeeks = 8

    // MARK: Level

    /// VMA in km/h, from a recent result when there is one, otherwise a cautious guess.
    static func estimateVMA(_ answers: OnboardingAnswers) -> Double {
        if let result = answers.recentResult, result.seconds > 0, result.distanceKm > 0 {
            let speed = result.distanceKm / (Double(result.seconds) / 3600)
            return min(max(speed / sustainableFraction(distanceKm: result.distanceKm), 7), 22)
        }
        var vma: Double
        switch answers.experience ?? .starting {
        case .starting: vma = 9
        case .underSixMonths: vma = 10.5
        case .sixMonthsToTwoYears: vma = 12
        case .overTwoYears: vma = 13
        }
        if answers.weeklyVolume == .over25 { vma += 0.5 }
        return vma
    }

    /// Share of VMA a runner holds over a given race distance.
    static func sustainableFraction(distanceKm: Double) -> Double {
        switch distanceKm {
        case ..<3.5: return 0.98
        case ..<7.5: return 0.93
        case ..<15: return 0.88
        case ..<30: return 0.82
        default: return 0.76
        }
    }

    static func paceFor(vma: Double, fraction: Double) -> Int {
        Int((3600 / (vma * fraction)).rounded())
    }

    static func predictedSeconds(vma: Double, goal: GoalType) -> Int? {
        guard let distance = goal.distanceKm else { return nil }
        return Int((distance / (vma * sustainableFraction(distanceKm: distance)) * 3600).rounded())
    }

    static func paces(vma: Double, goal: GoalType, targetSeconds: Int?) -> Paces {
        let tempo = paceFor(vma: vma, fraction: 0.85)
        var race = tempo
        if let distance = goal.distanceKm {
            let predicted = paceFor(vma: vma, fraction: sustainableFraction(distanceKm: distance))
            if let targetSeconds {
                let wanted = Int(Double(targetSeconds) / distance)
                // Stay cautious: never train more than 5 % faster than today's level.
                race = max(wanted, Int(Double(predicted) * 0.95))
            } else {
                race = predicted
            }
        }
        return Paces(
            easy: paceFor(vma: vma, fraction: 0.65),
            long: paceFor(vma: vma, fraction: 0.68),
            tempo: tempo,
            interval: paceFor(vma: vma, fraction: 0.97),
            race: race
        )
    }

    // MARK: Plan

    static func generate(answers: OnboardingAnswers, from start: Date = Date()) -> TrainingPlan {
        let calendar = Calendar.plan
        let today = calendar.startOfDay(for: start)
        let weekStart = calendar.mondayOfWeek(containing: today)
        let goal = answers.goal ?? .regular
        let vma = estimateVMA(answers)
        let paces = Self.paces(vma: vma, goal: goal, targetSeconds: answers.targetSeconds)
        let beginner = (answers.experience ?? .starting) == .starting
        // Pain, a heart condition or a long break reported in the questionnaire: start gently.
        let cautious = answers.healthFlags.contains { ["pain", "heart", "break"].contains($0) }

        // Number of weeks: up to the race, capped at two months.
        var raceDay: Date?
        var weekCount = maxWeeks
        if answers.hasRace, !goal.isOpenEnded, let raceDate = answers.raceDate {
            let race = calendar.startOfDay(for: raceDate)
            let days = calendar.dateComponents([.day], from: weekStart, to: race).day ?? 0
            let needed = days / 7 + 1
            if days >= 0, needed <= maxWeeks {
                weekCount = max(1, needed)
                raceDay = race
            }
        }

        var runDays = Array(Set(answers.runDays.filter { (1...7).contains($0) })).sorted()
        if runDays.isEmpty { runDays = [2, 4, 7] }
        let longDay = answers.longRunDay.flatMap { runDays.contains($0) ? $0 : nil } ?? runDays.last ?? 7
        let otherDays = runDays.filter { $0 != longDay }
        let restDays = (1...7).filter { !runDays.contains($0) }

        var baseKm: Double
        switch answers.weeklyVolume ?? .under10 {
        case .notYet: baseKm = beginner ? 5 : 8
        case .under10: baseKm = beginner ? 6 : 10
        case .from10to25: baseKm = 18
        case .over25: baseKm = 32
        }
        if cautious { baseKm *= 0.8 }
        baseKm = max(baseKm, Double(runDays.count) * 2.5)

        let weeklyCap: Double
        let longCap: Double
        switch goal {
        case .fiveK: weeklyCap = 35; longCap = 10
        case .tenK: weeklyCap = 45; longCap = 14
        case .half: weeklyCap = 60; longCap = 19
        case .regular, .shape: weeklyCap = 40; longCap = 12
        }

        var weeks: [PlanWeek] = []
        for w in 0..<weekCount {
            let phase = Self.phase(week: w, of: weekCount, hasRace: raceDay != nil)
            var km = baseKm * pow(1.08, Double(w))
            if w % 4 == 3 { km *= 0.8 }
            if phase == .taper { km = baseKm * pow(1.08, Double(max(0, w - 1))) * 0.6 }
            km = min(km, weeklyCap)

            let monday = calendar.date(byAdding: .day, value: w * 7, to: weekStart) ?? weekStart
            func date(_ day: Int) -> Date { calendar.date(byAdding: .day, value: day - 1, to: monday) ?? monday }

            let qualityCount: Int
            if (beginner && phase == .base) || (cautious && w < 2) { qualityCount = 0 }
            else if otherDays.count >= 4 { qualityCount = 2 }
            else if otherDays.count >= 2 { qualityCount = 1 }
            else { qualityCount = 0 }

            var qualityDays: [Int] = []
            if qualityCount >= 1, let first = otherDays.first { qualityDays.append(first) }
            if qualityCount >= 2 { qualityDays.append(otherDays[otherDays.count / 2]) }
            let easyDays = otherDays.filter { !qualityDays.contains($0) }

            let longKm = runDays.count == 1 ? min(km, longCap) : min(km * 0.36, longCap)
            let qualityKm = km * 0.24
            let easyTotal = max(0, km - longKm - qualityKm * Double(qualityDays.count))
            let easyKm = easyDays.isEmpty ? 0 : max(2.5, easyTotal / Double(easyDays.count))

            var workouts: [Workout] = []
            for (i, day) in qualityDays.enumerated() {
                workouts.append(quality(date: date(day), week: w, phase: phase, goal: goal, paces: paces,
                                        budgetKm: qualityKm, variant: w + i))
            }
            for day in easyDays {
                workouts.append(easy(date: date(day), week: w, km: easyKm, paces: paces, strides: !beginner && w % 2 == 0))
            }
            workouts.append(long(date: date(longDay), week: w, km: longKm, paces: paces, phase: phase))

            // Strength and stretching, on rest days when there are some.
            let strengthDay = restDays.first ?? easyDays.first ?? longDay
            let stretchDay = restDays.dropFirst().first ?? longDay
            if phase != .taper {
                workouts.append(strength(date: date(strengthDay), week: w, phase: phase))
            }
            workouts.append(stretching(date: date(stretchDay), week: w, phase: phase))

            // Drop what is before today, and replace race-day sessions by the race itself.
            workouts = workouts.filter { $0.date >= today }
            if let raceDay {
                workouts = workouts.filter { $0.date < raceDay }
                if calendar.isDate(raceDay, equalTo: monday, toGranularity: .weekOfYear) || (raceDay >= monday && raceDay < (calendar.date(byAdding: .day, value: 7, to: monday) ?? monday)) {
                    workouts.append(race(date: raceDay, week: w, goal: goal, paces: paces, target: answers.targetSeconds))
                }
            }
            workouts.sort { $0.date < $1.date }
            weeks.append(PlanWeek(index: w, phase: phase, startDate: monday, workouts: workouts))
        }

        let current = predictedSeconds(vma: vma, goal: goal)
        var predicted = current
        if let distance = goal.distanceKm { predicted = Int(Double(paces.race) * distance) }

        return TrainingPlan(
            goal: goal,
            createdAt: start,
            startDate: weekStart,
            raceDate: raceDay ?? (answers.hasRace ? answers.raceDate : nil),
            targetSeconds: answers.targetSeconds,
            currentSeconds: current,
            predictedSeconds: predicted,
            vmaKmh: vma,
            paces: paces,
            weeks: weeks
        )
    }

    static func phase(week: Int, of count: Int, hasRace: Bool) -> PlanPhase {
        if hasRace && count >= 3 && week == count - 1 { return .taper }
        let usable = hasRace && count >= 3 ? count - 1 : count
        let position = Double(week) / Double(max(1, usable))
        if position < 0.38 { return .base }
        if position < 0.70 { return .build }
        return .specific
    }

    // MARK: Sessions

    private static func workout(date: Date, week: Int, kind: WorkoutKind, title: String, subtitle: String,
                                effort: Int, blocks: [WorkoutBlock], why: String, pace: Int?, fallbackPace: Int) -> Workout {
        var meters = 0.0
        var seconds = 0
        for block in blocks {
            for step in block.steps {
                meters += step.estimatedMeters(fallbackPace: fallbackPace) * Double(max(1, block.repeats))
                seconds += step.estimatedSeconds(fallbackPace: fallbackPace) * max(1, block.repeats)
            }
        }
        return Workout(date: date, weekIndex: week, kind: kind, title: title, subtitle: subtitle,
                       distanceKm: kind.isRun ? (meters / 100).rounded() / 10 : 0,
                       durationMin: max(1, Int((Double(seconds) / 60).rounded())),
                       targetPace: pace, effort: effort, blocks: blocks, why: why)
    }

    private static func easy(date: Date, week: Int, km: Double, paces: Paces, strides: Bool) -> Workout {
        let meters = Int((km * 10).rounded()) * 100
        let step = WorkoutStep(kind: .steady, label: L("Easy pace"), distanceM: meters,
                               paceFast: paces.easy - 15, paceSlow: paces.easy + 20)
        return workout(date: date, week: week, kind: .easy,
                       title: strides ? L("Easy run + strides") : L("Easy run"),
                       subtitle: L("You should be able to talk"),
                       effort: 3, blocks: [WorkoutBlock(steps: [step])],
                       why: L("Easy kilometres build your endurance without tiring you. They are what makes the hard sessions possible."),
                       pace: paces.easy, fallbackPace: paces.easy)
    }

    private static func long(date: Date, week: Int, km: Double, paces: Paces, phase: PlanPhase) -> Workout {
        let meters = Int((km * 10).rounded()) * 100
        let step = WorkoutStep(kind: .steady, label: L("Long run pace"), distanceM: meters,
                               paceFast: paces.long - 10, paceSlow: paces.long + 25)
        return workout(date: date, week: week, kind: .long, title: L("Long run"),
                       subtitle: L("Slow and steady"),
                       effort: phase == .specific ? 6 : 5, blocks: [WorkoutBlock(steps: [step])],
                       why: L("The long run teaches your body to last. Keep it slow: the distance does the work, not the speed."),
                       pace: paces.long, fallbackPace: paces.long)
    }

    private static func quality(date: Date, week: Int, phase: PlanPhase, goal: GoalType, paces: Paces,
                                budgetKm: Double, variant: Int) -> Workout {
        let warmup = WorkoutBlock(steps: [WorkoutStep(kind: .warmup, label: L("Easy pace"), durationSec: 15 * 60,
                                                      paceFast: paces.easy - 10, paceSlow: paces.easy + 30)])
        let cooldown = WorkoutBlock(steps: [WorkoutStep(kind: .cooldown, label: L("Free pace"), durationSec: 10 * 60)])
        // Kilometres left for the main set once warm-up and cool-down are counted.
        let mainKm = max(1.5, budgetKm - 25.0 * 60 / Double(paces.easy))

        func reps(distanceM: Int, recoveryM: Int, nominal: Int, pace: Int, spread: Int) -> (WorkoutBlock, Int) {
            let perRep = Double(distanceM + recoveryM) / 1000
            let count = min(nominal, max(3, Int((mainKm / perRep).rounded())))
            let block = WorkoutBlock(repeats: count, steps: [
                WorkoutStep(kind: .work, label: "\(distanceM) m", distanceM: distanceM, paceFast: pace - spread, paceSlow: pace + spread),
                WorkoutStep(kind: .recovery, label: L("Very slow jog"), distanceM: recoveryM),
            ])
            return (block, count)
        }

        func format(_ meters: Int) -> String { meters >= 1000 ? "\(meters / 1000) km" : "\(meters) m" }

        switch phase {
        case .base:
            let distance = variant % 2 == 0 ? 400 : 300
            let (block, count) = reps(distanceM: distance, recoveryM: 200, nominal: 10, pace: paces.interval, spread: 5)
            return workout(date: date, week: week, kind: .intervals, title: "\(count) × \(format(distance))",
                           subtitle: L("Short and fast"), effort: 7, blocks: [warmup, block, cooldown],
                           why: L("Short repetitions raise your top speed. Everything slower will feel easier afterwards."),
                           pace: paces.interval, fallbackPace: paces.easy)
        case .build:
            if variant % 2 == 0 {
                let (block, count) = reps(distanceM: 800, recoveryM: 400, nominal: 6, pace: paces.interval + 8, spread: 5)
                return workout(date: date, week: week, kind: .intervals, title: "\(count) × 800 m",
                               subtitle: L("Controlled speed"), effort: 7, blocks: [warmup, block, cooldown],
                               why: L("The 800s develop your VMA. This is what will make your goal pace feel comfortable."),
                               pace: paces.interval + 8, fallbackPace: paces.easy)
            }
            let count = min(3, max(2, Int((mainKm / 2.2).rounded())))
            let block = WorkoutBlock(repeats: count, steps: [
                WorkoutStep(kind: .work, label: L("Tempo"), durationSec: 8 * 60, paceFast: paces.tempo - 5, paceSlow: paces.tempo + 8),
                WorkoutStep(kind: .recovery, label: L("Very slow jog"), durationSec: 2 * 60),
            ])
            return workout(date: date, week: week, kind: .tempo, title: L("%@ × 8 min tempo", count),
                           subtitle: L("Comfortably hard"), effort: 6, blocks: [warmup, block, cooldown],
                           why: L("Tempo running teaches you to hold a strong pace for a long time without blowing up."),
                           pace: paces.tempo, fallbackPace: paces.easy)
        case .specific:
            let distance: Int
            let nominal: Int
            switch goal {
            case .fiveK: distance = 1000; nominal = 5
            case .tenK: distance = 2000; nominal = 4
            case .half: distance = 3000; nominal = 3
            case .regular, .shape: distance = 1000; nominal = 4
            }
            let (block, count) = reps(distanceM: distance, recoveryM: 400, nominal: nominal, pace: paces.race, spread: 5)
            return workout(date: date, week: week, kind: .racePace, title: L("%@ × %@ at race pace", count, format(distance)),
                           subtitle: L("Learn the pace of the day"), effort: 7, blocks: [warmup, block, cooldown],
                           why: L("Running at your goal pace now means no surprises on race day: your legs will know it by heart."),
                           pace: paces.race, fallbackPace: paces.easy)
        case .taper:
            let block = WorkoutBlock(repeats: 4, steps: [
                WorkoutStep(kind: .work, label: "400 m", distanceM: 400, paceFast: paces.race - 5, paceSlow: paces.race + 5),
                WorkoutStep(kind: .recovery, label: L("Very slow jog"), distanceM: 200),
            ])
            return workout(date: date, week: week, kind: .racePace, title: L("4 × 400 m at race pace"),
                           subtitle: L("A reminder, nothing more"), effort: 5, blocks: [warmup, block, cooldown],
                           why: L("A short reminder of race pace to stay sharp while your body recovers."),
                           pace: paces.race, fallbackPace: paces.easy)
        }
    }

    /// Strength and stretching are built from `ExerciseCatalog`, with a light progression by phase.
    /// One step per exercise: its label carries the sets and reps, its duration includes the rests.
    private static func strength(date: Date, week: Int, phase: PlanPhase) -> Workout {
        let exercises = GuidedPrescription.strengthSession(phase: phase)
        let steps = exercises.map { $0.workoutStep }
        let minutes = GuidedPrescription.totalMinutes(exercises)
        return workout(date: date, week: week, kind: .strength, title: L("Strength"), subtitle: L("Bodyweight, %@ min", minutes),
                       effort: 4, blocks: [WorkoutBlock(steps: steps)],
                       why: L("Stronger legs and core help you hold your form and keep your stride efficient when you tire."),
                       pace: nil, fallbackPace: 360)
    }

    private static func stretching(date: Date, week: Int, phase: PlanPhase) -> Workout {
        let exercises = GuidedPrescription.stretchingSession(phase: phase)
        let steps = exercises.map { $0.workoutStep }
        let minutes = GuidedPrescription.totalMinutes(exercises)
        return workout(date: date, week: week, kind: .stretching, title: L("Stretching"), subtitle: L("Gentle, %@ min", minutes),
                       effort: 1, blocks: [WorkoutBlock(steps: steps)],
                       why: L("A few gentle minutes help you unwind after your runs and keep your range of motion."),
                       pace: nil, fallbackPace: 360)
    }

    private static func race(date: Date, week: Int, goal: GoalType, paces: Paces, target: Int?) -> Workout {
        let meters = Int(((goal.distanceKm ?? 5) * 1000).rounded())
        let step = WorkoutStep(kind: .work, label: goal.title, distanceM: meters,
                               paceFast: paces.race - 5, paceSlow: paces.race + 5)
        return workout(date: date, week: week, kind: .race, title: goal.title,
                       subtitle: L("This is your day"), effort: 9, blocks: [WorkoutBlock(steps: [step])],
                       why: L("Everything you did leads here. Start calm, hold your pace, and finish strong."),
                       pace: paces.race, fallbackPace: paces.race)
    }
}
