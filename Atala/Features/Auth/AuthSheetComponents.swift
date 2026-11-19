import SwiftUI

/// Wide sign-in button of the options sheet (54 pt, radius 16, icon and title centred together).
struct AuthProviderButton<Icon: View>: View {
    let title: String
    var dark: Bool = false
    var loading: Bool = false
    let action: () -> Void
    @ViewBuilder var icon: Icon

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                icon
                    .frame(width: 22, height: 22)
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundStyle(dark ? Color.white : Theme.ink)
            .padding(.horizontal, 18)
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .overlay(alignment: .trailing) {
                if loading {
                    ProgressView()
                        .tint(dark ? Color.white : Theme.ink)
                        .padding(.trailing, 18)
                }
            }
            .background(dark ? Theme.ink : Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(dark ? Color.clear : Theme.controlBorder, lineWidth: 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

/// The four-colour "G", drawn with arcs so no image asset is needed.
struct AuthGoogleMark: View {
    var size: CGFloat = 20

    private static let blue = Color(hex: 0x4285F4)
    private static let green = Color(hex: 0x34A853)
    private static let yellow = Color(hex: 0xFBBC05)
    private static let red = Color(hex: 0xEA4335)

    var body: some View {
        let line = size * 0.2
        ZStack {
            // Fractions run clockwise from 3 o'clock.
            arc(from: 0.0, to: 0.14, color: Self.blue, line: line)
            arc(from: 0.14, to: 0.40, color: Self.green, line: line)
            arc(from: 0.40, to: 0.58, color: Self.yellow, line: line)
            arc(from: 0.58, to: 0.89, color: Self.red, line: line)
            Rectangle()
                .fill(Self.blue)
                .frame(width: size * 0.48, height: line)
                .offset(x: size * 0.24)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private func arc(from start: CGFloat, to end: CGFloat, color: Color, line: CGFloat) -> some View {
        Circle()
            .trim(from: start, to: end)
            .stroke(color, style: StrokeStyle(lineWidth: line, lineCap: .butt))
            .padding(line / 2)
    }
}

/// Bordered text field used for the e-mail address and the code.
struct AuthFieldBox<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(.horizontal, 16)
            .frame(height: 54)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Theme.controlBorder, lineWidth: 1)
            )
    }
}

/// Black capsule button with a spinner while a request is running.
struct AuthSubmitButton: View {
    let title: String
    var enabled: Bool = true
    var loading: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if loading {
                    ProgressView()
                        .tint(Color.white)
                }
                Text(title)
            }
        }
        .buttonStyle(PrimaryButtonStyle(enabled: enabled && !loading))
        .disabled(!enabled || loading)
    }
}

#Preview {
    VStack(spacing: 12) {
        AuthProviderButton(title: "Continue with Google", action: {}) {
            AuthGoogleMark()
        }
        AuthSubmitButton(title: "Send me a code", loading: true, action: {})
    }
    .padding()
}
