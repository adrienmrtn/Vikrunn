import Foundation
import SwiftUI
import AuthenticationServices

/// Messages shown to the user when signing in fails.
enum AuthError: LocalizedError, Equatable {
    case cancelled
    case notConfigured
    case network
    case invalidEmail
    case invalidCode
    case tooManyAttempts
    case sessionExpired
    case appleFailed
    case googleFailed
    /// The chosen way of signing in is not enabled on the server yet.
    case providerUnavailable
    case server(String)

    var errorDescription: String? {
        switch self {
        case .cancelled: return "Sign-in was cancelled."
        case .notConfigured: return "Sign-in isn't available right now. Please try again later."
        case .network: return "No connection. Check your network and try again."
        case .invalidEmail: return "That e-mail address doesn't look right."
        case .invalidCode: return "That code is wrong or has expired. Check it or ask for a new one."
        case .tooManyAttempts: return "Too many attempts. Wait a minute and try again."
        case .sessionExpired: return "Your session has expired. Please sign in again."
        case .appleFailed: return "Sign in with Apple didn't work. Please try again."
        case .googleFailed: return "Sign in with Google didn't work. Please try again."
        case .providerUnavailable: return "This way of signing in isn't switched on yet. Please use another one."
        case .server(let message): return message
        }
    }
}

/// Result of asking the server for a fresh session.
enum AuthRefreshOutcome {
    case refreshed(Account)
    /// The refresh token is dead: the user must sign in again.
    case invalid
    /// No answer (offline, timeout, server error): try again later.
    case unavailable
}

/// Accounts through Supabase Auth (GoTrue REST): Apple, Google, e-mail one-time code.
@MainActor
final class AuthService: ObservableObject {
    static let shared = AuthService()

    // Kept alive while a system sign-in sheet is on screen (used by AuthServiceProviders.swift).
    var appleCoordinator: AuthAppleCoordinator?
    var webCoordinator: AuthWebCoordinator?
    var webSession: ASWebAuthenticationSession?
    var webContinuation: CheckedContinuation<URL, Error>?

    /// True when the saved session can no longer be refreshed: the user has to sign in again.
    /// Local data is kept; nothing is sent to the account meanwhile.
    @Published private(set) var sessionExpired = false

    private var refreshTask: Task<AuthRefreshOutcome, Never>?

    private init() {}

    // MARK: E-mail one-time code

    func sendEmailCode(to email: String) async throws {
        let address = Self.normalized(email)
        guard Self.isValidEmail(address) else { throw AuthError.invalidEmail }
        let payload: [String: Any] = ["email": address, "create_user": true]
        _ = try await authRequest(path: "/auth/v1/otp", method: "POST", body: SupabaseClient.encode(payload))
    }

    func verifyEmailCode(email: String, code: String) async throws -> Account {
        let address = Self.normalized(email)
        let digits = code.filter { $0.isNumber }
        guard Self.isValidEmail(address) else { throw AuthError.invalidEmail }
        guard digits.count == 6 else { throw AuthError.invalidCode }
        let payload: [String: Any] = ["type": "email", "email": address, "token": digits]
        let response: SupabaseResponse
        do {
            response = try await SupabaseClient.send(path: "/auth/v1/verify", method: "POST",
                                                     body: SupabaseClient.encode(payload))
        } catch {
            throw Self.map(error)
        }
        guard response.isSuccess else {
            let mapped = Self.error(from: response)
            // A refused code answers 4xx with various wordings: one clear message for all of them.
            if case .server = mapped, (400..<500).contains(response.status) { throw AuthError.invalidCode }
            throw mapped
        }
        guard let account = Self.account(fromSession: response.json) else { throw AuthError.invalidCode }
        sessionExpired = false
        return account
    }

    // MARK: Guest

    /// Dev-only local account: nothing leaves the phone.
    func continueAsGuest() -> Account {
        Account(userID: "guest-" + UUID().uuidString, email: nil, isGuest: true,
                accessToken: nil, refreshToken: nil, expiresAt: nil)
    }

    // MARK: Session

    /// A usable access token for the signed-in account, refreshed shortly before it expires
    /// (or right away with `forceRefresh`, after the server refused the current one).
    /// Returns nil for a guest, when signed out, offline with an expired token, or when the session is dead.
    func validAccessToken(store: AppStore, forceRefresh: Bool = false) async -> String? {
        guard let account = store.account, !account.isGuest else { return nil }
        if !forceRefresh, let token = account.accessToken,
           let expiresAt = account.expiresAt, expiresAt.timeIntervalSinceNow > 90 {
            return token
        }
        guard let refreshToken = account.refreshToken, !refreshToken.isEmpty else {
            if !forceRefresh, let token = account.accessToken, account.expiresAt == nil { return token }
            sessionExpired = true
            return nil
        }
        // Refresh tokens are single-use: concurrent callers share one refresh.
        let task: Task<AuthRefreshOutcome, Never>
        if let running = refreshTask {
            task = running
        } else {
            let previous = account
            task = Task { [weak self] in
                guard let self else { return AuthRefreshOutcome.unavailable }
                return await self.refreshSession(refreshToken: refreshToken, previous: previous)
            }
            refreshTask = task
        }
        let outcome = await task.value
        if refreshTask == task { refreshTask = nil }

        switch outcome {
        case .refreshed(let refreshed):
            guard let current = store.account, !current.isGuest, current.userID == refreshed.userID else {
                return nil
            }
            if current.accessToken != refreshed.accessToken || current.refreshToken != refreshed.refreshToken {
                store.account = refreshed
            }
            sessionExpired = false
            return refreshed.accessToken
        case .invalid:
            sessionExpired = true
            return nil
        case .unavailable:
            // Offline or server trouble: the current token may still do for a moment.
            if !forceRefresh, let token = account.accessToken,
               let expiresAt = account.expiresAt, expiresAt.timeIntervalSinceNow > 5 {
                return token
            }
            return nil
        }
    }

    private func refreshSession(refreshToken: String, previous: Account) async -> AuthRefreshOutcome {
        let payload: [String: Any] = ["refresh_token": refreshToken]
        guard let response = try? await SupabaseClient.send(
            path: "/auth/v1/token", method: "POST",
            query: [URLQueryItem(name: "grant_type", value: "refresh_token")],
            body: SupabaseClient.encode(payload), timeout: 20) else { return .unavailable }
        guard response.isSuccess else {
            // 400/401/403: the refresh token is unknown, used or revoked. Anything else may pass.
            let dead = [400, 401, 403].contains(response.status)
            return dead ? .invalid : .unavailable
        }
        let json = response.json
        if var account = Self.account(fromSession: json) {
            if account.email == nil { account.email = previous.email }
            if account.refreshToken == nil { account.refreshToken = refreshToken }
            return .refreshed(account)
        }
        guard let token = json["access_token"] as? String else { return .unavailable }
        var account = previous
        account.accessToken = token
        account.refreshToken = (json["refresh_token"] as? String) ?? refreshToken
        account.expiresAt = Self.expiry(from: json)
        return .refreshed(account)
    }

    /// Sends a request as the signed-in user. When the server refuses the token (401), the session is
    /// refreshed once and the request sent again.
    func authorizedSend(store: AppStore, path: String, method: String = "GET", query: [URLQueryItem] = [],
                        body: Data? = nil, prefer: String? = nil,
                        timeout: TimeInterval = 30) async throws -> SupabaseResponse {
        guard let token = await validAccessToken(store: store) else {
            throw sessionExpired ? AuthError.sessionExpired : AuthError.network
        }
        let first = try await SupabaseClient.send(path: path, method: method, query: query, body: body,
                                                  accessToken: token, prefer: prefer, timeout: timeout)
        guard first.status == 401 else { return first }
        var retryToken: String?
        if let current = store.account?.accessToken, current != token {
            retryToken = current // refreshed by another request in the meantime
        } else {
            retryToken = await validAccessToken(store: store, forceRefresh: true)
        }
        guard let retryToken else {
            throw sessionExpired ? AuthError.sessionExpired : AuthError.network
        }
        return try await SupabaseClient.send(path: path, method: method, query: query, body: body,
                                             accessToken: retryToken, prefer: prefer, timeout: timeout)
    }

    /// Called once a sign-in succeeded (any provider).
    func noteSignedIn() {
        sessionExpired = false
    }

    /// Saves what is pending, closes the session on the server, then clears the phone
    /// (the data stays in the account). The caller asks for confirmation first.
    func signOut(store: AppStore) async {
        if store.hasRealAccount {
            await SyncService.pushAll(store: store)
            if let token = await validAccessToken(store: store) {
                _ = try? await SupabaseClient.send(path: "/auth/v1/logout", method: "POST",
                                                   query: [URLQueryItem(name: "scope", value: "local")],
                                                   accessToken: token, timeout: 10)
            }
        }
        sessionExpired = false
        store.eraseEverything()
    }

    /// Deletes the server data and the account when signed in, then erases the phone.
    func deleteAccount(store: AppStore) async throws {
        if store.hasRealAccount {
            let response: SupabaseResponse
            do {
                response = try await authorizedSend(store: store, path: "/functions/v1/delete-account",
                                                    method: "POST",
                                                    body: SupabaseClient.encode([String: Any]()), timeout: 30)
            } catch {
                throw Self.map(error)
            }
            guard response.isSuccess else {
                if response.status == 401 { throw AuthError.sessionExpired }
                throw AuthError.server("We couldn't delete your account. Please try again in a moment.")
            }
        }
        sessionExpired = false
        store.eraseEverything()
    }

    // MARK: Requests and parsing (shared with AuthServiceProviders.swift)

    func authRequest(path: String, method: String, query: [URLQueryItem] = [], body: Data? = nil,
                     accessToken: String? = nil) async throws -> [String: Any] {
        let response: SupabaseResponse
        do {
            response = try await SupabaseClient.send(path: path, method: method, query: query, body: body,
                                                     accessToken: accessToken)
        } catch {
            throw Self.map(error)
        }
        guard response.isSuccess else { throw Self.error(from: response) }
        return response.json
    }

    /// A clear message for each kind of refusal of the auth server.
    static func error(from response: SupabaseResponse) -> AuthError {
        let code = response.errorCode
        let message = response.errorMessage.lowercased()
        if response.status == 429 || code.hasPrefix("over_") { return .tooManyAttempts }
        switch code {
        case "otp_expired", "invalid_credentials":
            return .invalidCode
        case "email_address_invalid":
            return .invalidEmail
        case "email_address_not_authorized":
            return .server("We can't send a code to this address yet. Please use Apple or Google for now.")
        case "provider_disabled", "email_provider_disabled", "otp_disabled", "signup_disabled",
             "oauth_provider_not_supported":
            return .providerUnavailable
        case "session_not_found", "session_expired", "refresh_token_not_found",
             "refresh_token_already_used", "bad_jwt", "no_authorization", "user_not_found":
            return .sessionExpired
        default:
            break
        }
        if message.contains("unsupported provider") || message.contains("provider is not enabled") {
            return .providerUnavailable
        }
        if response.status >= 500 {
            return .server("Our server is having a moment. Please try again shortly.")
        }
        return .server("That didn't work (\(response.status)). Please try again.")
    }

    static func map(_ error: Error) -> AuthError {
        if let authError = error as? AuthError { return authError }
        if let supabaseError = error as? SupabaseError {
            switch supabaseError {
            case .notConfigured: return .notConfigured
            case .network: return .network
            case .http(let status, _):
                if status == 429 { return .tooManyAttempts }
                if status == 401 { return .sessionExpired }
                return .server("That didn't work (\(status)). Please try again.")
            }
        }
        return .network
    }

    /// Builds the account from a GoTrue session (`access_token`, `refresh_token`, `expires_in`, `user`).
    static func account(fromSession json: [String: Any]) -> Account? {
        guard let token = json["access_token"] as? String,
              let user = json["user"] as? [String: Any],
              let userID = user["id"] as? String else { return nil }
        return Account(userID: userID, email: user["email"] as? String, isGuest: false,
                       accessToken: token, refreshToken: json["refresh_token"] as? String,
                       expiresAt: expiry(from: json))
    }

    static func expiry(from json: [String: Any]) -> Date {
        // `expires_in` first: it does not depend on the phone's clock being right.
        if let seconds = (json["expires_in"] as? NSNumber)?.doubleValue, seconds > 0 {
            return Date().addingTimeInterval(seconds)
        }
        if let epoch = (json["expires_at"] as? NSNumber)?.doubleValue, epoch > 1_000_000_000 {
            return Date(timeIntervalSince1970: epoch)
        }
        return Date().addingTimeInterval(3600)
    }

    static func normalized(_ email: String) -> String {
        email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    static func isValidEmail(_ email: String) -> Bool {
        let parts = email.split(separator: "@", omittingEmptySubsequences: false)
        guard parts.count == 2, !parts[0].isEmpty, !email.contains(" ") else { return false }
        let domain = parts[1]
        return domain.contains(".") && !domain.hasPrefix(".") && !domain.hasSuffix(".")
    }
}
