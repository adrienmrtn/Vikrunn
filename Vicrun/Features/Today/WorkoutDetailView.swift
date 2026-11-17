import SwiftUI

/// Detail of one session of the plan, with the actions that fit its state.
struct WorkoutDetailView: View {
    let workoutID: UUID

    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var showRun = false
    @State private var showManualEntry = false

    var body: some View {
        Group {
            if let workout = store.workout(id: workoutID) {
                content(workout)
            } else {
                missing
            }
        }
        .background(Color.white)
        .toolbar(.hidden, for: .navigationBar)
        .modifier(MainHidesTabBar())
        .fullScreenCover(isPresented: $showRun) {
            RunFlowView(workout: store.workout(id: workoutID))
                .environmentObject(store)
        }
        .sheet(isPresented: $showManualEntry) {
            ManualRunEntryView(workout: store.workout(id: workoutID))
                .environmentObject(store)
        }
    }

    // MARK: Content

    private func content(_ workout: Workout) -> some View {
        ZStack(alignment: .top) {
            ScrollView {
                ZStack(alignment: .top) {
                    illustration
                    VStack(alignment: .leading, spacing: 18) {
                        summaryPanel(workout)
                        WorkoutDetailSteps(workout: workout)
                            .padding(.horizontal, 4)
                        whySection(workout)
                            .padding(.horizontal, 4)
                        if workout.status == .done, let run = store.run(id: workout.runID) {
                            linkedRun(run)
                                .padding(.horizontal, 4)
                        }
                    }
                    .padding(.horizontal, Theme.screenPadding)
                    .padding(.top, 210)
                    .padding(.bottom, 24)
                }
            }
            .ignoresSafeArea(edges: .top)
            topButtons(workout)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if workout.status != .done {
                actionBar(workout)
            }
        }
    }

    private var illustration: some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: 300)
            .overlay(
                Image("illu_session")
                    .resizable()
                    .scaledToFill()
            )
            .clipped()
            .overlay(alignment: .bottom) {
                LinearGradient(colors: [Color.white.opacity(0), Color.white],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: 50)
            }
            .accessibilityHidden(true)
    }

    private func topButtons(_ workout: Workout) -> some View {
        HStack {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .frame(width: 44, height: 44)
                    .background(Color.white.opacity(0.9), in: Circle())
            }
            .accessibilityLabel("Back")
            Spacer()
            if workout.status == .done && !workout.kind.isRun {
                Menu {
                    Button("Mark as not done") {
                        store.setStatus(.planned, for: workout.id)
                        store.refreshMissed()
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .frame(width: 44, height: 44)
                        .background(Color.white.opacity(0.9), in: Circle())
                }
                .accessibilityLabel("More options")
            }
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 4)
    }

    private func summaryPanel(_ workout: Workout) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .center, spacing: 8) {
                    Text("\(Fmt.day(workout.date)) · effort \(workout.effort) of 10")
                        .font(.system(size: 13))
                        .foregroundStyle(Color(hex: 0x5A5A5A))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Spacer(minLength: 4)
                    MainStatusBadge(status: workout.status)
                }
                Text(workout.title)
                    .font(.system(size: 32, weight: .bold))
                    .tracking(-0.8)
                    .foregroundStyle(Theme.ink)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                if !workout.subtitle.isEmpty {
                    Text(workout.subtitle)
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.muted)
                }
            }
            MainWorkoutStats(workout: workout)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .mainGlass(radius: 24)
    }

    private func whySection(_ workout: Workout) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Why this session")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.ink)
            Text(workout.why)
                .font(.system(size: 14))
                .lineSpacing(4)
                .foregroundStyle(Theme.body)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func linkedRun(_ run: RunRecord) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Your run")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.ink)
            NavigationLink {
                RunSummaryView(run: run)
                    .modifier(MainHidesTabBar())
            } label: {
                HStack(spacing: 12) {
                    IconTile(systemName: "checkmark", size: 40)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(Fmt.km(run.distanceKm)) in \(Fmt.duration(run.durationSec))")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Theme.ink)
                        Text("\(Fmt.pace(run.avgPace))/km on average · see the summary")
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.muted)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 4)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.faint)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .card(radius: 16)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: Actions

    private func actionBar(_ workout: Workout) -> some View {
        HStack(spacing: 10) {
            actionButtons(workout)
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(Color.white.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.hairline).frame(height: 1)
        }
    }

    @ViewBuilder
    private func actionButtons(_ workout: Workout) -> some View {
        if workout.status == .missed {
            Button("I did it") {
                if workout.kind.isRun {
                    showManualEntry = true
                } else {
                    store.setStatus(.done, for: workout.id)
                }
            }
            .buttonStyle(PrimaryButtonStyle())
        } else if workout.kind.isRun {
            Button("I already did it") {
                showManualEntry = true
            }
            .buttonStyle(SecondaryButtonStyle())
            Button("Start") {
                showRun = true
            }
            .buttonStyle(PrimaryButtonStyle())
        } else {
            Button("Mark as done") {
                store.setStatus(.done, for: workout.id)
            }
            .buttonStyle(PrimaryButtonStyle())
        }
    }

    private var missing: some View {
        VStack(spacing: 8) {
            MainEmptyState(systemName: "calendar.badge.exclamationmark",
                           title: "Session not found",
                           message: "This session is no longer in your plan.",
                           actionTitle: "Back") {
                dismiss()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    let store = AppStore.preview
    return NavigationStack {
        WorkoutDetailView(workoutID: store.nextRun?.id ?? UUID())
    }
    .environmentObject(store)
    .environmentObject(MainChrome())
}
