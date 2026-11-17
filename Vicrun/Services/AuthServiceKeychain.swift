import Foundation
import Security

/// The session tokens of the signed-in account, as they are kept in the Keychain.
struct AuthKeychainSession: Codable, Equatable {
    var userID: String
    var accessToken: String?
    var refreshToken: String?
    var expiresAt: Date?
}

/// Tiny wrapper over the Keychain: one item holding the session tokens.
/// The rest of the account (id, e-mail) stays in the app's JSON file.
enum AuthKeychain {
    private static let service = "app.atala.session"
    private static let itemName = "supabase-session"

    private static var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: itemName,
        ]
    }

    static func load() -> AuthKeychainSession? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try? decoder.decode(AuthKeychainSession.self, from: data)
    }

    @discardableResult
    static func save(_ session: AuthKeychainSession) -> Bool {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(session) else { return false }
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            // Readable while the phone is locked (sync when the app goes to the background), never backed up
            // to another device.
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let updateStatus = SecItemUpdate(baseQuery as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess { return true }
        guard updateStatus == errSecItemNotFound else { return false }
        var item = baseQuery
        for (key, value) in attributes { item[key] = value }
        return SecItemAdd(item as CFDictionary, nil) == errSecSuccess
    }

    static func clear() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}

extension Account {
    /// The tokens of a real account, ready for the Keychain. Nil for a guest or without tokens.
    var keychainSession: AuthKeychainSession? {
        guard !isGuest, accessToken != nil || refreshToken != nil else { return nil }
        return AuthKeychainSession(userID: userID, accessToken: accessToken, refreshToken: refreshToken,
                                   expiresAt: expiresAt)
    }

    /// The same account without its tokens: what is written to the JSON file.
    var withoutTokens: Account {
        var copy = self
        copy.accessToken = nil
        copy.refreshToken = nil
        copy.expiresAt = nil
        return copy
    }

    /// True for an account that syncs with the server (not a guest).
    var isReal: Bool { !isGuest }
}
