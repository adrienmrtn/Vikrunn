import SwiftUI

/// Geometry of the coach carousel, kept out of the view so the visual effect closure stays trivial.
enum OnboardingCoachCarousel {
    static let slot: CGFloat = 150

    /// 0 when the item is centred in the scroll view, 1 when it is one slot away or more.
    static func distance(_ proxy: GeometryProxy, viewport: CGFloat) -> CGFloat {
        let midX = proxy.frame(in: .scrollView(axis: .horizontal)).midX
        return min(1, abs(midX - viewport / 2) / slot)
    }

    static func scale(_ proxy: GeometryProxy, viewport: CGFloat) -> CGFloat {
        1 - 0.42 * distance(proxy, viewport: viewport)
    }

    static func opacity(_ proxy: GeometryProxy, viewport: CGFloat) -> Double {
        Double(1 - 0.55 * distance(proxy, viewport: viewport))
    }
}

/// Screen 3: swipe through the six coaches. The centred one is large and animated.
struct OnboardingCoachScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @Environment(\.onboardingStep) private var step
    @State private var selectedID: Int?

    private var currentID: Int { selectedID ?? store.answers.coachID }
    private var currentIndex: Int { Coach.all.firstIndex { $0.id == currentID } ?? 0 }

    var body: some View {
        VStack(spacing: 0) {
            ProgressHeader(progress: nav.progress(for: step, store: store), onBack: { nav.back(store) })
                .padding(.horizontal, Theme.screenPadding)
            Text("Choose your coach")
                .font(Theme.title(28))
                .foregroundStyle(Theme.ink)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 10)
            GeometryReader { geo in
                carousel(size: geo.size)
            }
            .padding(.top, 8)
            hint
                .padding(.top, 10)
                .padding(.bottom, 4)
            BottomBar {
                PrimaryButton(title: "Choose \(Coach.by(id: currentID).name)") {
                    store.answers.coachID = currentID
                    nav.next(store)
                }
            }
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear {
            if selectedID == nil { selectedID = store.answers.coachID }
        }
        .onChange(of: selectedID) { _, newValue in
            if let newValue, newValue != store.answers.coachID {
                store.answers.coachID = newValue
            }
        }
    }

    private func carousel(size: CGSize) -> some View {
        let slot = OnboardingCoachCarousel.slot
        let viewport = size.width
        let figureHeight = max(160, size.height - 62)
        let figureWidth = figureHeight * 0.63
        let margin = max(0, (viewport - slot) / 2)
        return ScrollView(.horizontal) {
            HStack(spacing: 0) {
                ForEach(Coach.all) { coach in
                    coachItem(coach, figureWidth: figureWidth, figureHeight: figureHeight)
                        .frame(width: slot, height: size.height, alignment: .bottom)
                        .contentShape(Rectangle())
                        .visualEffect { content, proxy in
                            content
                                .scaleEffect(OnboardingCoachCarousel.scale(proxy, viewport: viewport), anchor: .bottom)
                                .opacity(OnboardingCoachCarousel.opacity(proxy, viewport: viewport))
                        }
                        .zIndex(coach.id == currentID ? 1 : 0)
                        .onTapGesture {
                            withAnimation(.snappy) { selectedID = coach.id }
                        }
                }
            }
            .scrollTargetLayout()
        }
        .contentMargins(.horizontal, margin, for: .scrollContent)
        .scrollTargetBehavior(.viewAligned)
        .scrollPosition(id: $selectedID, anchor: .center)
        .scrollIndicators(.hidden)
    }

    private func coachItem(_ coach: Coach, figureWidth: CGFloat, figureHeight: CGFloat) -> some View {
        VStack(spacing: 8) {
            CoachAnimationView(coach: coach, animated: coach.id == currentID)
                .frame(width: figureWidth, height: figureHeight)
            VStack(spacing: 2) {
                Text(coach.name)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Text(coach.trait)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.muted)
            }
            .lineLimit(1)
            .fixedSize()
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(coach.name), \(coach.trait)")
        .accessibilityAddTraits(coach.id == currentID ? [.isButton, .isSelected] : .isButton)
    }

    private var hint: some View {
        HStack(spacing: 14) {
            arrowButton(symbol: "chevron.left", label: "Previous coach", delta: -1)
            Text("Swipe to change coach")
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
            arrowButton(symbol: "chevron.right", label: "Next coach", delta: 1)
        }
    }

    private func arrowButton(symbol: String, label: String, delta: Int) -> some View {
        let target = currentIndex + delta
        let enabled = target >= 0 && target < Coach.all.count
        return Button(action: {
            guard enabled else { return }
            withAnimation(.snappy) { selectedID = Coach.all[target].id }
        }) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(enabled ? Theme.ink : Theme.faint)
                .frame(width: 44, height: 44)
                .background(Color.white, in: Circle())
                .overlay(Circle().stroke(Theme.controlBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

/// Screen 4: the chosen coach says hello.
struct OnboardingHelloScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        OnboardingTapScreen(backdrop: {
            Color(hex: 0x2F5A2A)
                .overlay {
                    Image("bg_hello")
                        .resizable()
                        .scaledToFill()
                }
                .clipped()
                .ignoresSafeArea()
        }, top: {
            VStack(alignment: .leading, spacing: 6) {
                Text("Hello \(store.firstName)!")
                    .font(Theme.title(28))
                    .foregroundStyle(Theme.ink)
                Text("I'm \(store.coach.name). I'll be your coach all the way to your goal. A few questions, and I'll build your plan.")
                    .font(.system(size: 16))
                    .foregroundStyle(Color(hex: 0x333333))
                    .lineSpacing(4)
                    .multilineTextAlignment(.leading)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                Color.white.opacity(0.95),
                in: UnevenRoundedRectangle(topLeadingRadius: 24, bottomLeadingRadius: 8,
                                           bottomTrailingRadius: 24, topTrailingRadius: 24)
            )
            .shadow(color: Color(hex: 0x14280A).opacity(0.18), radius: 12, x: 0, y: 8)
        })
    }
}

/// Screen 11: full-screen green transition before the goal questions.
struct OnboardingGoalIntroScreen: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        OnboardingTapScreen(lightContent: true, backdrop: {
            Theme.green
                .overlay(alignment: .bottom) {
                    Circle()
                        .fill(Color(hex: 0x2A6E5B))
                        .frame(width: 630, height: 630)
                        .offset(y: 240)
                }
                .clipped()
                .ignoresSafeArea()
        }, top: {
            VStack(alignment: .leading, spacing: 14) {
                Text("I know where you're starting from, \(store.firstName).")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.greenOnDark)
                Text("Now it's time to set your goal.")
                    .font(.system(size: 38, weight: .bold))
                    .foregroundStyle(Color.white)
                    .lineSpacing(2)
            }
            .multilineTextAlignment(.leading)
            .padding(.top, 20)
        })
    }
}

/// Screen 16: the coach answers the "why".
struct OnboardingCoachReplyScreen: View {
    @EnvironmentObject private var store: AppStore

    private var motivation: Motivation { store.answers.motivation ?? .prove }

    private var chip: String {
        switch motivation {
        case .prove: return "You want to prove to yourself you can do it"
        case .record: return "You want to beat your record"
        case .health: return "You want to take care of your health"
        case .clearHead: return "You want to clear your head"
        case .together: return "You want to run with people you love"
        }
    }

    private var detail: String {
        let answers = store.answers
        let goal = answers.goal ?? .regular
        if goal == .regular {
            return "A running habit is built session after session. I'll be there for each one."
        }
        if let target = answers.targetSeconds {
            return "A \(OnboardingCopy.goalNoun(goal)) in \(Fmt.goalTime(target)) is built session after session. I'll be there for each one."
        }
        return "A \(OnboardingCopy.goalNoun(goal)) is built session after session. I'll be there for each one."
    }

    var body: some View {
        OnboardingTapScreen(backdrop: {
            Theme.orangeTint
                .overlay(alignment: .bottom) {
                    Circle()
                        .fill(Color(hex: 0xF6D3BD))
                        .frame(width: 630, height: 630)
                        .offset(y: 240)
                }
                .clipped()
                .ignoresSafeArea()
        }, top: {
            VStack(alignment: .leading, spacing: 14) {
                Text(chip)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.white, in: Capsule())
                Text(motivation.coachReply)
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(Theme.ink)
                Text(detail)
                    .font(.system(size: 17))
                    .foregroundStyle(Color(hex: 0x4A3A30))
                    .lineSpacing(4)
            }
            .multilineTextAlignment(.leading)
            .padding(.top, 20)
        })
    }
}

#Preview("Coach") {
    OnboardingCoachScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}

#Preview("Hello") {
    OnboardingHelloScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
