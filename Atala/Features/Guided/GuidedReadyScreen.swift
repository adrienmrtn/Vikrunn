import SwiftUI

/// Before the session: what is coming, the voice switch, a word from the coach, then "Start".
struct GuidedReadyScreen: View {
    let workout: Workout
    let exercises: [GuidedPrescription]
    let onStart: () -> Void
    let onClose: () -> Void

    @EnvironmentObject private var store: AppStore

    private var totalMinutes: Int { GuidedPrescription.totalMinutes(exercises) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                closeButton
                header
                voiceRow
                VStack(alignment: .leading, spacing: 4) {
                    Text("Exercises")
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
                PrimaryButton(title: "Start", enabled: !exercises.isEmpty, action: onStart)
            }
        }
    }

    private var closeButton: some View {
        Button(action: onClose) {
            Image(systemName: "xmark")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Close")
        .padding(.leading, -10)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("\(exercises.count) exercises · about \(Fmt.minutes(totalMinutes))")
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

    private var voiceRow: some View {
        VStack(spacing: 0) {
            Divider().overlay(Theme.hairline)
            HStack(spacing: 14) {
                IconTile(systemName: "speaker.wave.2.fill", tint: Theme.orangeDeep,
                         background: Theme.orangeTint, size: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(store.coach.name)'s voice")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    Text(store.settings.voice.enabled ? "Announces each exercise and counts you down"
                                                      : "Off for this session")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Toggle("Coach voice", isOn: $store.settings.voice.enabled)
                    .labelsHidden()
                    .tint(Theme.green)
            }
            .frame(minHeight: 64)
            .padding(.vertical, 4)
            Divider().overlay(Theme.hairline)
        }
    }

    // MARK: Wording

    private var setupLine: String {
        workout.kind == .strength
            ? "Bodyweight only. A little floor space is all you need."
            : "Find a quiet spot, with a wall nearby and a mat if you have one."
    }

    private var coachLine: String {
        if workout.kind == .strength {
            return "I'll count the sets and time your rests. Go at your own pace, clean movements matter more than speed."
        }
        return "I'll time every stretch for you. Ease into each one, breathe, and stop if anything hurts."
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
