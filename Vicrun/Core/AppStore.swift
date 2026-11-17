import Foundation
import SwiftUI

/// Single source of truth of the app. Everything is saved on the phone as one JSON file
/// (the session tokens go to the Keychain), and mirrored to the account when one is signed in.
@MainActor
final class AppStore: ObservableObject {
    @Published var answers: OnboardingAnswers {
        didSet {
            scheduleSave()
            if answers != oldValue { syncNoteChange(profile: true) }
        }
    }
    /// Index of the onboarding screen the user reached, so they can resume where they left.
    @Published var onboardingStep: Int { didSet { scheduleSave() } }
    @Published var hasCompletedOnboarding: Bool { didSet { scheduleSave() } }
    @Published var plan: TrainingPlan? {
        didSet {
            scheduleSave()
            if plan != oldValue { syncNoteChange(profile: false) }
        }
    }
    @Published var runs: [RunRecord] {
        didSet {
            scheduleSave()
            syncNoteRunsChange(from: oldValue)
        }
    }
    @Published var messages: [ChatMessage] { didSet { scheduleSave() } }
    @Published var settings: AppSettings {
        didSet {
            scheduleSave()
            if settings != oldValue { syncNoteChange(profile: true) }
        }
    }
    @Published var account: Account? {
        didSet {
            scheduleSave()
            persistSession(replacing: oldValue)
        }
    }

    // MARK: Sync state (see SyncServiceStore.swift)

    /// What still has to be sent to the account, and what was last seen there. Saved with the rest.
    var syncState: SyncState { didSet { scheduleSave() } }
    /// Last time the phone and the account were in step.
    @Published var lastSyncedAt: Date? { didSet { scheduleSave() } }
    /// What the sync is doing right now (not saved).
    @Published var syncActivity: SyncActivity = .idle
    /// True while the user must choose between the phone's data and the account's (not saved).
    @Published var syncChoicePending = false
    /// Set while server data is being written into the store, so it is not sent straight back.
    var syncIsApplyingRemote = false
    var syncTask: Task<Void, Never>?

    private struct Snapshot: Codable {
        var answers = OnboardingAnswers()
        var onboardingStep = 0
        var hasCompletedOnboarding = false
        var plan: TrainingPlan?
        var runs: [RunRecord] = []
        var messages: [ChatMessage] = []
        var settings = AppSettings()
        var account: Account?
        // Added later: optional, so files saved by earlier versions still load.
        var syncState: SyncState?
        var lastSyncedAt: Date?
    }

    private let fileURL: URL?
    private var saveTask: Task<Void, Never>?

    /// False for previews and tests: nothing is written to disk, to the Keychain or to the server.
    var isPersistent: Bool { fileURL != nil }

    init(inMemory: Bool = false) {
        var url: URL?
        if !inMemory {
            let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            if let folder {
                try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                url = folder.appendingPathComponent("vicrun.json")
            }
        }
        fileURL = url
        var snapshot = Snapshot()
        if let url, let data = try? Data(contentsOf: url) {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            if let loaded = try? decoder.decode(Snapshot.self, from: data) { snapshot = loaded }
        }

        // Session tokens live in the Keychain. Files saved by earlier versions still hold them: move them.
        var loadedAccount = snapshot.account
        var tokensToMigrate = false
        if url != nil {
            if var real = loadedAccount, !real.isGuest {
                if let legacy = real.keychainSession {
                    AuthKeychain.save(legacy)
                    tokensToMigrate = true
                } else if let stored = AuthKeychain.load(), stored.userID == real.userID {
                    real.accessToken = stored.accessToken
                    real.refreshToken = stored.refreshToken
                    real.expiresAt = stored.expiresAt
                    loadedAccount = real
                }
            } else {
                // No account on file (first launch after a reinstall, guest): drop any leftover session.
                AuthKeychain.clear()
            }
        }

        answers = snapshot.answers
        onboardingStep = snapshot.onboardingStep
        hasCompletedOnboarding = snapshot.hasCompletedOnboarding
        plan = snapshot.plan
        runs = snapshot.runs
        messages = snapshot.messages
        settings = snapshot.settings
        account = loadedAccount
        syncState = snapshot.syncState ?? SyncState()
        lastSyncedAt = snapshot.lastSyncedAt
        if tokensToMigrate { save() }
    }

    // MARK: Persistence

    private func scheduleSave() {
        saveTask?.cancel()
        saveTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 400_000_000)
            guard !Task.isCancelled else { return }
            self?.save()
        }
    }

    func save() {
        guard let fileURL else { return }
        let snapshot = Snapshot(answers: answers, onboardingStep: onboardingStep,
                                hasCompletedOnboarding: hasCompletedOnboarding, plan: plan, runs: runs,
                                messages: messages, settings: settings, account: account?.withoutTokens,
                                syncState: syncState, lastSyncedAt: lastSyncedAt)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(snapshot) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }

    /// Keeps the Keychain in step with the signed-in account.
    private func persistSession(replacing old: Account?) {
        guard isPersistent else { return }
        let session = account?.keychainSession
        guard session != old?.keychainSession else { return }
        if let session {
            AuthKeychain.save(session)
        } else {
            AuthKeychain.clear()
        }
    }

    // MARK: Coach and profile

    var coach: Coach { Coach.by(id: answers.coachID) }
    var firstName: String { answers.firstName.isEmpty ? "Runner" : answers.firstName }

    // MARK: Plan

    /// Builds (or rebuilds) the plan from the current answers.
    func generatePlan() {
        plan = PlanEngine.generate(answers: answers)
    }

    func finishOnboarding() {
        if plan == nil { generatePlan() }
        hasCompletedOnboarding = true
        syncSchedulePush(after: 0.5)
    }

    var allWorkouts: [Workout] { plan?.allWorkouts ?? [] }

    func workout(id: UUID) -> Workout? { allWorkouts.first { $0.id == id } }

    func workouts(on day: Date) -> [Workout] {
        allWorkouts.filter { Calendar.plan.isDate($0.date, inSameDayAs: day) }
    }

    var todayWorkouts: [Workout] { workouts(on: Date()) }

    /// The next planned run from today on.
    var nextRun: Workout? {
        let today = Calendar.plan.startOfDay(for: Date())
        return allWorkouts.first { $0.kind.isRun && $0.status == .planned && $0.date >= today }
    }

    var currentWeek: PlanWeek? {
        guard let plan else { return nil }
        let monday = Calendar.plan.mondayOfWeek(containing: Date())
        return plan.weeks.first { Calendar.plan.isDate($0.startDate, inSameDayAs: monday) } ?? plan.weeks.first
    }

    func updateWorkout(_ id: UUID, _ change: (inout Workout) -> Void) {
        guard var plan else { return }
        for w in plan.weeks.indices {
            if let i = plan.weeks[w].workouts.firstIndex(where: { $0.id == id }) {
                change(&plan.weeks[w].workouts[i])
                plan.weeks[w].workouts.sort { $0.date < $1.date }
                self.plan = plan
                return
            }
        }
    }

    func setStatus(_ status: WorkoutStatus, for id: UUID) {
        updateWorkout(id) { $0.status = status }
    }

    /// Sessions planned before today and never done become "missed". The plan is not recalculated.
    func refreshMissed() {
        guard var plan else { return }
        let today = Calendar.plan.startOfDay(for: Date())
        var changed = false
        for w in plan.weeks.indices {
            for i in plan.weeks[w].workouts.indices
            where plan.weeks[w].workouts[i].status == .planned && plan.weeks[w].workouts[i].date < today {
                plan.weeks[w].workouts[i].status = .missed
                changed = true
            }
        }
        // Derived from the date, the same on every phone: not a change to send to the account
        // (it would otherwise look more recent than a real edit made on another phone).
        if changed {
            syncApplyRemote { self.plan = plan }
        }
    }

    // MARK: Runs

    /// Saves a run and marks its session as done when it belongs to the plan.
    func addRun(_ run: RunRecord) {
        runs.removeAll { $0.id == run.id }
        runs.append(run)
        runs.sort { $0.date > $1.date }
        if let workoutID = run.workoutID {
            updateWorkout(workoutID) {
                $0.status = .done
                $0.runID = run.id
            }
        }
    }

    func updateRun(_ id: UUID, _ change: (inout RunRecord) -> Void) {
        guard let i = runs.firstIndex(where: { $0.id == id }) else { return }
        var run = runs[i]
        change(&run)
        runs[i] = run
    }

    func deleteRun(_ id: UUID) {
        if let run = runs.first(where: { $0.id == id }), let workoutID = run.workoutID {
            updateWorkout(workoutID) {
                $0.runID = nil
                $0.status = .planned
            }
        }
        runs.removeAll { $0.id == id }
        refreshMissed()
    }

    func run(id: UUID?) -> RunRecord? {
        guard let id else { return nil }
        return runs.first { $0.id == id }
    }

    var totalKm: Double { runs.reduce(0) { $0 + $1.distanceKm } }

    // MARK: Coach proposals

    /// Applies a change proposed by the coach, after the user accepted it.
    func apply(proposalIn messageID: UUID) {
        guard let m = messages.firstIndex(where: { $0.id == messageID }),
              var proposal = messages[m].proposal, proposal.status == .pending else { return }
        for change in proposal.changes {
            if change.remove {
                removeWorkout(change.workoutID)
                continue
            }
            updateWorkout(change.workoutID) { workout in
                if let date = change.newDate {
                    workout.date = Calendar.plan.startOfDay(for: date)
                    if workout.status == .missed { workout.status = .planned }
                }
                if let kind = change.newKind { workout.kind = kind }
                if let title = change.newTitle { workout.title = title }
                if let km = change.newDistanceKm { workout.distanceKm = km }
                if let minutes = change.newDurationMin { workout.durationMin = minutes }
            }
        }
        proposal.status = .applied
        messages[m].proposal = proposal
    }

    func decline(proposalIn messageID: UUID) {
        guard let m = messages.firstIndex(where: { $0.id == messageID }),
              var proposal = messages[m].proposal, proposal.status == .pending else { return }
        proposal.status = .declined
        messages[m].proposal = proposal
    }

    private func removeWorkout(_ id: UUID) {
        guard var plan else { return }
        for w in plan.weeks.indices { plan.weeks[w].workouts.removeAll { $0.id == id } }
        self.plan = plan
    }

    // MARK: Account

    /// Forgets the account only. `AuthService.signOut(store:)` is what the app uses: it also clears the phone.
    func signOut() {
        account = nil
        syncState = SyncState()
        lastSyncedAt = nil
        syncChoicePending = false
    }

    /// Erases everything stored on the phone and returns to the first screen.
    func eraseEverything() {
        syncTask?.cancel()
        syncTask = nil
        syncIsApplyingRemote = true
        defer { syncIsApplyingRemote = false }
        syncState = SyncState()
        lastSyncedAt = nil
        syncChoicePending = false
        syncActivity = .idle
        answers = OnboardingAnswers()
        onboardingStep = 0
        plan = nil
        runs = []
        messages = []
        settings = AppSettings()
        account = nil
        hasCompletedOnboarding = false
        save()
    }

    // MARK: Sample data (previews and the simulated first version)

    static var preview: AppStore {
        let store = AppStore(inMemory: true)
        store.answers = SampleData.answers
        store.generatePlan()
        store.runs = SampleData.runs(for: store.plan)
        store.hasCompletedOnboarding = true
        return store
    }
}
