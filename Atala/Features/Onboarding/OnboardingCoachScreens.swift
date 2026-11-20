import SwiftUI

/// Geometry of the coach carousel, computed from the container size only.
struct OnboardingCoachCarouselMetrics {
    /// Scale of the coach on each side of the selected one.
    static let neighbourScale: CGFloat = 0.55
    static let labelHeight: CGFloat = 54
    /// Width / height of the room given to a coach picture.
    static let aspect: CGFloat = 0.628

    let figureWidth: CGFloat
    let figureHeight: CGFloat
    /// Distance between the centres of two coaches.
    let spacing: CGFloat

    init(size: CGSize) {
        let byHeight = max(140, size.height - Self.labelHeight - 8)
        let byWidth = size.width * 0.62 / Self.aspect
        let height = min(byHeight, byWidth)
        let width = height * Self.aspect
        figureHeight = height
        figureWidth = width
        // The neighbour's outer edge stays 6 pt inside the screen.
        spacing = max(60, size.width / 2 - 6 - width * Self.neighbourScale / 2)
    }
}

/// The coaches on one line. The selected one is centred and large, one smaller, faded
/// neighbour stands on each side. Positions come from `index` and the drag only, so the
/// centred coach is always the selected one.
struct OnboardingCoachCarouselView: View {
    @Binding var index: Int

    @State private var drag: CGFloat = 0
    @GestureState private var isDragging = false

    private static let snap = Animation.spring(response: 0.42, dampingFraction: 0.86)

    var body: some View {
        GeometryReader { geo in
            carousel(size: geo.size, metrics: OnboardingCoachCarouselMetrics(size: geo.size))
        }
        .onChange(of: isDragging) { _, dragging in
            if dragging { Haptics.prepare(.select) }
            // A cancelled drag never calls onEnded: bring the line back in place.
            if !dragging, drag != 0 {
                withAnimation(Self.snap) { drag = 0 }
            }
        }
    }

    private func carousel(size: CGSize, metrics: OnboardingCoachCarouselMetrics) -> some View {
        ZStack(alignment: .bottom) {
            ForEach(Coach.all.indices, id: \.self) { position in
                item(position, metrics: metrics)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .bottom)
        .contentShape(Rectangle())
        .simultaneousGesture(dragGesture(spacing: metrics.spacing))
        .accessibilityElement(children: .contain)
    }

    /// Horizontal distance from the container centre, in points.
    private func centreOffset(of position: Int, spacing: CGFloat) -> CGFloat {
        var pull = min(max(drag, -spacing), spacing)
        let atStart = index == 0 && pull > 0
        let atEnd = index == Coach.all.count - 1 && pull < 0
        if atStart || atEnd { pull *= 0.3 }
        return CGFloat(position - index) * spacing + pull
    }

    private func item(_ position: Int, metrics: OnboardingCoachCarouselMetrics) -> some View {
        let coach = Coach.all[position]
        let x = centreOffset(of: position, spacing: metrics.spacing)
        // 0 when centred, 1 for the direct neighbour, 2 and more further away.
        let distance = abs(x) / metrics.spacing
        let near = min(1, distance)
        let scale = 1 - (1 - OnboardingCoachCarouselMetrics.neighbourScale) * near
        let figureOpacity = distance <= 1 ? 1 - 0.55 * distance : max(0, 0.45 * (2 - distance))
        let isSelected = position == index
        return VStack(spacing: 8) {
            CoachPoseView(coach: coach, pose: .wave)
                .frame(width: metrics.figureWidth, height: metrics.figureHeight)
                .scaleEffect(scale, anchor: .bottom)
                .opacity(Double(figureOpacity))
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
            .frame(height: OnboardingCoachCarouselMetrics.labelHeight - 8)
            .opacity(Double(1 - near))
        }
        .frame(width: metrics.figureWidth)
        .contentShape(Rectangle())
        .onTapGesture { select(position) }
        .offset(x: x)
        .zIndex(Double(1 - near))
        .allowsHitTesting(abs(position - index) <= 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(coach.name), \(coach.trait)")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .accessibilityHidden(abs(position - index) > 1)
        .accessibilityAction { select(position) }
    }

    private func select(_ position: Int) {
        guard position != index, Coach.all.indices.contains(position) else { return }
        Haptics.select()
        withAnimation(Self.snap) {
            index = position
            drag = 0
        }
    }

    private func dragGesture(spacing: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .updating($isDragging) { _, state, _ in
                state = true
            }
            .onChanged { value in
                drag = value.translation.width
            }
            .onEnded { value in
                let moved = value.translation.width
                let predicted = value.predictedEndTranslation.width
                var target = index
                if moved < -spacing * 0.5 || predicted < -spacing * 0.45 {
                    target += 1
                } else if moved > spacing * 0.5 || predicted > spacing * 0.45 {
                    target -= 1
                }
                target = min(max(target, 0), Coach.all.count - 1)
                // One tick per coach change, none when the line snaps back.
                if target != index { Haptics.select() }
                withAnimation(Self.snap) {
                    index = target
                    drag = 0
                }
            }
    }
}

/// Screen 3: swipe through the coaches. The centred one is large, its neighbours small and faded.
struct OnboardingCoachScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var nav: OnboardingNavigator
    @State private var index = 0
    @State private var restored = false

    private var current: Coach {
        Coach.all[min(max(index, 0), Coach.all.count - 1)]
    }

    var body: some View {
        VStack(spacing: 0) {
            OnboardingHeaderSpace()
            Text(L("First, let's choose your coach"))
                .font(Theme.title(28))
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 10)
            OnboardingCoachCarouselView(index: $index)
                .padding(.top, 8)
            hint
                .padding(.top, 10)
                .padding(.bottom, 4)
            BottomBar {
                PrimaryButton(title: L("Choose %@", current.name)) {
                    store.answers.coachID = current.id
                    nav.next(store)
                }
            }
        }
        .background(Color.white.ignoresSafeArea())
        .onAppear {
            guard !restored else { return }
            restored = true
            index = Coach.all.firstIndex { $0.id == store.answers.coachID } ?? 0
        }
        .onChange(of: index) { _, newValue in
            guard Coach.all.indices.contains(newValue) else { return }
            let id = Coach.all[newValue].id
            if id != store.answers.coachID {
                store.answers.coachID = id
            }
        }
    }

    private var hint: some View {
        HStack(spacing: 14) {
            arrowButton(symbol: "chevron.left", label: L("Previous coach"), delta: -1)
            Text(L("Swipe to change coach"))
                .font(.system(size: 13))
                .foregroundStyle(Theme.muted)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            arrowButton(symbol: "chevron.right", label: L("Next coach"), delta: 1)
        }
    }

    private func arrowButton(symbol: String, label: String, delta: Int) -> some View {
        let target = index + delta
        let enabled = target >= 0 && target < Coach.all.count
        return Button(action: {
            guard enabled else { return }
            Haptics.select()
            withAnimation(.spring(response: 0.42, dampingFraction: 0.86)) { index = target }
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

#Preview("Coach") {
    OnboardingCoachScreen()
        .environmentObject(AppStore.preview)
        .environmentObject(OnboardingNavigator())
}
