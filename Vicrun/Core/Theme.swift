import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// Design tokens of the retained direction ("very light glass", white, green and orange).
enum Theme {
    static let ink = Color(hex: 0x111111)
    static let body = Color(hex: 0x444444)
    static let muted = Color(hex: 0x6B6B6B)
    static let faint = Color(hex: 0x9A9A9A)
    static let hairline = Color(hex: 0xE4E4E4)
    static let cardBorder = Color(hex: 0xECECEA)
    static let controlBorder = Color(hex: 0xDADADA)
    static let surface = Color(hex: 0xF4F6F3)
    static let bubble = Color(hex: 0xF1F4F1)
    static let green = Color(hex: 0x1F5D4C)
    static let greenSoft = Color(hex: 0x3F8571)
    static let greenTint = Color(hex: 0xE3EEEA)
    static let greenOnDark = Color(hex: 0xCFE2DB)
    static let orange = Color(hex: 0xE8641E)
    static let orangeDeep = Color(hex: 0xB4480F)
    static let orangeTint = Color(hex: 0xFBE7DA)
    static let yellow = Color(hex: 0xD9B45A)
    static let yellowDeep = Color(hex: 0x9A7412)
    static let yellowTint = Color(hex: 0xFBF1D3)
    static let locked = Color(hex: 0xE9E9E6)

    static let screenPadding: CGFloat = 20
    static let cardRadius: CGFloat = 20
    static let buttonHeight: CGFloat = 54

    static func title(_ size: CGFloat = 28) -> Font { .system(size: size, weight: .bold) }
}

// MARK: - Buttons

struct PrimaryButtonStyle: ButtonStyle {
    var enabled: Bool = true
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: Theme.buttonHeight)
            .background(enabled ? Theme.ink : Theme.faint, in: Capsule())
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(Theme.ink)
            .frame(maxWidth: .infinity)
            .frame(height: Theme.buttonHeight)
            .background(Color.white, in: Capsule())
            .overlay(Capsule().stroke(Theme.ink, lineWidth: 1))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

struct PrimaryButton: View {
    let title: String
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(title, action: action)
            .buttonStyle(PrimaryButtonStyle(enabled: enabled))
            .disabled(!enabled)
    }
}

/// Bottom call-to-action area used on most screens.
struct BottomBar<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(spacing: 10) { content }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 12)
            .padding(.bottom, 8)
            .background(Color.white)
    }
}

// MARK: - Cards

extension View {
    /// White card with the soft border and shadow of the V2 option cards.
    func card(selected: Bool = false, radius: CGFloat = Theme.cardRadius) -> some View {
        self
            .background(Color.white, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .stroke(selected ? Theme.ink : Theme.cardBorder, lineWidth: selected ? 1.5 : 1)
            )
            .shadow(color: .black.opacity(0.05), radius: 5, x: 0, y: 2)
    }
}

/// Small illustrated vignette (pastel icon on white) blended into the card.
struct VignetteImage: View {
    let name: String
    var size: CGFloat = 48
    var body: some View {
        Image(name)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .blendMode(.multiply)
            .accessibilityHidden(true)
    }
}

/// Rounded tile holding an SF Symbol.
struct IconTile: View {
    let systemName: String
    var tint: Color = Theme.green
    var background: Color = Theme.greenTint
    var size: CGFloat = 44
    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.32, style: .continuous)
            .fill(background)
            .frame(width: size, height: size)
            .overlay(
                Image(systemName: systemName)
                    .font(.system(size: size * 0.45, weight: .semibold))
                    .foregroundStyle(tint)
            )
            .accessibilityHidden(true)
    }
}

/// Option row with a leading vignette or icon, a title, an optional subtitle and a radio or checkbox.
struct OptionCard<Leading: View>: View {
    let title: String
    var subtitle: String? = nil
    let selected: Bool
    var multi: Bool = false
    let action: () -> Void
    @ViewBuilder var leading: Leading

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                leading
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                    if let subtitle {
                        Text(subtitle)
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.muted)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                SelectionMark(selected: selected, multi: multi)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(minHeight: 74)
            .card(selected: selected)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct SelectionMark: View {
    let selected: Bool
    var multi: Bool = false
    var body: some View {
        if multi {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(selected ? Theme.green : Color.clear)
                .frame(width: 24, height: 24)
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .stroke(selected ? Theme.green : Color(hex: 0xC4C4C4), lineWidth: 1.5)
                )
                .overlay(
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.white)
                        .opacity(selected ? 1 : 0)
                )
        } else {
            Circle()
                .stroke(selected ? Theme.ink : Color(hex: 0xC4C4C4), lineWidth: selected ? 6 : 1.5)
                .frame(width: selected ? 16 : 20.5, height: selected ? 16 : 20.5)
                .frame(width: 22, height: 22)
        }
    }
}

// MARK: - Headers

/// Back chevron + thin progress bar used through the questionnaire.
struct ProgressHeader: View {
    /// 0...1, or nil to hide the bar.
    var progress: Double?
    var onBack: (() -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            if let onBack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Back")
                .padding(.leading, -12)
            } else {
                Color.clear.frame(width: 32, height: 44)
            }
            if let progress {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.hairline)
                        Capsule().fill(Theme.ink)
                            .frame(width: max(4, geo.size.width * min(max(progress, 0), 1)))
                    }
                }
                .frame(height: 4)
                .animation(.easeInOut(duration: 0.35), value: progress)
            } else {
                Spacer()
            }
            Color.clear.frame(width: 32, height: 44)
        }
        .frame(height: 44)
    }
}

/// Grey note with the coach avatar, used for the coach's short comments.
struct CoachNote: View {
    let coach: Coach
    let text: String
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            CoachAvatar(coach: coach, size: 36)
            VStack(alignment: .leading, spacing: 3) {
                Text(coach.name)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Theme.green)
                Text(text)
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.ink)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct CoachAvatar: View {
    let coach: Coach
    var size: CGFloat = 36
    var body: some View {
        Image(coach.avatarAsset)
            .resizable()
            .scaledToFill()
            .frame(width: size, height: size)
            .clipShape(Circle())
            .accessibilityLabel(coach.name)
    }
}

// MARK: - Formatting

enum Fmt {
    /// "4:25" from seconds per kilometre.
    static func pace(_ secPerKm: Int) -> String {
        guard secPerKm > 0 else { return "--:--" }
        return String(format: "%d:%02d", secPerKm / 60, secPerKm % 60)
    }

    /// "54:48" or "1:45:00".
    static func duration(_ seconds: Int) -> String {
        let s = max(0, seconds)
        let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, sec) : String(format: "%d:%02d", m, sec)
    }

    /// "1 h 45" style used for goals.
    static func goalTime(_ seconds: Int) -> String {
        let h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60
        if h > 0 { return s == 0 ? String(format: "%d:%02d:00", h, m) : String(format: "%d:%02d:%02d", h, m, s) }
        return String(format: "%d:%02d", m, s)
    }

    static func km(_ km: Double, decimals: Int = 1) -> String {
        if abs(km - km.rounded()) < 0.05 { return "\(Int(km.rounded())) km" }
        return String(format: "%.\(decimals)f km", km)
    }

    static func minutes(_ min: Int) -> String {
        min >= 60 ? String(format: "%d h %02d", min / 60, min % 60) : "\(min) min"
    }

    static func day(_ date: Date, _ format: String = "EEEE d MMMM") -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US")
        f.dateFormat = format
        return f.string(from: date)
    }
}
