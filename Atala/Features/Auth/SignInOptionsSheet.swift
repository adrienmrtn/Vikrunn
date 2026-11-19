import SwiftUI

private enum AuthSheetMode {
    case options, email, code
}

private enum AuthSheetBusy {
    case apple, google, sendingCode, verifyingCode
}

private enum AuthSheetField {
    case email, code
}

/// Bottom-sheet content: Apple, Google, e-mail one-time code, and a dev-only guest entry.
@MainActor
struct SignInOptionsSheet: View {
    private let onSignedIn: (Account) -> Void

    @State private var mode: AuthSheetMode = .options
    @State private var busy: AuthSheetBusy?
    @State private var errorText: String?
    @State private var infoText: String?
    @State private var email: String = ""
    @State private var code: String = ""
    @FocusState private var focus: AuthSheetField?
    /// Height of the options content, measured on screen; the sheet is exactly that tall.
    @State private var fittedHeight: CGFloat = 380

    /// The options step is as tall as its content. Only the e-mail and code steps use the tall
    /// detent, so the keyboard does not hide the field.
    private var detents: Set<PresentationDetent> {
        mode == .options ? [.height(fittedHeight)] : [.large]
    }

    init(onSignedIn: @escaping (Account) -> Void) {
        self.onSignedIn = onSignedIn
    }

    var body: some View {
        // The frame only pins the content to the top of the sheet: it is measured before it.
        container
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color.white)
            .presentationDetents(detents)
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(28)
            .interactiveDismissDisabled(busy != nil)
    }

    /// No scroll view around the options: nothing may stretch them. The typing steps keep one,
    /// in case the keyboard leaves less room than the content needs on a small phone.
    @ViewBuilder
    private var container: some View {
        if mode == .options {
            content
        } else {
            ScrollView {
                content
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }

    /// Everything the sheet shows, at its natural height, with a reader behind it.
    private var content: some View {
        VStack(alignment: .leading, spacing: 14) {
            header
            switch mode {
            case .options: optionsSection
            case .email: emailSection
            case .code: codeSection
            }
            messages
            terms
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 26)
        .padding(.bottom, 12)
        .fixedSize(horizontal: false, vertical: true)
        .background(
            GeometryReader { geo in
                Color.clear
                    .onAppear { fit(geo.size.height) }
                    .onChange(of: geo.size.height) { _, newHeight in
                        fit(newHeight)
                    }
            }
        )
    }

    /// Only the options step drives the height of the sheet.
    private func fit(_ height: CGFloat) {
        guard mode == .options, height > 100 else { return }
        let rounded = height.rounded(.up)
        if abs(rounded - fittedHeight) >= 1 {
            fittedHeight = rounded
        }
    }

    // MARK: Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            if mode != .options {
                Button(action: { goBack() }) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 14, weight: .semibold))
                        Text(L("Back"))
                            .font(.system(size: 15, weight: .medium))
                    }
                    .foregroundStyle(Theme.ink)
                    .frame(height: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(busy != nil)
            }
            Text(headerTitle)
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.ink)
            Text(headerSubtitle)
                .font(.system(size: 14))
                .foregroundStyle(Theme.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var headerTitle: String {
        switch mode {
        case .options: return L("Save your plan")
        case .email: return L("Continue with e-mail")
        case .code: return L("Enter your code")
        }
    }

    private var headerSubtitle: String {
        switch mode {
        case .options: return L("Choose how to sign in. It takes ten seconds.")
        case .email: return L("We'll send you a 6-digit code. No password to remember.")
        case .code: return L("We sent a 6-digit code to %@.", AuthService.normalized(email))
        }
    }

    private var optionsSection: some View {
        VStack(spacing: 10) {
            AuthProviderButton(title: L("Continue with Apple"), dark: true, loading: busy == .apple,
                               action: { signIn(with: .apple) }) {
                Image(systemName: "apple.logo")
                    .font(.system(size: 19, weight: .medium))
                    .accessibilityHidden(true)
            }
            AuthProviderButton(title: L("Continue with Google"), loading: busy == .google,
                               action: { signIn(with: .google) }) {
                AuthGoogleMark()
            }
            AuthProviderButton(title: L("Continue with e-mail"), action: { showEmail() }) {
                Image(systemName: "envelope")
                    .font(.system(size: 17, weight: .medium))
                    .accessibilityHidden(true)
            }
            Button(action: { continueAsGuest() }) {
                Text(L("Continue without an account"))
                    .font(.system(size: 14, weight: .medium))
                    .underline()
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .disabled(busy != nil)
    }

    private var emailSection: some View {
        VStack(spacing: 12) {
            AuthFieldBox {
                TextField(L("you@example.com"), text: $email)
                    .font(.system(size: 16))
                    .foregroundStyle(Theme.ink)
                    .keyboardType(.emailAddress)
                    .textContentType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.send)
                    .focused($focus, equals: .email)
                    .onSubmit { sendCode() }
                    .accessibilityLabel(L("E-mail address"))
            }
            AuthSubmitButton(title: L("Send me a code"),
                             enabled: AuthService.isValidEmail(AuthService.normalized(email)),
                             loading: busy == .sendingCode,
                             action: { sendCode() })
        }
    }

    private var codeSection: some View {
        VStack(spacing: 12) {
            AuthFieldBox {
                TextField("000000", text: $code)
                    .font(.system(size: 22, weight: .semibold, design: .monospaced))
                    .foregroundStyle(Theme.ink)
                    .multilineTextAlignment(.center)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($focus, equals: .code)
                    .accessibilityLabel(L("6-digit code"))
                    .onChange(of: code) { _, newValue in
                        let digits = String(newValue.filter { $0.isNumber }.prefix(6))
                        if digits != newValue {
                            code = digits
                        } else if digits.count == 6 {
                            verifyCode()
                        }
                    }
            }
            AuthSubmitButton(title: L("Confirm"), enabled: code.count == 6, loading: busy == .verifyingCode,
                             action: { verifyCode() })
            Button(action: { resendCode() }) {
                Text(busy == .sendingCode ? L("Sending…") : L("Send a new code"))
                    .font(.system(size: 14, weight: .medium))
                    .underline()
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(busy != nil)
        }
    }

    @ViewBuilder
    private var messages: some View {
        if let errorText {
            Text(errorText)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Theme.orangeDeep)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Theme.orangeTint, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        } else if let infoText {
            Text(infoText)
                .font(.system(size: 13))
                .foregroundStyle(Theme.green)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var terms: some View {
        Text(LocalizedStringKey(L("By continuing, you accept the [terms of use](https://vikstudios.com/atala/terms) and the [privacy policy](https://vikstudios.com/atala).")))
            .font(.system(size: 12))
            .foregroundStyle(Theme.muted)
            .tint(Theme.ink)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity)
    }

    // MARK: Actions

    private func goBack() {
        Haptics.tap()
        errorText = nil
        infoText = nil
        switch mode {
        case .code:
            code = ""
            mode = .email
            focus = .email
        default:
            focus = nil
            mode = .options
        }
    }

    private func showEmail() {
        errorText = nil
        infoText = nil
        mode = .email
        focus = .email
    }

    private func continueAsGuest() {
        guard busy == nil else { return }
        Haptics.tap()
        onSignedIn(AuthService.shared.continueAsGuest())
    }

    private func signIn(with provider: AuthSheetBusy) {
        guard busy == nil else { return }
        busy = provider
        errorText = nil
        infoText = nil
        Task {
            do {
                let account: Account
                if provider == .apple {
                    account = try await AuthService.shared.signInWithApple()
                } else {
                    account = try await AuthService.shared.signInWithGoogle()
                }
                busy = nil
                Haptics.success()
                onSignedIn(account)
            } catch {
                busy = nil
                show(error)
            }
        }
    }

    private func sendCode() {
        guard busy == nil else { return }
        guard AuthService.isValidEmail(AuthService.normalized(email)) else {
            show(AuthError.invalidEmail)
            return
        }
        busy = .sendingCode
        errorText = nil
        infoText = nil
        Task {
            do {
                try await AuthService.shared.sendEmailCode(to: email)
                busy = nil
                code = ""
                mode = .code
                focus = .code
            } catch {
                busy = nil
                show(error)
            }
        }
    }

    private func resendCode() {
        guard busy == nil else { return }
        Haptics.tap()
        busy = .sendingCode
        errorText = nil
        infoText = nil
        Task {
            do {
                try await AuthService.shared.sendEmailCode(to: email)
                busy = nil
                code = ""
                infoText = L("A new code is on its way.")
            } catch {
                busy = nil
                show(error)
            }
        }
    }

    private func verifyCode() {
        guard busy == nil, code.count == 6 else { return }
        busy = .verifyingCode
        errorText = nil
        infoText = nil
        Task {
            do {
                let account = try await AuthService.shared.verifyEmailCode(email: email, code: code)
                busy = nil
                focus = nil
                Haptics.success()
                onSignedIn(account)
            } catch {
                busy = nil
                show(error)
            }
        }
    }

    private func show(_ error: Error) {
        if let authError = error as? AuthError {
            if authError == .cancelled { return }
            errorText = authError.errorDescription
        } else {
            errorText = L("Something went wrong. Please try again.")
        }
        Haptics.error()
    }
}

#Preview {
    Color.white
        .sheet(isPresented: .constant(true)) {
            SignInOptionsSheet(onSignedIn: { _ in })
        }
}
