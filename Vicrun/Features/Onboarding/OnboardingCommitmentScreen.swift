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
                        strokes.append([value.location])
                        isDrawing = true
                    }
                }
                .onEnded { _ in
                    isDrawing = false
                }
        )
        .accessibilityLabel("Signature area")
        .accessibilityHint("Draw your signature with your finger")
    }
}

/// Screen 27: the commitment card, built from the real answers, signed with a finger.
struct OnboardingCommitmentScreen: View {
    @EnvironmentObject private var store: AppStore
    @State private var strokes: [[CGPoint]] = []

    private var weekCount: Int {
        store.plan?.weeks.count ?? PlanEngine.maxWeeks
    }

    private var frequency: String {
        let count = OnboardingCopy.sessionsPerWeek(store.answers)
        switch count {
        case 1: return "once a week"
        case 2: return "twice a week"
        default: return "\(count) times a week"
        }
    }

    private var pledge: Text {
        let answers = store.answers
        let goal = answers.goal ?? .regular
        let start = Text("I, \(store.firstName), commit to running ")
            + Text(frequency).foregroundColor(Theme.green)
            + Text(" for ")
            + Text(OnboardingCopy.weeks(weekCount)).foregroundColor(Theme.green)
        if goal == .regular {
            return start + Text(" to make running part of my life.")
        }
        let finish = start + Text(" to finish my \(OnboardingCopy.goalNoun(goal))")
        if let target = answers.targetSeconds {
            return finish + Text(" in ") + Text(Fmt.goalTime(target)).foregroundColor(Theme.orangeDeep) + Text(".")
        }
        return finish + Text(".")
    }

    private var reason: String? {
        guard let motivation = store.answers.motivation else { return nil }
        switch motivation {
        case .prove: return "Because I want to prove to myself I can do it."
        case .record: return "Because I want to beat my record."
        case .health: return "Because I want to take care of my health."
        case .clearHead: return "Because I want to clear my head."
        case .together: return "Because I want to run with people I love."
        }
    }

    var body: some View {
        OnboardingScaffold(title: "Are you ready for your plan?",
                           buttonTitle: "I commit",
                           canContinue: !strokes.isEmpty,
                           tinted: true) {
            card
            Text("A commitment to yourself, not a contract: it binds you to nothing else. Your signature is not saved.")
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .lineSpacing(3)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("MY COMMITMENT")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.green)
                Spacer()
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(Theme.green)
                    .accessibilityHidden(true)
            }
            pledge
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(Theme.ink)
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
            if let reason {
                Text(reason)
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            signatureArea
            HStack {
                Text("Signed on \(Fmt.day(Date(), "d MMMM yyyy"))")
                Spacer(minLength: 8)
                Text("Witness: \(store.coach.name)")
            }
            .font(.system(size: 12))
            .foregroundStyle(Theme.muted)
        }
        .padding(.horizontal, 20)
        .padding(.top, 22)
        .padding(.bottom, 16)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: Theme.green.opacity(0.12), radius: 15, x: 0, y: 10)
    }

    private var signatureArea: some View {
        ZStack(alignment: .topTrailing) {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(hex: 0xFAFAF8))
            if strokes.isEmpty {
                Text("Sign here with your finger")
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
                Button("Clear") {
                    strokes = []
                }
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.muted)
                .padding(.horizontal, 12)
                .frame(height: 44)
            }
        }
        .frame(height: 130)
    }
}

#Preview {
    OnboardingCommitmentScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
