import XCTest
@testable import SuirenCore

actor TestCredentials: CredentialStore {
    var credential: Credential?
    init(_ credential: Credential?) { self.credential = credential }
    func read() -> Credential? { credential }
    func write(_ credential: Credential?) { self.credential = credential }
}
private actor GatedTransport: SyncTransport {
    var request: SyncRequest?
    var response: CheckedContinuation<SyncResponse, Error>?
    var started: CheckedContinuation<SyncRequest, Never>?
    func exchange(_ request: SyncRequest, credential: Credential) async throws -> SyncResponse {
        self.request = request; started?.resume(returning: request); started = nil
        return try await withCheckedThrowingContinuation { response = $0 }
    }
    func waitForRequest() async -> SyncRequest {
        if let request { return request }; return await withCheckedContinuation { started = $0 }
    }
    func complete(_ value: SyncResponse) { response?.resume(returning: value); response = nil }
}

private actor AcknowledgingTransport: SyncTransport {
    var batchSizes: [Int] = []
    func exchange(_ request: SyncRequest, credential: Credential) -> SyncResponse {
        batchSizes.append(request.changes.count + request.expiry_changes.count)
        var response = SyncResponse()
        response.applied = request.changes.map { AssetChange(cursor: 1, version: $0.base_version + 1, asset: $0) }
        response.applied_expiry = request.expiry_changes.map { ExpiryChange(cursor: 1, version: $0.base_version + 1, item: $0) }
        return response
    }
    func sizes() -> [Int] { batchSizes }
}

final class SyncTests: XCTestCase {
    private func store() throws -> LocalStore {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        return try LocalStore(databaseURL: url.appendingPathComponent("data.sqlite"), backupsDirectory: url.appendingPathComponent("backups"))
    }
    private func setup(_ store: LocalStore, credential: Credential) async throws {
        var s = AppSettings(); s.servers = [ServerAddress(url: credential.serverURL, name: "test")]; s.activeURL = credential.serverURL
        try await store.updateSettings(s); try await store.enableSync(target: credential.target)
    }
    func testMixedRecordsAreDrainedInDurableBatchesOfAtMost100() async throws {
        let store = try store(), c = Credential(serverURL: "https://example.test/", username: "test", token: "synthetic", expiresAt: "")
        for i in 0..<103 { try await store.saveAsset(Asset(name: "asset \(i)", priceCents: 1, purchaseDate: .today)) }
        for i in 0..<102 { try await store.saveExpiry(ExpiryItem(name: "expiry \(i)", category: "test", packageExpiryDate: .today)) }
        try await setup(store, credential: c)
        let transport = AcknowledgingTransport()
        try await SyncCoordinator(store: store, credentials: TestCredentials(c), transport: transport).sync()
        let sizes = await transport.sizes(); XCTAssertEqual(sizes, [100, 100, 5])
        let snapshot = try await store.snapshot(); XCTAssertEqual(snapshot.pendingCount, 0); XCTAssertNotNil(snapshot.session?.lastSyncedAt)
    }
    func testCancellationPreservesFrozenBatchAndRejectsLateResponse() async throws {
        let store = try store(), c = Credential(serverURL: "https://example.test/", username: "test", token: "synthetic", expiresAt: "")
        try await store.saveAsset(Asset(name: "before", priceCents: 1, purchaseDate: .today)); try await setup(store, credential: c)
        let credentials = TestCredentials(c), transport = GatedTransport()
        let coordinator = SyncCoordinator(store: store, credentials: credentials, transport: transport)
        let task = Task { try await coordinator.sync() }
        let request = await transport.waitForRequest(); task.cancel()
        var response = SyncResponse(); response.applied = request.changes.map { AssetChange(cursor: 1, version: 1, asset: $0) }
        await transport.complete(response)
        do { try await task.value; XCTFail("cancellation ignored") } catch is CancellationError {} catch { XCTFail("\(error)") }
        let snapshot = try await store.snapshot(); XCTAssertEqual(snapshot.pendingCount, 1); XCTAssertEqual(snapshot.session?.batch?.request.idempotency_key, request.idempotency_key)
    }
    func testCredentialChangeDoesNotApplyResponseOrSendToDifferentAddress() async throws {
        let store = try store(), c = Credential(serverURL: "https://first.test/", username: "test", token: "synthetic", expiresAt: "")
        try await store.saveAsset(Asset(name: "before", priceCents: 1, purchaseDate: .today)); try await setup(store, credential: c)
        let credentials = TestCredentials(c), transport = GatedTransport()
        let coordinator = SyncCoordinator(store: store, credentials: credentials, transport: transport)
        let task = Task { try await coordinator.sync() }; let request = await transport.waitForRequest()
        var settings = try await store.snapshot().settings; settings.activeURL = "https://second.test/"; try await store.updateSettings(settings)
        var response = SyncResponse(); response.applied = request.changes.map { AssetChange(cursor: 1, version: 1, asset: $0) }
        await transport.complete(response)
        do { try await task.value; XCTFail("late response accepted") } catch {}
        let snapshot = try await store.snapshot(); XCTAssertFalse(snapshot.settings.syncEnabled); XCTAssertEqual(snapshot.pendingCount, 1); XCTAssertNotNil(snapshot.session?.batch)
        let retained = try await credentials.read(); XCTAssertEqual(retained, c)
    }
    func testRealGoHTTPRoundTripIsolationConflictAndDeletion() async throws {
        guard let base = ProcessInfo.processInfo.environment["SUIRENX_IOS_TEST_SERVER"] else { throw XCTSkip("Set SUIRENX_IOS_TEST_SERVER to an isolated Go server for HTTP integration") }
        let http = HTTPClient(); try await http.health(serverURL: base)
        let user = "ios_" + UUID().uuidString.replacingOccurrences(of: "-", with: "")
        let c = try await http.authenticate(serverURL: base, username: user, password: "synthetic-test-password", confirmation: "synthetic-test-password")
        do { _ = try await http.authenticate(serverURL: base, username: user, password: "wrong-password"); XCTFail("wrong password accepted") }
        catch let error as SuirenError { XCTAssertEqual(error.message, "账号或密码不正确") }
        let credentials = TestCredentials(c), first = try store(), second = try store()
        try await setup(first, credential: c); try await setup(second, credential: c)
        let sync1 = SyncCoordinator(store: first, credentials: credentials, transport: http), sync2 = SyncCoordinator(store: second, credentials: credentials, transport: http)
        let asset = Asset(name: "iOS HTTP", priceCents: 20001, purchaseDate: .today)
        try await first.saveAsset(asset)
        var expiry = ExpiryItem(name: "No location", category: "test", packageExpiryDate: .today); expiry.openedDate = .today; expiry.openedValidityDays = 7
        try await first.saveExpiry(expiry)
        try await sync1.sync(); try await sync2.sync()
        var snap = try await second.snapshot(); XCTAssertEqual(snap.assets.first?.name, "iOS HTTP"); XCTAssertEqual(snap.expiry.first?.location, "")
        try await first.archive(kind: "asset", id: asset.id, archived: true)
        for value: Int64? in [80050, 0, nil] {
            try await first.updateAssetSalePrice(id: asset.id, cents: value)
            try await sync1.sync(); try await sync2.sync()
            let sold = try await second.snapshot()
            XCTAssertEqual(sold.assets[0].salePriceCents, value)
        }
        try await first.archive(kind: "asset", id: asset.id, archived: false)
        try await sync1.sync(); try await sync2.sync()
        var a = snap.assets[0]; a.name = "second version"; try await second.saveAsset(a)
        a.name = "first version"; try await first.saveAsset(a); try await sync1.sync(); try await sync2.sync()
        snap = try await second.snapshot(); XCTAssertEqual(snap.conflicts.count, 1)
        try await second.resolve(key: snap.conflicts[0].id, resolution: .both); try await sync2.sync(); try await sync1.sync()
        snap = try await first.snapshot(); XCTAssertEqual(snap.assets.count, 2)
        try await first.delete(kind: "asset", id: asset.id); try await sync1.sync(); try await sync2.sync()
        snap = try await second.snapshot(); XCTAssertFalse(snap.assets.contains { $0.id == asset.id }); XCTAssertTrue(snap.journal.contains { $0.payload.id == asset.id && $0.payload.deleted })
        let other = try await http.authenticate(serverURL: base, username: user + "_other", password: "synthetic-test-password", confirmation: "synthetic-test-password")
        let isolated = try store(); try await setup(isolated, credential: other)
        try await SyncCoordinator(store: isolated, credentials: TestCredentials(other), transport: http).sync()
        let empty = try await isolated.snapshot(); XCTAssertTrue(empty.assets.isEmpty); XCTAssertTrue(empty.expiry.isEmpty)
    }
}
