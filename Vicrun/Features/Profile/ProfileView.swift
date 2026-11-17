import SwiftUI

/// Profile and settings: identity, coach, goal, training days, paces, voice, Health and account.
struct ProfileView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    /// Goal and training days are edited on a copy, and applied only with "Update my plan".
    @State private var draft = OnboardingAnswers()
    @State private var loaded = false
    @State private var confirmUpdate = false
    @State private var planUpdated = false

    init() {}

    var body: some View {
        NavigationStack {
            Form {
                Group {
                    ProfileIdentitySection()
                    ProfileCoachSection()
                    ProfileGoalSection(draft: $draft)
                    ProfileDaysSection(draft: $draft)
                    updateSection
                }
                Group {
                    ProfilePacesSection()
                    ProfileVoiceSection()
                    ProfileHealthSection()
                    ProfileSubscriptionSection()
                    ProfileAccountSection()
                    ProfileFooterSection()
                }
            }
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .fontWeight(.semibold)
                }
            }
            .onAppear(perform: loadDraft)
            .alert("Update your plan?", isPresented: $confirmUpdate) {
                Button("Update my plan") { applyDraft() }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Your plan will be rebuilt from this week with your new goal and training days. The sessions of your current plan are replaced. The runs you already did stay in your history.")
            }
        }
        .tint(Theme.green)
    }

    // MARK: Plan update

    @ViewBuilder
    private var updateSection: some View {
        if store.plan == nil {
            Section {
                Button("Build my plan") { applyDraft() }
                    .fontWeight(.semibold)
                    .disabled(!canBuild)
            } footer: {
                Text(canBuild
                     ? "Your plan is built from your goal and your training days."
                     : "Choose a goal and at least one training day to build your plan.")
            }
        } else if hasPlanChanges {
            Section {
                Button("Update my plan") { confirmUpdate = true }
                    .fontWeight(.semibold)
                    .disabled(!canBuild)
                Button("Discard changes", role: .cancel) { draft = store.answers }
            } footer: {
                Text(canBuild
                     ? "Your changes to the goal and training days apply once you update your plan."
                     : "Keep a goal and at least one training day to update your plan.")
            }
        } else if planUpdated {
            Section {
                Label("Your plan is up to date", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(Theme.green)
            }
        }
    }

    private var canBuild: Bool {
        draft.goal != nil && !draft.runDays.isEmpty
    }

    private var hasPlanChanges: Bool {
        let saved = store.answers
        if draft.goal != saved.goal { return true }
        if draft.runDays.sorted() != saved.runDays.sorted() { return true }
        if draft.longRunDay != saved.longRunDay { return true }
        guard draft.goal != .regular else { return false }
        if draft.targetSeconds != saved.targetSeconds { return true }
        if draft.hasRace != saved.hasRace { return true }
        if draft.hasRace {
            switch (draft.raceDate, saved.raceDate) {
            case let (new?, old?):
                if !Calendar.plan.isDate(new, inSameDayAs: old) { return true }
            case (nil, nil):
                break
            default:
                return true
            }
        }
        return false
    }

    private func loadDraft() {
        guard !loaded else { return }
        loaded = true
        draft = store.answers
    }

    private func applyDraft() {
        var answers = store.answers
        let isRace = draft.goal != nil && draft.goal != .regular
        answers.goal = draft.goal
        answers.hasRace = isRace && draft.hasRace && draft.raceDate != nil
        answers.raceDate = answers.hasRace ? draft.raceDate : nil
        answers.targetSeconds = isRace ? draft.targetSeconds : nil
        answers.runDays = draft.runDays.sorted()
        answers.longRunDay = draft.longRunDay
        store.answers = answers
        store.generatePlan()
        store.refreshMissed()
        draft = store.answers
        planUpdated = true
    }
}

// MARK: - Identity

private struct ProfileIdentitySection: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        Section("You") {
            HStack {
                Text("First name")
                TextField("Your first name", text: $store.answers.firstName)
                    .multilineTextAlignment(.trailing)
                    .textContentType(.givenName)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
            }
        }
    }
}

// MARK: - Coach

private struct ProfileCoachSection: View {
    @EnvironmentObject private var store: AppStore

    private let columns = [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        Section {
            LazyVGrid(columns: columns, spacing: 14) {
                ForEach(Coach.all) { coach in
                    coachButton(coach)
                }
            }
            .padding(.vertical, 6)
            Picker("Tone", selection: $store.answers.tone) {
                ForEach(CoachTone.allCases) { tone in
                    Text(tone.title).tag(tone)
                }
            }
            .pickerStyle(.segmented)
            Text("“\(store.answers.tone.example)”")
                .font(.system(size: 14))
                .italic()
                .foregroundStyle(Theme.muted)
        } header: {
            Text("Your coach")
        } footer: {
            Text("The six coaches follow the same method. Only the face and the name change.")
        }
    }

    private func coachButton(_ coach: Coach) -> some View {
        let selected = store.answers.coachID == coach.id
        return Button {
            store.answers.coachID = coach.id
        } label: {
            VStack(spacing: 6) {
                CoachAvatar(coach: coach, size: 56)
                    .padding(3)
                    .overlay(
                        Circle().stroke(selected ? Theme.green : Color.clear, lineWidth: 2.5)
                    )
                Text(coach.name)
                    .font(.system(size: 13, weight: selected ? .semibold : .regular))
                    .foregroundStyle(selected ? Theme.ink : Theme.muted)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(coach.name)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

#Preview {
    ProfileView()
        .environmentObject(AppStore.preview)
}
