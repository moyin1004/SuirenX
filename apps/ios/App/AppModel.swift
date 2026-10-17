import SwiftUI
import Security
import BackgroundTasks
import SuirenCore

actor KeychainCredentials: CredentialStore {
    private let service: String
    init() {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-ui-testing"), let run = ProcessInfo.processInfo.environment["SUIRENX_TEST_RUN"], UUID(uuidString: run) != nil {
            service = "io.suirenx.app.session.uitests.\(run)"; return
        }
        #endif
        service = "io.suirenx.app.session"
    }
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
         kSecAttrAccount as String: "current", kSecAttrSynchronizable as String: false]
    }
    func read() throws -> Credential? {
        var q = query; q[kSecReturnData as String] = true; q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?; let status = SecItemCopyMatching(q as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw SuirenError("无法读取安全凭据（\(status)），请解锁设备后重试") }
        return try JSONDecoder().decode(Credential.self, from: data)
    }
    func write(_ credential: Credential?) throws {
        guard let credential else {
            let status = SecItemDelete(query as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else { throw SuirenError("无法清除安全凭据") }; return
        }
        let attributes: [String: Any] = [kSecValueData as String: try JSONEncoder().encode(credential),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var entry = query; attributes.forEach { entry[$0.key] = $0.value }
            guard SecItemAdd(entry as CFDictionary, nil) == errSecSuccess else { throw SuirenError("保存安全凭据失败") }
        } else if status != errSecSuccess { throw SuirenError("更新安全凭据失败") }
    }
}

@MainActor final class AppModel: ObservableObject {
    let store: LocalStore
    let credentials = KeychainCredentials()
    let http = HTTPClient()
    let coordinator: SyncCoordinator
    @Published var snapshot: LocalSnapshot?
    @Published var credential: Credential?
    @Published var error: String?
    @Published var syncing = false
    @Published var syncMessage: String?
    private var syncTask: Task<Void, Never>?
    private var networkTask: Task<Void, Error>?
    private var changeTask: Task<Void, Never>?
    private var foregroundTask: Task<Void, Never>?
    private var accountGeneration = UUID()
    private var refreshGeneration = 0
    var assets: [Asset] { snapshot?.assets ?? [] }
    var expiry: [ExpiryItem] { snapshot?.expiry ?? [] }
    var settings: AppSettings { snapshot?.settings ?? AppSettings() }
    var pendingExpiry: Int { expiry.filter { [.expired, .today, .soon].contains($0.bucket(soonDays: settings.soonDays)) }.count }
    var syncLabel: String {
        if syncing { return "同步中" }
        if !settings.syncEnabled { return "同步未开启，数据保存在本机" }
        if credential == nil { return "请先登录同步账号" }
        if let syncMessage { return syncMessage }
        if !(snapshot?.conflicts.isEmpty ?? true) { return "有记录待处理" }
        return (snapshot?.pendingCount ?? 0) > 0 ? "等待同步" : "已同步"
    }
    init(store: LocalStore) {
        self.store = store; coordinator = SyncCoordinator(store: store, credentials: credentials, transport: http)
    }
    func refresh() async {
        refreshGeneration += 1; let generation = refreshGeneration
        do {
            let next = try await store.snapshot()
            guard generation == refreshGeneration else { return }; snapshot = next
            let account = try await credentials.read()
            guard generation == refreshGeneration else { return }; credential = account
        }
        catch { self.error = error.localizedDescription }
    }
    @discardableResult func perform(_ action: () async throws -> Void) async -> Bool {
        do { try await action(); await refresh(); return true }
        catch is CancellationError { return false }
        catch { self.error = error.localizedDescription; await refresh(); return false }
    }
    @discardableResult func mutate(_ action: () async throws -> Void) async -> Bool {
        let success = await perform(action)
        if success { scheduleAfterChange() }; return success
    }
    func updateSettings(_ value: AppSettings) async {
        if value.activeURL != settings.activeURL || !value.syncEnabled { cancelSync() }
        _ = await perform { try await store.updateSettings(value) }; scheduleBackground(); startForegroundTimer()
    }
    func login(username: String, password: String, confirmation: String?) async throws {
        guard try await credentials.read() == nil else { throw SuirenError("请先显式退出当前账号") }
        guard let url = settings.activeURL else { throw SuirenError("请先在服务器地址设置中选择服务器") }
        let generation = accountGeneration
        let result = try await http.authenticate(serverURL: url, username: username, password: password, confirmation: confirmation)
        guard generation == accountGeneration, settings.activeURL == url, try await credentials.read() == nil else { throw SuirenError("账号或连接已改变，请重新登录") }
        try await credentials.write(result)
        var next = settings; next.syncEnabled = false; try await store.updateSettings(next); await refresh()
    }
    func logout() async {
        accountGeneration = UUID(); cancelSync()
        let previous = credential
        var next = settings; next.syncEnabled = false
        let success = await perform { try await store.updateSettings(next); try await credentials.write(nil) }
        if success, let previous { Task { await http.logout(previous) } }
        scheduleBackground()
    }
    func enableSync() async {
        guard let c = credential, settings.activeURL == c.serverURL else { error = "当前服务器无法验证账号，请先显式退出账号，再重新登录"; return }
        let success = await perform { try await store.enableSync(target: c.target) }
        if success { syncNow(); scheduleBackground(); startForegroundTimer() }
    }
    func syncNow() {
        guard syncTask == nil else { return }
        syncTask = Task { await runSync(); syncTask = nil }
    }
    func runSync() async {
        guard !syncing else { return }; syncing = true; syncMessage = nil
        let request = Task { try await coordinator.sync() }; networkTask = request
        defer { syncing = false; networkTask = nil }
        do { try await withTaskCancellationHandler(operation: { try await request.value }, onCancel: { request.cancel() }) }
        catch is CancellationError { syncMessage = "同步已停止，待发送批次已保留" }
        catch { syncMessage = error.localizedDescription }
        await refresh()
    }
    func cancelSync() { changeTask?.cancel(); syncTask?.cancel(); networkTask?.cancel() }
    private func scheduleAfterChange() {
        guard settings.syncEnabled, settings.syncAfterChanges else { return }
        changeTask?.cancel()
        changeTask = Task {
            do { try await Task.sleep(for: .milliseconds(600)); try Task.checkCancellation(); syncNow() } catch {}
        }
        scheduleBackground()
    }
    func sceneChanged(_ phase: ScenePhase) {
        if phase == .active {
            Task { await refresh(); startForegroundTimer(); if settings.syncEnabled { syncNow() } }
        } else { foregroundTask?.cancel(); scheduleBackground() }
    }
    private func startForegroundTimer() {
        foregroundTask?.cancel()
        guard settings.syncEnabled else { return }
        let minutes = settings.syncAfterChanges ? 15 : settings.intervalMinutes
        foregroundTask = Task {
            while !Task.isCancelled {
                do { try await Task.sleep(for: .seconds(minutes * 60)); try Task.checkCancellation(); syncNow() } catch { break }
            }
        }
    }
    func scheduleBackground() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: "io.suirenx.app.sync")
        guard settings.syncEnabled else { return }
        let request = BGAppRefreshTaskRequest(identifier: "io.suirenx.app.sync")
        request.earliestBeginDate = Date().addingTimeInterval(TimeInterval((settings.syncAfterChanges ? 15 : settings.intervalMinutes) * 60))
        do { try BGTaskScheduler.shared.submit(request) }
        catch { /* Scheduling may be unavailable on Simulator or when background refresh is disabled. Manual sync remains available. */ }
    }
}
