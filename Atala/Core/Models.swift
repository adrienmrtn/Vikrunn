import Foundation
import CoreLocation

// MARK: - Onboarding answers

enum Gender: String, Codable, CaseIterable, Identifiable {
    case woman, man, other, undisclosed
    var id: String { rawValue }
    var title: String {
        switch self {
        case .woman: return "Woman"
        case .man: return "Man"
        case .other: return "Non-binary"
        case .undisclosed: return "Prefer not to say"
        }
    }
}

enum GoalType: String, Codable, CaseIterable, Identifiable {
    // `shape` was added later: the raw value is the case name, so saved answers still decode.
    case fiveK, tenK, half, regular, shape
    var id: String { rawValue }
    var title: String {
        switch self {
        case .fiveK: return "5K"
        case .tenK: return "10K"
        case .half: return "Half marathon"
        case .regular: return "Run regularly"
        case .shape: return "Get in shape"
        }
    }
    var subtitle: String {
        switch self {
        case .fiveK: return "Your first distance"
        case .tenK: return "The great classic"
        case .half: return "21.1 km"
        case .regular: return "A steady habit, no race planned"
        case .shape: return "I just want to feel fit and strong"
        }
    }
    /// Race distance in kilometres, nil for the goals without a race.
    var distanceKm: Double? {
        switch self {
        case .fiveK: return 5
        case .tenK: return 10
        case .half: return 21.0975
        case .regular, .shape: return nil
        }
    }
    var vignette: String {
        switch self {
        case .fiveK: return "vignette_goal_5k"
        case .tenK: return "vignette_goal_10k"
        case .half: return "vignette_goal_half"
        case .regular: return "vignette_goal_regular"
        case .shape: return "vignette_why_athletic"
        }
    }
    /// True for the goals with no race distance, date or target time ("run regularly", "get in shape").
    var isOpenEnded: Bool {
        switch self {
        case .regular, .shape: return true
        case .fiveK, .tenK, .half: return false
        }
    }
}

extension Optional where Wrapped == GoalType {
    /// False while no goal is chosen.
    var isOpenEnded: Bool { self?.isOpenEnded ?? false }
}

enum Experience: String, Codable, CaseIterable, Identifiable {
    case starting, underSixMonths, sixMonthsToTwoYears, overTwoYears
    var id: String { rawValue }
    var title: String {
        switch self {
        case .starting: return "I'm just starting"
        case .underSixMonths: return "Less than 6 months"
        case .sixMonthsToTwoYears: return "6 months to 2 years"
        case .overTwoYears: return "More than 2 years"
        }
    }
    var vignette: String {
        switch self {
        case .starting: return "vignette_exp_1"
        case .underSixMonths: return "vignette_exp_2"
        case .sixMonthsToTwoYears: return "vignette_exp_3"
        case .overTwoYears: return "vignette_exp_4"
        }
    }
}

enum WeeklyVolume: String, Codable, CaseIterable, Identifiable {
    // `notYet` was added later: the raw value is the case name, so saved answers still decode.
    case notYet, under10, from10to25, over25
    var id: String { rawValue }
    var title: String {
        switch self {
        case .notYet: return "I don't run yet"
        case .under10: return "Less than 10 km"
        case .from10to25: return "10 to 25 km"
        case .over25: return "More than 25 km"
        }
    }
    var vignette: String {
        switch self {
        case .notYet: return "vignette_exp_1"
        case .under10: return "vignette_volume_1"
        case .from10to25: return "vignette_volume_2"
        case .over25: return "vignette_volume_3"
        }
    }
}

enum Motivation: String, Codable, CaseIterable, Identifiable {
    // `athletic` was added later and is listed first: the raw value is the case name, so answers
    // saved by earlier versions still decode.
    case athletic, prove, record, health, clearHead, together
    var id: String { rawValue }
    var title: String {
        switch self {
        case .athletic: return "Get an athletic body"
        case .prove: return "Prove to myself I can do it"
        case .record: return "Beat my record"
        case .health: return "Take care of my health"
        case .clearHead: return "Clear my head"
        case .together: return "Run with people I love"
        }
    }
    var subtitle: String {
        switch self {
        case .athletic: return "Look and feel stronger"
        case .prove: return "See something through"
        case .record: return "Do better than last time"
        case .health: return "Move more, feel better"
        case .clearHead: return "Switch off from the day"
        case .together: return "Share the effort"
        }
    }
    /// The coach's adaptive answer shown right after the "why" question.
    var coachReply: String {
        switch self {
        case .athletic: return "I'll get you there, one session at a time."
        case .prove: return "I'll help you prove it to yourself."
        case .record: return "We'll go and get that record together."
        case .health: return "We'll build a habit your body thanks you for."
        case .clearHead: return "I'll make every run a breath of fresh air."
        case .together: return "I'll get you ready to enjoy every shared kilometre."
        }
    }
    var vignette: String {
        switch self {
        case .athletic: return "vignette_why_athletic"
        case .prove: return "vignette_why_prove"
        case .record: return "vignette_why_record"
        case .health: return "vignette_why_health"
        case .clearHead: return "vignette_why_clear_head"
        case .together: return "vignette_why_together"
        }
    }
}

enum Constraint: String, Codable, CaseIterable, Identifiable {
    case pastInjury, travel, treadmill, strength, irregularSleep
    var id: String { rawValue }
    var title: String {
        switch self {
        case .pastInjury: return "A past injury"
        case .travel: return "I travel often"
        case .treadmill: return "I sometimes run on a treadmill"
        case .strength: return "I already do strength training"
        case .irregularSleep: return "Irregular hours or sleep"
        }
    }
    var vignette: String {
        switch self {
        case .pastInjury: return "vignette_constraint_injury"
        case .travel: return "vignette_constraint_travel"
        case .treadmill: return "vignette_constraint_treadmill"
        case .strength: return "vignette_constraint_strength"
        case .irregularSleep: return "vignette_constraint_sleep"
        }
    }
}

enum CoachTone: String, Codable, CaseIterable, Identifiable {
    case kind, direct, playful
    var id: String { rawValue }
    var title: String {
        switch self {
        case .kind: return "Kind"
        case .direct: return "Direct"
        case .playful: return "Playful"
        }
    }
    var example: String {
        switch self {
        case .kind: return "Nice work today. Rest well, you earned it."
        case .direct: return "Session done. Tomorrow: easy run, no excuses."
        case .playful: return "Boom. Those 800s never saw you coming."
        }
    }
}

struct RecentResult: Codable, Equatable {
    var distanceKm: Double
    var seconds: Int
}

/// Everything the questionnaire collects. Stored on the phone from the first answer.
struct OnboardingAnswers: Codable, Equatable {
    var firstName: String = ""
    var coachID: Int = 1
    var gender: Gender?
    var experience: Experience?
    var weeklyVolume: WeeklyVolume?
    var recentResult: RecentResult?
    var healthFlags: [String] = []
    var goal: GoalType?
    var hasRace: Bool = false
    var raceDate: Date?
    var targetSeconds: Int?
    var motivation: Motivation?
    /// Days the user can run, 1 = Monday ... 7 = Sunday. One ticked day = one session.
    var runDays: [Int] = []
    var longRunDay: Int?
    var constraints: [Constraint] = []
    var tone: CoachTone = .kind
}

// MARK: - Plan

enum WorkoutKind: String, Codable {
    case easy, intervals, tempo, long, racePace, strength, stretching, race, free

    var title: String {
        switch self {
        case .easy: return "Easy run"
        case .intervals: return "Intervals"
        case .tempo: return "Tempo"
        case .long: return "Long run"
        case .racePace: return "Race pace"
        case .strength: return "Strength"
        case .stretching: return "Stretching"
        case .race: return "Race day"
        case .free: return "Free run"
        }
    }
    var symbol: String {
        switch self {
        case .easy: return "figure.run"
        case .intervals: return "bolt.fill"
        case .tempo: return "speedometer"
        case .long: return "road.lanes"
        case .racePace: return "flag.fill"
        case .strength: return "dumbbell.fill"
        case .stretching: return "figure.cooldown"
        case .race: return "flag.checkered"
        case .free: return "plus"
        }
    }
    /// True for sessions that are run (and can be tracked with GPS).
    var isRun: Bool { self != .strength && self != .stretching }
    /// True for the hard sessions shown in orange.
    var isQuality: Bool { self == .intervals || self == .tempo || self == .racePace || self == .race }
}

enum StepKind: String, Codable {
    case warmup, work, recovery, cooldown, steady, exercise
    var title: String {
        switch self {
        case .warmup: return "Warm-up"
        case .work: return "Fast"
        case .recovery: return "Recovery"
        case .cooldown: return "Cool-down"
        case .steady: return "Steady"
        case .exercise: return "Exercise"
        }
    }
}

/// One step of a session. Either time-based or distance-based.
struct WorkoutStep: Codable, Identifiable, Equatable {
    var id = UUID()
    var kind: StepKind
    var label: String
    var durationSec: Int?
    var distanceM: Int?
    /// Target pace range in seconds per kilometre (fast bound first). Nil = free pace.
    var paceFast: Int?
    var paceSlow: Int?
    /// Identifier of the guided exercise (see `ExerciseCatalog`). Nil for running steps and older plans.
    var exerciseID: String?

    /// Estimated distance in metres.
    func estimatedMeters(fallbackPace: Int) -> Double {
        if let distanceM { return Double(distanceM) }
        guard let durationSec else { return 0 }
        let pace = paceSlow ?? paceFast ?? fallbackPace
        return pace > 0 ? Double(durationSec) / Double(pace) * 1000 : 0
    }

    /// Estimated duration in seconds.
    func estimatedSeconds(fallbackPace: Int) -> Int {
        if let durationSec { return durationSec }
        guard let distanceM else { return 0 }
        let pace = paceSlow ?? paceFast ?? fallbackPace
        return Int(Double(distanceM) / 1000 * Double(pace))
    }
}

/// A group of steps repeated `repeats` times (1 for warm-up, cool-down, steady runs).
struct WorkoutBlock: Codable, Identifiable, Equatable {
    var id = UUID()
    var repeats: Int = 1
    var steps: [WorkoutStep]
}

enum WorkoutStatus: String, Codable {
    case planned, done, missed
}

struct Workout: Codable, Identifiable, Equatable {
    var id = UUID()
    var date: Date
    var weekIndex: Int
    var kind: WorkoutKind
    var title: String
    var subtitle: String
    var distanceKm: Double
    var durationMin: Int
    var targetPace: Int?
    /// Perceived effort, 1 to 10.
    var effort: Int
    var blocks: [WorkoutBlock]
    var why: String
    var status: WorkoutStatus = .planned
    var runID: UUID?
    /// How a guided strength or stretching session felt. Runs keep their feeling on the run record.
    var feeling: Feeling?

    /// Blocks expanded into the flat list of steps followed during the run.
    var flatSteps: [WorkoutStep] {
        var result: [WorkoutStep] = []
        for block in blocks {
            for _ in 0..<max(1, block.repeats) {
                for step in block.steps {
                    var copy = step
                    copy.id = UUID()
                    result.append(copy)
                }
            }
        }
        return result
    }
}

enum PlanPhase: String, Codable {
    case base, build, specific, taper
    var title: String {
        switch self {
        case .base: return "Base"
        case .build: return "Build"
        case .specific: return "Specific"
        case .taper: return "Taper"
        }
    }
    var summary: String {
        switch self {
        case .base: return "Settle the habit, run easy"
        case .build: return "More volume, first hard efforts"
        case .specific: return "Train at your goal pace"
        case .taper: return "Ease off, fresh legs"
        }
    }
}

struct PlanWeek: Codable, Identifiable, Equatable {
    var id = UUID()
    var index: Int
    var phase: PlanPhase
    var startDate: Date
    var workouts: [Workout]

    var plannedKm: Double { workouts.filter { $0.kind.isRun }.reduce(0) { $0 + $1.distanceKm } }
    var runCount: Int { workouts.filter { $0.kind.isRun }.count }
}

/// Training paces in seconds per kilometre.
struct Paces: Codable, Equatable {
    var easy: Int
    var long: Int
    var tempo: Int
    var interval: Int
    var race: Int
}

struct TrainingPlan: Codable, Identifiable, Equatable {
    var id = UUID()
    var goal: GoalType
    var createdAt: Date
    var startDate: Date
    var raceDate: Date?
    var targetSeconds: Int?
    /// What the user is worth today on the goal distance.
    var currentSeconds: Int?
    /// What the plan aims for on race day.
    var predictedSeconds: Int?
    var vmaKmh: Double
    var paces: Paces
    var weeks: [PlanWeek]

    var allWorkouts: [Workout] { weeks.flatMap { $0.workouts }.sorted { $0.date < $1.date } }
    var peakKm: Double { weeks.map { $0.plannedKm }.max() ?? 0 }
    var totalRuns: Int { weeks.reduce(0) { $0 + $1.runCount } }
}

// MARK: - Runs

enum RunSource: String, Codable {
    case gps, manual, health
}

enum Feeling: String, Codable, CaseIterable, Identifiable {
    case easy, good, hard, pain
    var id: String { rawValue }
    var title: String {
        switch self {
        case .easy: return "Easy"
        case .good: return "Hard but good"
        case .hard: return "Very hard"
        case .pain: return "Something hurts"
        }
    }
    var subtitle: String {
        switch self {
        case .easy: return "I had more in the tank"
        case .good: return "The planned effort, no more, no less"
        case .hard: return "I finished at my limit"
        case .pain: return "I'd rather tell my coach"
        }
    }
}

struct RoutePoint: Codable, Equatable {
    var lat: Double
    var lon: Double
    var time: Date
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lon) }
}

/// One step as it was actually run (used for the bars in the summary).
struct RunSplit: Codable, Identifiable, Equatable {
    var id = UUID()
    var label: String
    var kind: StepKind
    var distanceM: Double
    var durationSec: Int
    var paceFast: Int?
    var paceSlow: Int?
    var pace: Int { distanceM > 0 ? Int(Double(durationSec) / (distanceM / 1000)) : 0 }
    var onTarget: Bool {
        guard let paceFast, let paceSlow else { return true }
        return pace >= paceFast - 5 && pace <= paceSlow + 5
    }
}

struct RunRecord: Codable, Identifiable, Equatable {
    var id = UUID()
    var workoutID: UUID?
    var title: String
    var date: Date
    var source: RunSource
    var distanceM: Double
    var durationSec: Int
    var avgHeartRate: Int?
    var route: [RoutePoint] = []
    var splits: [RunSplit] = []
    var feeling: Feeling?

    var distanceKm: Double { distanceM / 1000 }
    var avgPace: Int { distanceM > 0 ? Int(Double(durationSec) / (distanceM / 1000)) : 0 }
}

// MARK: - Coach chat

enum ChatRole: String, Codable {
    case user, coach
}

/// A single change the coach proposes on the plan. Applied only after the user accepts.
struct PlanChange: Codable, Identifiable, Equatable {
    var id = UUID()
    var workoutID: UUID
    var newDate: Date?
    var newKind: WorkoutKind?
    var newTitle: String?
    var newDistanceKm: Double?
    var newDurationMin: Int?
    var remove: Bool = false
    /// Snapshot of the session taken when the change is applied, so the chat can still show what changed.
    var previousDate: Date?
    var previousDetail: String?
}

enum ProposalStatus: String, Codable {
    case pending, applied, declined
}

struct PlanProposal: Codable, Identifiable, Equatable {
    var id = UUID()
    var summary: String
    var changes: [PlanChange]
    var status: ProposalStatus = .pending
}

struct ChatMessage: Codable, Identifiable, Equatable {
    var id = UUID()
    var role: ChatRole
    var text: String
    var date: Date = Date()
    var proposal: PlanProposal?
}

// MARK: - Settings and account

struct VoiceSettings: Codable, Equatable {
    var enabled = true
    var announceSteps = true
    var announcePace = true
    var announceEveryKm = true
}

struct AppSettings: Codable, Equatable {
    var voice = VoiceSettings()
    var healthImportEnabled = false
}

struct Account: Codable, Equatable {
    var userID: String
    var email: String?
    var isGuest: Bool
    var accessToken: String?
    var refreshToken: String?
    var expiresAt: Date?
}

// MARK: - Calendar helpers

extension Calendar {
    /// Gregorian calendar with weeks starting on Monday.
    static let plan: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 2
        calendar.locale = Locale(identifier: "en_US")
        return calendar
    }()

    /// 1 = Monday ... 7 = Sunday.
    func mondayIndex(of date: Date) -> Int {
        (component(.weekday, from: date) + 5) % 7 + 1
    }

    func mondayOfWeek(containing date: Date) -> Date {
        let day = startOfDay(for: date)
        return self.date(byAdding: .day, value: -(mondayIndex(of: day) - 1), to: day) ?? day
    }
}

enum Weekday {
    static let short = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    static let long = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
    static func short(_ index: Int) -> String { short[min(max(index, 1), 7) - 1] }
    static func long(_ index: Int) -> String { long[min(max(index, 1), 7) - 1] }
}
