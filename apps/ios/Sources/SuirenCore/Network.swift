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
        guard let url = URL(string: base)?.appendingPathComponent(path) else { throw SuirenError("服务器地址无效") }
        var r = URLRequest(url: url); r.httpMethod = body == nil ? "GET" : "POST"; r.httpBody = body; r.timeoutInterval = timeout
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil { r.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        if let token { r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        do {
            let (data, response) = try await session.data(for: r)
            guard let http = response as? HTTPURLResponse else { throw SuirenError("服务器响应无效") }
            guard (200...299).contains(http.statusCode) else {
                let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
                let code = object?["code"] as? String ?? object?["error"] as? String ?? ""
                if http.statusCode == 401 {
                    if token == nil {
                        if code == "account_disabled" { throw SuirenError("账号已被停用，请联系管理员") }
                        throw SuirenError(path == "healthz" ? "健康接口返回未授权，请检查服务器地址" : "账号或密码不正确")
                    }
                    throw SuirenError("当前服务器无法验证账号，请先显式退出账号，再重新登录")
                }
                if http.statusCode == 429 { throw SuirenError("尝试过于频繁，请稍后重试") }
                if code == "registration_closed" { throw SuirenError("服务器已关闭新账号注册，请联系管理员") }
                if http.statusCode == 409 && path == "api/v1/auth/register" { throw SuirenError("账号已存在，请登录或更换账号名") }
                if code == "account_disabled" { throw SuirenError("账号已被停用，请联系管理员") }
                if (300...399).contains(http.statusCode) { throw SuirenError("服务器返回重定向，请填写最终服务器地址") }
                throw SuirenError("请求失败（HTTP \(http.statusCode)）\(code.isEmpty ? "" : "：" + String(code.prefix(100)))")
            }
            return data
        } catch let error as URLError {
            if error.code == .cancelled { throw CancellationError() }
            throw SuirenError(error.code == .timedOut ? "同步超时，待发送批次已保留" : "连接失败，请检查网络和服务器地址；本机数据已保留")
        }
    }
    public func health(serverURL: String) async throws {
        // Fresh unauthenticated request; redirects are not followed and no settings are saved.
        let data = try await request(base: serverURL, path: "healthz", timeout: 10)
        let object = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard object?["status"] as? String == "ok" else { throw SuirenError("健康接口未返回 status=ok") }
    }
    public func authenticate(serverURL: String, username: String, password: String, confirmation: String? = nil) async throws -> Credential {
        try AccountValidation.validate(username: username, password: password, confirmation: confirmation)
        let name = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let data = try await request(base: serverURL, path: confirmation == nil ? "api/v1/auth/login" : "api/v1/auth/register",
                                     body: JSONSerialization.data(withJSONObject: ["username": name, "password": password]))
        struct Token: Decodable { var access_token: String; var token_type: String; var expires_at: String }
        let token = try JSONDecoder().decode(Token.self, from: data)
        guard !token.access_token.isEmpty, token.token_type.lowercased() == "bearer" else { throw SuirenError("服务器未返回有效凭据") }
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
                throw SuirenError("服务器尚不支持卖出价格，请先升级服务器；本机数据和批次已保留")
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
            throw SuirenError("同步配置或账号已改变，原批次已保留；请先退出账号后重新登录")
        }
    }
    public func sync() async throws {
        guard !running else { throw SuirenError("同步正在进行") }; running = true; defer { running = false }
        guard let credential = try await credentials.read() else { throw SuirenError("请先登录同步账号") }
        try await validate(credential)
        let deadline = Date().addingTimeInterval(60)
        var pulled = false
        while true {
            try Task.checkCancellation(); try await validate(credential)
            guard Date() < deadline else { throw SuirenError("同步超时，批次已保留供重试") }
            guard let batch = try await store.freezeBatch(alreadyPulled: pulled) else { break }
            try await validate(credential)
            let response = try await transport.exchange(batch.request, credential: credential)
            try Task.checkCancellation(); try await validate(credential)
            try await store.apply(response, batch: batch, target: credential.target); pulled = true
        }
        try await validate(credential); try await store.markSynced(target: credential.target)
    }
}
