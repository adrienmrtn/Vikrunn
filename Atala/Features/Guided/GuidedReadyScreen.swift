import SwiftUI

/// Before the session: what is coming, the voice switch, a word from the coach, then "Start".
struct GuidedReadyScreen: View {
    let workout: Workout
    let exercises: [GuidedPrescription]
    let onStart: () -> Void
    let onClose: () -> Void

    @EnvironmentObject private var store: AppStore
    @ObservedObject private var purchases = PurchaseService.shared
    @State private var showPaywall = false

    private var totalMinutes: Int { GuidedPrescription.totalMinutes(exercises) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                closeButton
                header
                voiceRow
                VStack(alignment: .leading, spacing: 4) {
                    Text(L("Exercises"))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    GuidedExerciseList(exercises: exercises)
                }
                CoachNote(coach: store.coach, text: coachLine)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 4)
            .padding(.bottom, 16)
        }
        .scrollIndicators(.hidden)
        .background(Color.white.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            BottomBar {
                // Starting the session plays its own, stronger haptic.
                PrimaryButton(title: L("Start"), enabled: !exercises.isEmpty, haptic: nil, action: onStart)
            }
        }
        .proPaywall(isPresented: $showPaywall)
    }

    private var closeButton: some View {
        Button(action: {
            Haptics.tap()
            onClose()
        }) {
            Image(systemName: "xmark")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(L("Close"))
        .padding(.leading, -10)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(exercises.count == 1 ? L("%@ exercise · about %@", exercises.count, Fmt.minutes(totalMinutes))
                                       : L("%@ exercises · about %@", exercises.count, Fmt.minutes(totalMinutes)))
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.orangeDeep)
            Text(workout.title)
                .font(Theme.title(32))
                .tracking(-0.8)
                .foregroundStyle(Theme.ink)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
            Text(setupLine)
                .font(.system(size: 15))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// The coach's voice needs Atala Pro: a live switch with it, a padlock that opens the paywall without.
    private var voiceRow: some View {
        VStack(spacing: 0) {
            Divider().overlay(Theme.hairline)
            CoachVoiceRow(coachName: store.coach.name,
                          isPro: purchases.isPro,
                          isOn: $store.settings.voice.enabled,
                          onText: L("Announces each exercise and counts you down"),
                          offText: L("Off for this session"),
                          onLocked: { showPaywall = true })
                .padding(.vertical, 6)
            Divider().overlay(Theme.hairline)
        }
    }

    // MARK: Wording

    private var setupLine: String {
        workout.kind == .strength
            ? L("Bodyweight only. A little floor space is all you need.")
            : L("Find a quiet spot, with a wall nearby and a mat if you have one.")
    }

    private var coachLine: String {
        if workout.kind == .strength {
            return L("I'll count the sets and time your rests. Go at your own pace, clean movements matter more than speed.")
        }
        return L("I'll time every stretch for you. Ease into each one, breathe, and stop if anything hurts.")
    }
}

#Preview {
    let store = AppStore.preview
    let workout = store.allWorkouts.first { $0.kind == .stretching }
    return Group {
        if let workout {
            GuidedReadyScreen(workout: workout, exercises: GuidedPrescription.session(for: workout),
                              onStart: {}, onClose: {})
        }
    }
    .environmentObject(store)
}
