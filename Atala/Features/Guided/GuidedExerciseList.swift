import SwiftUI

/// The exercises of a session: picture, name, muscles and the amount. A tap opens the instructions.
struct GuidedExerciseList: View {
    let exercises: [GuidedPrescription]

    @State private var shown: ExerciseInfo?

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(exercises.enumerated()), id: \.offset) { _, exercise in
                Button {
                    Haptics.tap()
                    shown = exercise.info
                } label: {
                    GuidedExerciseRow(exercise: exercise)
                }
                .buttonStyle(.plain)
                .accessibilityHint("Shows how to do it")
            }
        }
        .sheet(item: $shown) { info in
            GuidedHowToSheet(info: info)
        }
    }
}

private struct GuidedExerciseRow: View {
    let exercise: GuidedPrescription

    var body: some View {
        HStack(spacing: 12) {
            GuidedExerciseThumb(info: exercise.info, size: 56)
            VStack(alignment: .leading, spacing: 2) {
                Text(exercise.info.name)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Text(exercise.info.muscles)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Text(exercise.amountText)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.ink)
                .multilineTextAlignment(.trailing)
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.faint)
                .accessibilityHidden(true)
        }
        .padding(.vertical, 8)
        .frame(minHeight: 72)
        .contentShape(Rectangle())
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.hairline).frame(height: 1)
        }
        .accessibilityElement(children: .combine)
    }
}

/// "How to": both positions, the steps, what to keep in mind, the usual mistake and the muscles.
struct GuidedHowToSheet: View {
    let info: ExerciseInfo

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                frames
                section("How to do it") {
                    ForEach(Array(info.steps.enumerated()), id: \.offset) { index, step in
                        line(marker: "\(index + 1)", text: step)
                    }
                }
                section("Keep in mind") {
                    ForEach(Array(info.cues.enumerated()), id: \.offset) { _, cue in
                        line(marker: nil, text: cue)
                    }
                }
                section("Common mistake") {
                    Text(info.mistake)
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.body)
                        .fixedSize(horizontal: false, vertical: true)
                }
                section("Muscles") {
                    Text(info.muscles)
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.body)
                }
                Text(ExerciseCatalog.safetyLine)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 18)
            .padding(.bottom, 24)
        }
        .background(Color.white.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(info.kind.title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.orangeDeep)
                Text(info.name)
                    .font(Theme.title(28))
                    .foregroundStyle(Theme.ink)
                Text(info.summary)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Button {
                Haptics.tap()
                dismiss()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .frame(width: 44, height: 44)
                    .background(Theme.surface, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
        }
    }

    private var frames: some View {
        HStack(spacing: 10) {
            framePanel(name: info.imageStart, caption: "Start")
            framePanel(name: info.imageEnd, caption: info.kind == .stretch ? "Stretch" : "Effort")
        }
    }

    private func framePanel(name: String, caption: String) -> some View {
        VStack(spacing: 6) {
            GuidedExerciseImage(name: name, symbol: info.kind.symbol)
                .padding(10)
                .frame(maxWidth: .infinity)
                .frame(height: 150)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .accessibilityHidden(true)
            Text(caption)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.muted)
        }
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.ink)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func line(marker: String?, text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            if let marker {
                Text(marker)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.green)
                    .frame(width: 22, height: 22)
                    .background(Theme.greenTint, in: Circle())
            } else {
                Circle()
                    .fill(Theme.greenSoft)
                    .frame(width: 6, height: 6)
                    .frame(width: 22, height: 22)
            }
            Text(text)
                .font(.system(size: 15))
                .foregroundStyle(Theme.body)
                .lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

#Preview("List") {
    GuidedExerciseList(exercises: GuidedPrescription.strengthSession(phase: .build))
        .padding(20)
}

#Preview("How to") {
    GuidedHowToSheet(info: ExerciseCatalog.all[0])
}
