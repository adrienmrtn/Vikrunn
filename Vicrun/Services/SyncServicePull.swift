import Foundation

struct SyncPullResult {
    var outcome: SyncOutcome
    /// True when server data replaced or completed local data.
    var changed: Bool
}

/// Which side wins when the phone and the account both hold a profile or a plan.
enum SyncMergeBias {
    /// Regular sync: the most recent change wins.
    case lastWriteWins
    /// Right after sign-in, "Use my saved plan": the account wins.
    case account
    /// Right after sign-in, "Use this phone's plan": the phone wins.
    case phone
}

/// What to do when someone signs in on a phone that already holds data.
enum SyncSignInPolicy {
    /// Welcome screen and end of onboarding: the account's plan, when there is one, replaces the phone's.
    case preferAccount
    /// Profile: when both sides hold a plan or runs, the user chooses.
    case askIfBoth
}

enum SyncSignInOutcome: Equatable {
    /// The account's plan is now on the phone: open the app.
    case restored
    /// Nothing to restore (new account, or no plan saved yet): carry on; the phone's data is uploaded.
    case continued
    /// Both sides hold data: `store.syncChoicePending` is set and the user must choose.
    case needsChoice
    case failed(SyncFailure)
}

extension SyncService {
    // MARK: Regular pull

    static func runPull(store: AppStore) async -> SyncPullResult {
        guard store.isPersistent, let account = store.account, !account.isGuest,
              store.syncState.reconciled else {
            return SyncPullResult(outcome: .notSignedIn, changed: false)
        }
        let userID = account.userID
        do {
            let snapshot = try await fetchSnapshot(store: store, everything: false)
            guard store.account?.userID == userID, store.syncState.reconciled else {
                return SyncPullResult(outcome: .failed(.interrupted), changed: false)
            }
            let changed = merge(snapshot, into: store, bias: .lastWriteWins)
            store.lastSyncedAt = Date()
            if changed { store.refreshMissed() }
            if store.messages.isEmpty { await CoachService.restoreHistory(store: store) }
            return SyncPullResult(outcome: .done, changed: changed)
        } catch let failure as SyncFailure {
            return SyncPullResult(outcome: .failed(failure), changed: false)
        } catch {
            return SyncPullResult(outcome: .failed(.offline), changed: false)
        }
    }

    // MARK: Merge (no network: decides and applies in one go)

    private static func takesRemote(dirtyAt: Date?, stamp: String, bias: SyncMergeBias) -> Bool {
        switch bias {
        case .account: return true
        case .phone: return false
        case .lastWriteWins:
            guard let dirtyAt else { return true }
            guard let remoteDate = SyncCoding.date(fromServer: stamp) else { return false }
            return remoteDate > dirtyAt
        }
    }

    /// Applies a snapshot of the account to the store. Returns true when local data changed.
    @discardableResult
    static func merge(_ remote: SyncRemoteSnapshot, into store: AppStore, bias: SyncMergeBias) -> Bool {
        var state = store.syncState
        let now = Date()
        var newAnswers: OnboardingAnswers?
        var newSettings: AppSettings?
        var newPlan: TrainingPlan?

        // Profile
        if let stamp = remote.profileStamp {
            if stamp != state.profileStamp, let answers = remote.answers,
               takesRemote(dirtyAt: state.profileDirtyAt, stamp: stamp, bias: bias) {
                newAnswers = answers
                newSettings = remote.settings
                state.profileStamp = stamp
                state.profileDirtyAt = nil
            } else if bias == .phone, state.profileDirtyAt == nil {
                state.profileDirtyAt = now
            }
        } else if state.profileDirtyAt == nil {
            state.profileDirtyAt = now // nothing in the account yet: upload
        }

        // Plan
        if let stamp = remote.planStamp {
            if stamp != state.planStamp, let plan = remote.plan,
               takesRemote(dirtyAt: state.planDirtyAt, stamp: stamp, bias: bias) {
                newPlan = plan
                state.planStamp = stamp
                state.planDirtyAt = nil
            } else if bias == .phone, store.plan != nil, state.planDirtyAt == nil {
                state.planDirtyAt = now
            }
        } else if store.plan != nil, state.planDirtyAt == nil {
            state.planDirtyAt = now
        }

        // Runs: union by id; a tombstone removes the run everywhere.
        var byKey: [String: RunRecord] = [:]
        for run in store.runs { byKey[SyncState.key(run.id)] = run }
        var remoteKeys = Set<String>()
        var runsChanged = false
        for item in remote.runs {
            remoteKeys.insert(item.key)
            if item.deleted {
                if byKey[item.key] != nil {
                    byKey[item.key] = nil
                    runsChanged = true
                }
                state.runStamps[item.key] = item.stamp
                state.runsDirtyAt[item.key] = nil
                state.deletedRunIDs.removeAll { $0 == item.key }
                continue
            }
            if state.deletedRunIDs.contains(item.key) { continue } // deleted here, not sent yet
            let local = byKey[item.key]
            if local != nil, state.runStamps[item.key] == item.stamp { continue }
            guard let record = remote.runRecords[item.key] else { continue }
            if local != nil, let dirtyAt = state.runsDirtyAt[item.key],
               !takesRemote(dirtyAt: dirtyAt, stamp: item.stamp, bias: .lastWriteWins) {
                continue // edited here more recently: the push sends it
            }
            if local != record {
                byKey[item.key] = record
                runsChanged = true
            }
            state.runStamps[item.key] = item.stamp
            state.runsDirtyAt[item.key] = nil
        }
        // On the phone only (recorded offline, or as a guest before signing in): upload.
        for key in byKey.keys where !remoteKeys.contains(key) && state.runsDirtyAt[key] == nil {
            state.runsDirtyAt[key] = now
        }

        var changed = false
        store.syncApplyRemote {
            if let newAnswers, store.answers != newAnswers {
                store.answers = newAnswers
                changed = true
            }
            if let newSettings, store.settings != newSettings {
                store.settings = newSettings
                changed = true
            }
            if let newPlan, store.plan != newPlan {
                store.plan = newPlan
                changed = true
            }
            if runsChanged {
                store.runs = byKey.values.sorted { $0.date > $1.date }
                changed = true
            }
        }
        store.syncState = state
        return changed
    }

    // MARK: Sign-in

    /// Call right after a successful sign-in, instead of setting `store.account` directly.
    /// Looks at what the account holds, restores it or uploads the phone's data, and reports what
    /// happened. On failure the store is left exactly as it was (still signed out, or still a guest).
    static func completeSignIn(_ account: Account, store: AppStore,
                               policy: SyncSignInPolicy) async -> SyncSignInOutcome {
        await serialized { () -> SyncSignInOutcome in
            let previous = SyncPreviousSession(account: store.account, state: store.syncState,
                                               lastSyncedAt: store.lastSyncedAt)
            store.syncTask?.cancel()
            store.syncTask = nil
            store.account = account
            store.syncState = SyncState() // not matched yet: nothing is sent
            store.lastSyncedAt = nil
            return await reconcile(store: store, policy: policy, previous: previous)
        }
    }

    /// Answer to "Use this phone's plan" / "Use my saved plan".
    static func resolveChoice(keepPhone: Bool, store: AppStore) async -> SyncSignInOutcome {
        await serialized { () -> SyncSignInOutcome in
            guard store.hasRealAccount, !store.syncState.reconciled else {
                store.syncChoicePending = false
                return .continued
            }
            return await reconcile(store: store, policy: .preferAccount, previous: nil,
                                   forced: keepPhone ? .phone : .account)
        }
    }

    /// Matches the phone with the account once. `previous` is restored when the account cannot be read.
    static func reconcile(store: AppStore, policy: SyncSignInPolicy, previous: SyncPreviousSession?,
                          forced: SyncMergeBias? = nil) async -> SyncSignInOutcome {
        guard store.isPersistent, let account = store.account, !account.isGuest else { return .continued }
        let userID = account.userID
        store.syncActivity = .restoring
        defer { store.syncActivity = .idle }

        let snapshot: SyncRemoteSnapshot
        do {
            snapshot = try await fetchSnapshot(store: store, everything: true)
        } catch {
            if let previous, store.account?.userID == userID {
                store.account = previous.account
                store.syncState = previous.state
                store.lastSyncedAt = previous.lastSyncedAt
            }
            return .failed((error as? SyncFailure) ?? .offline)
        }
        guard store.account?.userID == userID else { return .failed(.interrupted) }

        let accountHasData = snapshot.planStamp != nil || snapshot.hasLiveRuns
        let phoneHasData = (store.plan != nil && store.hasCompletedOnboarding) || !store.runs.isEmpty
        let bias: SyncMergeBias
        if let forced {
            bias = forced
        } else if accountHasData, phoneHasData, policy == .askIfBoth {
            store.syncChoicePending = true
            return .needsChoice
        } else if accountHasData {
            bias = .account
        } else {
            // New account. A profile saved earlier (onboarding left half-way) still comes back when
            // nothing was answered on this phone yet.
            let untouched = store.answers == OnboardingAnswers() && store.plan == nil
            bias = untouched ? .account : .phone
        }

        var state = SyncState()
        state.reconciled = true
        store.syncState = state
        store.syncChoicePending = false
        if bias == .account {
            // A conversation held as a guest belongs to the plan that is being replaced.
            if snapshot.plan != nil { store.messages = [] }
        }
        merge(snapshot, into: store, bias: bias)
        store.lastSyncedAt = Date()

        let restoredPlan = bias == .account && snapshot.plan != nil && store.plan != nil
        if restoredPlan {
            store.refreshMissed()
            store.hasCompletedOnboarding = true
        }
        if store.messages.isEmpty { await CoachService.restoreHistory(store: store) }
        store.syncSchedulePush(after: 0.3)
        return restoredPlan ? .restored : .continued
    }
}

/// What the store held before a sign-in attempt, to put back if the account cannot be read.
struct SyncPreviousSession {
    var account: Account?
    var state: SyncState
    var lastSyncedAt: Date?
}
