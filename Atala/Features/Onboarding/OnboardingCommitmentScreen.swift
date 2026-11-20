import SwiftUI

/// Finger-drawn signature. Strokes live in memory only: nothing is saved.
struct OnboardingSignaturePad: View {
    @Binding var strokes: [[CGPoint]]
    @State private var isDrawing = false

    var body: some View {
        Canvas { context, _ in
            for stroke in strokes {
                guard let first = stroke.first else { continue }
                var path = Path()
                path.move(to: first)
                if stroke.count == 1 {
                    path.addLine(to: CGPoint(x: first.x + 0.5, y: first.y + 0.5))
                }
                for point in stroke.dropFirst() {
                    path.addLine(to: point)
                }
                context.stroke(path, with: .color(Theme.ink),
                               style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
            }
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    if isDrawing, !strokes.isEmpty {
                        strokes[strokes.count - 1].append(value.location)
                    } else {
                        // First stroke of the signature: a soft tick under the finger.
                        if strokes.isEmpty { Haptics.soft() }
                        strokes.append([value.location])
                        isDrawing = true
                    }
                }
                .onEnded { _ in
                    isDrawing = false
                }
        )
        .accessibilityLabel(L("Signature area"))
        .accessibilityHint(L("Draw your signature with your finger"))
    }
}

/// Screen 27: the commitment card, built from the real answers, signed with a finger.
struct OnboardingCommitmentScreen: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var strokes: [[CGPoint]] = []

    private var weekCount: Int {
        store.plan?.weeks.count ?? PlanEngine.maxWeeks
    }

    private var frequency: String {
        let count = OnboardingCopy.sessionsPerWeek(store.answers)
        switch count {
        case 1: return L("once a week")
        case 2: return L("twice a week")
        default: return L("%@ times a week", count)
        }
    }

    /// "I, Max, will show up 3 times a week for 8 weeks." The parts between two `*` are green:
    /// the markers stay in the translations, so each language puts them where they belong.
    private var pledge: Text {
        let sentence = L("I, %@, will show up *%@* for *%@*.",
                         store.firstName, frequency, OnboardingCopy.weeks(weekCount))
        let parts: [String] = sentence.components(separatedBy: "*")
        var result = Text(verbatim: "")
        for index in parts.indices {
            let piece = Text(verbatim: parts[index])
            result = result + (index % 2 == 1 ? piece.foregroundColor(Theme.green) : piece)
        }
        return result
    }

    var body: some View {
        OnboardingScaffold(title: L("Before you go, promise yourself you'll reach your goals."),
                           subtitle: L("Sign it. The runners who commit are the ones who show up."),
                           buttonTitle: L("I promise"),
                           buttonHaptic: .strong,
                           canContinue: !strokes.isEmpty,
                           tinted: true) {
            card
            // The coach approves as soon as the card is signed.
            CoachPoseView(coach: store.coach, pose: .thumbsUp)
                .adaptiveHeight(180)
                .frame(maxWidth: .infinity)
                .scaleEffect(strokes.isEmpty ? 0.92 : 1, anchor: .bottom)
                .opacity(strokes.isEmpty ? 0 : 1)
                .animation(reduceMotion ? nil : .spring(response: 0.5, dampingFraction: 0.72), value: strokes.isEmpty)
                .accessibilityHidden(true)
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 14) {
            pledge
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(Theme.ink)
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
            signatureArea
            Text(L("%@ · between you and you, never shared", Fmt.day(Date(), "d MMMM yyyy")))
                .font(.system(size: 12))
                .foregroundStyle(Theme.muted)
        }
        .padding(20)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: Theme.green.opacity(0.12), radius: 15, x: 0, y: 10)
    }

    private var signatureArea: some View {
        ZStack(alignment: .topTrailing) {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(hex: 0xFAFAF8))
            if strokes.isEmpty {
                Text(L("Sign here with your finger"))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.faint)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .allowsHitTesting(false)
            }
            OnboardingSignaturePad(strokes: $strokes)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Theme.faint, style: StrokeStyle(lineWidth: 1.5, dash: [6, 5]))
                .allowsHitTesting(false)
            if !strokes.isEmpty {
                Button(L("Clear")) {
                    Haptics.tap()
                    strokes = []
                }
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.muted)
                .padding(.horizontal, 12)
                .frame(height: 44)
            }
        }
        .frame(height: 150)
    }
}

#Preview {
    OnboardingCommitmentScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
