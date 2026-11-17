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
        case .server(let message): return message
        }
    }
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

    private var refreshTask: Task<Account?, Never>?

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
        do {
            let json = try await authRequest(path: "/auth/v1/verify", method: "POST",
                                             body: SupabaseClient.encode(payload))
            guard let account = Self.account(fromSession: json) else { throw AuthError.invalidCode }
            return account
        } catch AuthError.server(_) {
            throw AuthError.invalidCode
        }
    }

    // MARK: Guest

    /// Dev-only local account: nothing leaves the phone.
    func continueAsGuest() -> Account {
        Account(userID: "guest-" + UUID().uuidString, email: nil, isGuest: true,
                accessToken: nil, refreshToken: nil, expiresAt: nil)
    }

    // MARK: Session

    /// A usable access token for the signed-in account, refreshed when it is about to expire.
    /// Returns nil for a guest, when signed out, or when the session can no longer be refreshed.
    func validAccessToken(store: AppStore) async -> String? {
        guard let account = store.account, !account.isGuest, let token = account.accessToken else { return nil }
        if let expiresAt = account.expiresAt, expiresAt.timeIntervalSinceNow > 60 { return token }
        guard let refreshToken = account.refreshToken else {
            return account.expiresAt == nil ? token : nil
        }
        // Refresh tokens are single-use: concurrent callers share one refresh.
        if let running = refreshTask {
            return await running.value?.accessToken
        }
        let previous = account
        let task = Task<Account?, Never> { [weak self] in
            await self?.refreshSession(refreshToken: refreshToken, previous: previous)
        }
        refreshTask = task
        let refreshed = await task.value
        refreshTask = nil
        guard let refreshed else { return nil }
        if let current = store.account, !current.isGuest, current.userID == refreshed.userID {
            store.account = refreshed
        }
        return refreshed.accessToken
    }

    private func refreshSession(refreshToken: String, previous: Account) async -> Account? {
        let payload: [String: Any] = ["refresh_token": refreshToken]
        guard let json = try? await authRequest(path: "/auth/v1/token", method: "POST",
                                                query: [URLQueryItem(name: "grant_type", value: "refresh_token")],
                                                body: SupabaseClient.encode(payload)) else { return nil }
        if let account = Self.account(fromSession: json) { return account }
        guard let token = json["access_token"] as? String else { return nil }
        var account = previous
        account.accessToken = token
        account.refreshToken = (json["refresh_token"] as? String) ?? refreshToken
        account.expiresAt = Self.expiry(from: json)
        return account
    }

    func signOut(store: AppStore) async {
        if let token = await validAccessToken(store: store) {
            _ = try? await SupabaseClient.send(path: "/auth/v1/logout", method: "POST", accessToken: token, timeout: 10)
        }
        store.signOut()
    }

    /// Deletes the server data and the account when signed in, then erases the phone.
    func deleteAccount(store: AppStore) async throws {
        if let account = store.account, !account.isGuest {
            guard let token = await validAccessToken(store: store) else { throw AuthError.sessionExpired }
            let response: SupabaseResponse
            do {
                response = try await SupabaseClient.send(path: "/functions/v1/delete-account", method: "POST",
                                                         body: SupabaseClient.encode([String: Any]()),
                                                         accessToken: token)
            } catch {
                throw Self.map(error)
            }
            guard response.isSuccess else {
                if response.status == 401 { throw AuthError.sessionExpired }
                throw AuthError.server("We couldn't delete your account. Please try again in a moment.")
            }
        }
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
        guard response.isSuccess else {
            if response.status == 429 { throw AuthError.tooManyAttempts }
            throw AuthError.server(response.errorMessage)
        }
        return response.json
    }

    static func map(_ error: Error) -> AuthError {
        if let authError = error as? AuthError { return authError }
        if let supabaseError = error as? SupabaseError {
            switch supabaseError {
            case .notConfigured: return .notConfigured
            case .network: return .network
            case .http(let status, let message): return status == 429 ? .tooManyAttempts : .server(message)
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
        let seconds = (json["expires_in"] as? NSNumber)?.doubleValue ?? 3600
        return Date().addingTimeInterval(seconds)
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
