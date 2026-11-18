import Foundation

enum SupabaseError: Error {
    case notConfigured
    /// No connection, timeout, DNS failure…
    case network
    case http(status: Int, message: String)
}

/// Raw answer of a Supabase endpoint. HTTP errors are not thrown: check `isSuccess` or call `validated()`.
struct SupabaseResponse {
    let status: Int
    let data: Data

    var isSuccess: Bool { (200..<300).contains(status) }

    /// The body as a JSON object, or an empty dictionary.
    var json: [String: Any] {
        guard !data.isEmpty, let object = try? JSONSerialization.jsonObject(with: data) else { return [:] }
        return object as? [String: Any] ?? [:]
    }

    /// The body as a list of JSON objects (PostgREST answers), or an empty list.
    var jsonRows: [[String: Any]] {
        guard !data.isEmpty, let object = try? JSONSerialization.jsonObject(with: data) else { return [] }
        return object as? [[String: Any]] ?? []
    }

    /// Machine-readable error code: GoTrue `error_code`, PostgREST `code`, edge functions `error`.
    var errorCode: String {
        let body = json
        for key in ["error_code", "code", "error"] {
            if let text = body[key] as? String, !text.isEmpty { return text }
        }
        return ""
    }

    /// Best human-readable message found in an error body (GoTrue, PostgREST and edge functions differ).
    var errorMessage: String {
        let body = json
        for key in ["msg", "error_description", "message", "error"] {
            if let text = body[key] as? String, !text.isEmpty { return text }
        }
        return "Request failed (\(status))."
    }

    func validated() throws -> SupabaseResponse {
        guard isSuccess else { throw SupabaseError.http(status: status, message: errorMessage) }
        return self
    }
}

/// Minimal REST client for Supabase (auth, PostgREST, edge functions) built on URLSession.
enum SupabaseClient {
    /// No cache, no cookies: every answer comes from the server, and nothing is written to disk.
    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        configuration.httpShouldSetCookies = false
        configuration.httpCookieAcceptPolicy = .never
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForResource = 120
        return URLSession(configuration: configuration)
    }()

    /// JSON body from a dictionary or an array.
    static func encode(_ object: Any) -> Data? {
        guard JSONSerialization.isValidJSONObject(object) else { return nil }
        return try? JSONSerialization.data(withJSONObject: object)
    }

    static func url(path: String, query: [URLQueryItem] = []) -> URL? {
        guard var components = URLComponents(string: SupabaseConfig.urlString + path) else { return nil }
        if !query.isEmpty {
            components.queryItems = query
            // URLComponents leaves "+" as it is, and servers read it as a space (dates, e-mail addresses).
            if let encoded = components.percentEncodedQuery {
                components.percentEncodedQuery = encoded.replacingOccurrences(of: "+", with: "%2B")
            }
        }
        return components.url
    }

    /// Sends a request with the `apikey` header and, when given, the user's bearer token.
    static func send(path: String,
                     method: String = "GET",
                     query: [URLQueryItem] = [],
                     body: Data? = nil,
                     accessToken: String? = nil,
                     prefer: String? = nil,
                     timeout: TimeInterval = 30) async throws -> SupabaseResponse {
        guard SupabaseConfig.isConfigured, let url = Self.url(path: path, query: query) else {
            throw SupabaseError.notConfigured
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = timeout
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue(SupabaseConfig.publishableKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("atala-ios", forHTTPHeaderField: "X-Client-Info")
        if let accessToken {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        if let prefer {
            request.setValue(prefer, forHTTPHeaderField: "Prefer")
        }
        if let body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        // Reads and upserts can be repeated safely; anything else is sent once.
        let repeatable = method == "GET" || (prefer ?? "").contains("merge-duplicates")
        var attempt = 0
        while true {
            attempt += 1
            do {
                let (data, response) = try await session.data(for: request)
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                return SupabaseResponse(status: status, data: data)
            } catch {
                // iOS drops idle connections when the app comes back: one more try is enough.
                let lost = (error as? URLError)?.code == .networkConnectionLost
                if lost, repeatable, attempt == 1 { continue }
                throw SupabaseError.network
            }
        }
    }
}
