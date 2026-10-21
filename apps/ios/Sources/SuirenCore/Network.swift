import Foundation

public struct Credential: Codable, Equatable, Sendable {
    public var serverURL: String
    public var username: String
    public var token: String
    public var expiresAt: String
    public init(serverURL: String, username: String, token: String, expiresAt: String) {
        self.serverURL = serverURL; self.username = username; self.token = token; self.expiresAt = expiresAt
    }
    public var target: String { "\(serverURL)|\(username)" }
}
public protocol CredentialStore: Sendable {
    func read() async throws -> Credential?
    func write(_ credential: Credential?) async throws
}
public protocol SyncTransport: Sendable {
    func exchange(_ request: SyncRequest, credential: Credential) async throws -> SyncResponse
}

private final class NoRedirect: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}

public final class HTTPClient: SyncTransport, @unchecked Sendable {
    private let session: URLSession
    public convenience init() { self.init(configuration: .ephemeral) }
    init(configuration config: URLSessionConfiguration) {
        config.timeoutIntervalForRequest = 60; config.timeoutIntervalForResource = 60
        config.httpCookieStorage = nil; config.urlCache = nil; config.urlCredentialStorage = nil
        session = URLSession(configuration: config, delegate: NoRedirect(), delegateQueue: nil)
    }
    deinit { session.invalidateAndCancel() }
    private func request(base: String, path: String, body: Data? = nil, token: String? = nil, timeout: TimeInterval = 60) async throws -> Data {
        guard let url = URL(string: base)?.appendingPathComponent(path) else { throw SuirenError(.invalid_server_address) }
        var r = URLRequest(url: url); r.httpMethod = body == nil ? "GET" : "POST"; r.httpBody = body; r.timeoutInterval = timeout
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil { r.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        if let token { r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        do {
            let (data, response) = try await session.data(for: r)
            guard let http = response as? HTTPURLResponse else { throw SuirenError(.invalid_server_response) }
            guard (200...299).contains(http.statusCode) else {
                let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
                let candidate = object?["code"] as? String ?? ""
                let code = candidate.utf8.count <= 64 && candidate.range(of: #"\A[a-z][a-z0-9_]{0,63}\z"#, options: .regularExpression) != nil ? candidate : ""
                if http.statusCode == 401 {
                    if token == nil {
                        if code == "account_disabled" { throw SuirenError(.this_account_is_disabled_contact_the_administrator) }
                        throw SuirenError(path == "healthz" ? .the_health_endpoint_returned_unauthorized_check_the_server_address : .incorrect_account_or_password)
                    }
                    throw SuirenError(.the_current_server_cannot_verify_this_account_explicitly_sign_out)
                }
                if http.statusCode == 429 { throw SuirenError(.too_many_attempts_try_again_later) }
                if code == "registration_closed" { throw SuirenError(.new_account_registration_is_closed_contact_the_administrator) }
                if http.statusCode == 409 && path == "api/v1/auth/register" { throw SuirenError(.this_account_already_exists_sign_in_or_choose_another_name) }
                if code == "account_disabled" { throw SuirenError(.this_account_is_disabled_contact_the_administrator) }
                if (300...399).contains(http.statusCode) { throw SuirenError(.the_server_returned_a_redirect_enter_the_final_server_address) }
                throw SuirenError(.request_failed_http, arguments: [String(describing: http.statusCode), code.isEmpty ? "" : " [" + code + "]"])
            }
            return data
        } catch let error as URLError {
            if error.code == .cancelled { throw CancellationError() }
            throw SuirenError(error.code == .timedOut ? .sync_timed_out_the_pending_batch_was_kept : .connection_failed_check_the_network_and_server_address_local_data)
        }
    }
    public func health(serverURL: String) async throws {
        // Fresh unauthenticated request; redirects are not followed and no settings are saved.
        let data = try await request(base: serverURL, path: "healthz", timeout: 10)
        let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard object?["status"] as? String == "ok" else { throw SuirenError(.the_health_endpoint_did_not_return_status_ok) }
    }
    public func authenticate(serverURL: String, username: String, password: String, confirmation: String? = nil) async throws -> Credential {
        try AccountValidation.validate(username: username, password: password, confirmation: confirmation)
        let name = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let data = try await request(base: serverURL, path: confirmation == nil ? "api/v1/auth/login" : "api/v1/auth/register",
                                     body: JSONSerialization.data(withJSONObject: ["username": name, "password": password]))
        struct Token: Decodable { var access_token: String; var token_type: String; var expires_at: String }
        let token = try JSONDecoder().decode(Token.self, from: data)
        guard !token.access_token.isEmpty, token.token_type.lowercased() == "bearer" else { throw SuirenError(.the_server_did_not_return_valid_credentials) }
        return Credential(serverURL: serverURL, username: name, token: token.access_token, expiresAt: token.expires_at)
    }
    public func logout(_ credential: Credential) async {
        _ = try? await request(base: credential.serverURL, path: "api/v1/auth/logout", body: Data("{}".utf8), token: credential.token, timeout: 3)
    }
    public func exchange(_ request: SyncRequest, credential: Credential) async throws -> SyncResponse {
        if request.changes.contains(where: { !$0.deleted && $0.sale_price_cents != nil }) {
            let health = try await self.request(base: credential.serverURL, path: "healthz", timeout: 10)
            let capabilities = try JSONSerialization.jsonObject(with: health) as? [String: Any]
            guard capabilities?["asset_sale_price"] as? Bool == true else {
                throw SuirenError(.the_server_does_not_support_sale_prices_yet_upgrade_the)
            }
        }
        try Task.checkCancellation()
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys]
        let data = try await self.request(base: credential.serverURL, path: "api/v1/sync/assets", body: encoder.encode(request), token: credential.token)
        return try JSONDecoder().decode(SyncResponse.self, from: data)
    }
}

/// Serializes network sync, while allowing local writes during an HTTP request.
public actor SyncCoordinator {
    private let store: LocalStore
    private let credentials: any CredentialStore
    private let transport: any SyncTransport
    private var running = false
    public init(store: LocalStore, credentials: any CredentialStore, transport: any SyncTransport) {
        self.store = store; self.credentials = credentials; self.transport = transport
    }
    private func validate(_ credential: Credential) async throws {
        let snapshot = try await store.snapshot()
        guard snapshot.settings.syncEnabled, snapshot.settings.activeURL == credential.serverURL,
              snapshot.session?.target == credential.target, try await credentials.read() == credential else {
            throw SuirenError(.sync_settings_or_account_changed_the_original_batch_was_kept)
        }
    }
    public func sync() async throws {
        guard !running else { throw SuirenError(.sync_is_already_running) }; running = true; defer { running = false }
        guard let credential = try await credentials.read() else { throw SuirenError(.sign_in_to_a_sync_account_first) }
        try await validate(credential)
        let deadline = Date().addingTimeInterval(60)
        var pulled = false
        while true {
            try Task.checkCancellation(); try await validate(credential)
            guard Date() < deadline else { throw SuirenError(.sync_timed_out_the_batch_was_kept_for_retry) }
            guard let batch = try await store.freezeBatch(alreadyPulled: pulled) else { break }
            try await validate(credential)
            let response = try await transport.exchange(batch.request, credential: credential)
            try Task.checkCancellation(); try await validate(credential)
            try await store.apply(response, batch: batch, target: credential.target); pulled = true
        }
        try await validate(credential); try await store.markSynced(target: credential.target)
    }
}
