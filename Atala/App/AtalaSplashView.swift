import SwiftUI

/// Launch splash, shown over the app on every cold launch for about 2.6 s. It starts where the
/// system launch screen ends (the logo mark on cream): the mark settles in with a spring, opens
/// into the full logo, the tagline rises under it, then everything fades away. It never waits
/// for anything: the app underneath is already loaded.
struct AtalaSplashView: View {
    /// Called once, when the splash has fully faded: the owner then removes it.
    let onFinished: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var markSettled = false
    @State private var opened = false
    @State private var taglineShown = false
    @State private var leaving = false

    static let tagline = "Running made effortless"

    var body: some View {
        ZStack {
            Theme.cream.ignoresSafeArea()
            VStack(spacing: 18) {
                ZStack {
                    BrandLogo(style: .mark, height: 76)
                        .scaleEffect(opened ? 0.7 : (markSettled ? 1 : 0.72))
                        .opacity(opened ? 0 : 1)
                    BrandLogo(style: .full, height: 46)
                        .scaleEffect(opened ? 1 : 0.86)
                        .opacity(opened ? 1 : 0)
                }
                .frame(height: 80)
                .foregroundStyle(Theme.forest)
                Text(Self.tagline)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(Theme.forest.opacity(0.78))
                    .opacity(taglineShown ? 1 : 0)
                    .offset(y: taglineShown ? 0 : 12)
            }
            .padding(.horizontal, 32)
            .scaleEffect(leaving ? 1.08 : 1)
        }
        .opacity(leaving ? 0 : 1)
        .allowsHitTesting(!leaving)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Atala. \(Self.tagline)")
        .task {
            await play()
        }
    }

    private static func wait(_ seconds: Double) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    @MainActor
    private func play() async {
        guard !leaving else { return }
        if reduceMotion {
            // Static logo and tagline for about a second, then a plain cross-fade.
            markSettled = true
            opened = true
            taglineShown = true
            await Self.wait(2.0)
            withAnimation(.easeInOut(duration: 0.3)) {
                leaving = true
            }
            await Self.wait(0.32)
            onFinished()
            return
        }
        withAnimation(.spring(response: 0.42, dampingFraction: 0.6)) {
            markSettled = true
        }
        await Self.wait(0.48)
        withAnimation(.spring(response: 0.4, dampingFraction: 0.82)) {
            opened = true
        }
        await Self.wait(0.22)
        withAnimation(.easeOut(duration: 0.4)) {
            taglineShown = true
        }
        // Hold the full logo and tagline long enough to be read.
        await Self.wait(1.6)
        withAnimation(.easeIn(duration: 0.35)) {
            leaving = true
        }
        await Self.wait(0.32)
        onFinished()
    }
}

#Preview {
    AtalaSplashView(onFinished: {})
}
