import Foundation
import UIKit
import AuthenticationServices
import CryptoKit

// MARK: - Presentation anchor

@MainActor
enum AuthPresentation {
    /// The window the system sign-in sheets are attached to.
    static func anchor() -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let active = scenes.filter { $0.activationState == .foregroundActive }
        for scene in active + scenes {
            if let window = scene.windows.first(where: { $0.isKeyWindow }) ?? scene.windows.first {
                return window
            }
        }
        return ASPresentationAnchor()
    }
}

// MARK: - Apple

/// What we keep from the Apple credential (plain values, safe to pass between tasks).
struct AuthAppleCredential {
    let idToken: String
    let email: String?
}

/// Bridges the delegate callbacks of `ASAuthorizationController` to async/await.
final class AuthAppleCoordinator: NSObject, ASAuthorizationControllerDelegate,
                                  ASAuthorizationControllerPresentationContextProviding {
    private let anchor: ASPresentationAnchor
    private var continuation: CheckedContinuation<AuthAppleCredential, Error>?

    init(anchor: ASPresentationAnchor) {
        self.anchor = anchor
        super.init()
    }

    func begin(_ continuation: CheckedContinuation<AuthAppleCredential, Error>) {
        self.continuation = continuation
    }

    private func finish(_ result: Result<AuthAppleCredential, Error>) {
        guard let pending = continuation else { return }
        continuation = nil
        pending.resume(with: result)
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        anchor
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else {
            finish(.failure(AuthError.appleFailed))
            return
        }
        finish(.success(AuthAppleCredential(idToken: token, email: credential.email)))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        var cancelled = false
        if let authorizationError = error as? ASAuthorizationError, authorizationError.code == .canceled {
            cancelled = true
        }
        finish(.failure(cancelled ? AuthError.cancelled : AuthError.appleFailed))
    }
}

// MARK: - Google (web)

final class AuthWebCoordinator: NSObject, ASWebAuthenticationPresentationContextProviding {
    private let anchor: ASPresentationAnchor

    init(anchor: ASPresentationAnchor) {
        self.anchor = anchor
        super.init()
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        anchor
    }
}

// MARK: - Providers

extension AuthService {
    /// Native Sign in with Apple, then exchange of the identity token with Supabase.
    func signInWithApple() async throws -> Account {
        guard SupabaseConfig.isConfigured else { throw AuthError.notConfigured }
        let rawNonce = Self.randomNonce()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(rawNonce)

        let coordinator = AuthAppleCoordinator(anchor: AuthPresentation.anchor())
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = coordinator
        controller.presentationContextProvider = coordinator
        appleCoordinator = coordinator
        defer { appleCoordinator = nil }

        let credential: AuthAppleCredential = try await withCheckedThrowingContinuation { continuation in
            coordinator.begin(continuation)
            controller.performRequests()
        }

        let payload: [String: Any] = ["provider": "apple", "id_token": credential.idToken, "nonce": rawNonce]
        let json = try await authRequest(path: "/auth/v1/token", method: "POST",
                                         query: [URLQueryItem(name: "grant_type", value: "id_token")],
                                         body: SupabaseClient.encode(payload))
        guard var account = Self.account(fromSession: json) else { throw AuthError.appleFailed }
        if account.email == nil { account.email = credential.email }
        noteSignedIn()
        rememberProvider(.apple)
        return account
    }

    /// Google through the Supabase hosted page, in the system web sign-in sheet.
    /// Uses the PKCE flow: the callback carries a one-time code, exchanged here for the session.
    func signInWithGoogle() async throws -> Account {
        let verifier = Self.randomNonce(length: 64)
        guard SupabaseConfig.isConfigured,
              let url = SupabaseClient.url(path: "/auth/v1/authorize", query: [
                URLQueryItem(name: "provider", value: "google"),
                URLQueryItem(name: "redirect_to", value: SupabaseConfig.redirectURL),
                URLQueryItem(name: "code_challenge", value: Self.pkceChallenge(for: verifier)),
                URLQueryItem(name: "code_challenge_method", value: "s256"),
              ]) else { throw AuthError.notConfigured }

        // Only one web sign-in at a time.
        finishWebSignIn(.failure(AuthError.cancelled))

        let coordinator = AuthWebCoordinator(anchor: AuthPresentation.anchor())
        webCoordinator = coordinator
        defer {
            webCoordinator = nil
            webSession = nil
        }

        let callbackURL: URL = try await withCheckedThrowingContinuation { continuation in
            webContinuation = continuation
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: SupabaseConfig.callbackScheme) { callback, error in
                let result: Result<URL, AuthError>
                if let callback {
                    result = .success(callback)
                } else if let sessionError = error as? ASWebAuthenticationSessionError,
                          sessionError.code == .canceledLogin {
                    result = .failure(.cancelled)
                } else {
                    let reason: String = (error as NSError?).map { $0.domain + " " + String($0.code) } ?? "no callback"
                    result = .failure(.server(L("Sign in with Google didn't work. Please try again.") + " (" + reason + ")"))
                }
                Task { @MainActor in
                    switch result {
                    case .success(let callback): AuthService.shared.finishWebSignIn(.success(callback))
                    case .failure(let failure): AuthService.shared.finishWebSignIn(.failure(failure))
                    }
                }
            }
            session.presentationContextProvider = coordinator
            session.prefersEphemeralWebBrowserSession = false
            webSession = session
            if !session.start() {
                finishWebSignIn(.failure(AuthError.googleFailed))
            }
        }

        let account = try await account(fromCallback: callbackURL, verifier: verifier)
        noteSignedIn()
        rememberProvider(.google)
        return account
    }

    /// Called from `.onOpenURL`: finishes a web sign-in if the app was opened with `atala://auth-callback`.
    nonisolated func handleCallback(_ url: URL) {
        guard url.scheme?.lowercased() == SupabaseConfig.callbackScheme else { return }
        Task { @MainActor in
            let service = AuthService.shared
            guard service.webContinuation != nil else { return }
            service.finishWebSignIn(.success(url))
            service.webSession?.cancel()
        }
    }

    /// Resumes the pending web sign-in exactly once.
    func finishWebSignIn(_ result: Result<URL, Error>) {
        guard let pending = webContinuation else { return }
        webContinuation = nil
        pending.resume(with: result)
    }

    /// Turns the callback into an account: exchanges the one-time code (PKCE), or reads the tokens
    /// from the fragment when the server answered with the implicit flow.
    private func account(fromCallback url: URL, verifier: String) async throws -> Account {
        let values = Self.callbackValues(url)
        if let code = values["code"], !code.isEmpty {
            let payload: [String: Any] = ["auth_code": code, "code_verifier": verifier]
            let json: [String: Any]
            do {
                json = try await authRequest(path: "/auth/v1/token", method: "POST",
                                             query: [URLQueryItem(name: "grant_type", value: "pkce")],
                                             body: SupabaseClient.encode(payload))
            } catch let error as AuthError {
                // Keeps the server's reason next to the message: it tells which setting is wrong.
                if case .server(let reason) = error { throw Self.googleFailure(detail: "token: " + reason) }
                throw error
            }
            guard let account = Self.account(fromSession: json) else { throw AuthError.googleFailed }
            return account
        }
        guard let token = values["access_token"], !token.isEmpty else {
            let failure = (values["error_code"] ?? "") + " " + (values["error_description"] ?? "")
            let text = failure.replacingOccurrences(of: "+", with: " ").lowercased()
            if text.contains("provider") && (text.contains("not enabled") || text.contains("unsupported")) {
                throw AuthError.providerUnavailable
            }
            if values["error"] == "access_denied" { throw AuthError.cancelled }
            let reason: String = failure.replacingOccurrences(of: "+", with: " ")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            throw Self.googleFailure(detail: reason.isEmpty ? "no code in the callback" : reason)
        }
        let user = try await authRequest(path: "/auth/v1/user", method: "GET", accessToken: token)
        guard let userID = user["id"] as? String else { throw AuthError.googleFailed }
        let seconds = Double(values["expires_in"] ?? "") ?? 3600
        return Account(userID: userID, email: user["email"] as? String, isGuest: false,
                       accessToken: token, refreshToken: values["refresh_token"],
                       expiresAt: Date().addingTimeInterval(seconds))
    }

    /// "Sign in with Google didn't work", followed by what the server said.
    private static func googleFailure(detail: String) -> AuthError {
        let base: String = L("Sign in with Google didn't work. Please try again.")
        return AuthError.server(base + " (" + String(detail.prefix(160)) + ")")
    }

    /// Key/value pairs of the URL fragment (tokens) and query (errors).
    static func callbackValues(_ url: URL) -> [String: String] {
        var values: [String: String] = [:]
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return values }
        for part in [components.percentEncodedQuery, components.percentEncodedFragment] {
            guard let part, !part.isEmpty else { continue }
            var parser = URLComponents()
            parser.percentEncodedQuery = part
            for item in parser.queryItems ?? [] {
                if let value = item.value { values[item.name] = value }
            }
        }
        return values
    }

    // MARK: Nonce

    static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var generator = SystemRandomNumberGenerator()
        var result = ""
        for _ in 0..<length {
            let index = Int.random(in: 0..<charset.count, using: &generator)
            result.append(charset[index])
        }
        return result
    }

    /// PKCE challenge: SHA-256 of the verifier, base64url without padding.
    static func pkceChallenge(for verifier: String) -> String {
        let digest = SHA256.hash(data: Data(verifier.utf8))
        return Data(digest).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func sha256(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
