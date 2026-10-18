import XCTest
import CSQLite
@testable import SuirenCore

final class StorageTests: XCTestCase {
    private func sql(_ sql: String, at url: URL) throws {
        var db: OpaquePointer?; XCTAssertEqual(sqlite3_open(url.path, &db), SQLITE_OK); defer { sqlite3_close(db) }
        guard sqlite3_exec(db, sql, nil, nil, nil) == SQLITE_OK else { throw SuirenError(String(cString: sqlite3_errmsg(db))) }
    }
    private func paths() -> (URL, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        return (root.appendingPathComponent("local.sqlite"), root.appendingPathComponent("backups"))
    }
    func testJournalFailureRollsBackBusinessWrite() async throws {
        let (url, backups) = paths(); let store = try LocalStore(databaseURL: url, backupsDirectory: backups)
        var a = Asset(name: "original", priceCents: 100, purchaseDate: .today); try await store.saveAsset(a)
        try sql("CREATE TRIGGER fail_journal BEFORE INSERT ON journal BEGIN SELECT RAISE(ABORT,'injected failure'); END", at: url)
        a.name = "should rollback"
        do { try await store.saveAsset(a); XCTFail("write unexpectedly succeeded") } catch {}
        let reopened = try LocalStore(databaseURL: url, backupsDirectory: backups)
        let snapshot = try await reopened.snapshot(); XCTAssertEqual(snapshot.assets.first?.name, "original"); XCTAssertEqual(snapshot.journal.first?.payload.name, "original")
    }
    func testNewerDatabaseIsNotResetAndFrozenBatchSurvivesReopen() async throws {
        let (url, backups) = paths(); let store = try LocalStore(databaseURL: url, backupsDirectory: backups)
        try await store.saveAsset(Asset(name: "durable", priceCents: 1, purchaseDate: .today)); try await store.enableSync(target: "target")
        let batch = try await store.freezeBatch(alreadyPulled: false)
        let reopened = try LocalStore(databaseURL: url, backupsDirectory: backups)
        let retry = try await reopened.freezeBatch(alreadyPulled: false); XCTAssertEqual(batch, retry)
        try sql("INSERT INTO schema_migrations(version) VALUES(2)", at: url)
        XCTAssertThrowsError(try LocalStore(databaseURL: url, backupsDirectory: backups))
        let preserved = try await reopened.snapshot(); XCTAssertEqual(preserved.assets.first?.name, "durable")
    }
    func testAssetQueryAndTotalsRespectArchiveTagsAndSearch() {
        var active = Asset(id: "a", name: "Laptop", priceCents: Int64.max, purchaseDate: .today); active.tags = ["工作", "电脑"]
        var retired = active; retired.id = "b"; retired.status = .retired; retired.retiredDate = .today
        var archived = active; archived.id = "c"; archived.archivedAt = timestamp()
        var query = AssetQuery(); let all = [active, retired, archived]
        XCTAssertEqual(query.apply(all).count, 1)
        query.filter = .all; query.tags = ["工作", "电脑"]; query.search = "laptop"
        let result = query.apply(all); XCTAssertEqual(result.count, 3)
        let summary = AssetSummary(result); XCTAssertEqual(summary.totalCents, Decimal(Int64.max) * 3); XCTAssertEqual(summary.dailyCents, Decimal(Int64.max)); XCTAssertEqual(summary.active, 1)
        query.filter = .archived; XCTAssertEqual(query.apply(all).map(\.id), ["c"])
    }
    func testIncomingResponsePayloadDefaultsAreCompatible() throws {
        let data = Data(#"{"id":"one","name":"Asset","price_cents":0,"purchase_date":"2026-01-01","status":"ACTIVE"}"#.utf8)
        let payload = try JSONDecoder().decode(AssetPayload.self, from: data)
        XCTAssertEqual(payload.base_version, 0); XCTAssertFalse(payload.deleted); XCTAssertTrue(payload.tags.isEmpty)
        XCTAssertEqual(try payload.domain().name, "Asset")
    }
    func testOfflineRestoreWithUnacknowledgedBatchKeepsNewerRestoredRevision() async throws {
        let (url, backups) = paths(); let store = try LocalStore(databaseURL: url, backupsDirectory: backups)
        let original = Asset(name: "before", priceCents: 1, purchaseDate: .today)
        try await store.saveAsset(original); try await store.enableSync(target: "target")
        let batch = try await store.freezeBatch(alreadyPulled: false)!
        var restored = original; restored.name = "restored offline"
        let data = try JSONEncoder().encode(LocalBackup(assets: [restored], expiry: []))
        try await store.restore(data)
        var snapshot = try await store.snapshot(); XCTAssertEqual(snapshot.session?.batch, batch); XCTAssertFalse(snapshot.settings.syncEnabled)
        var response = SyncResponse(); response.applied = [AssetChange(cursor: 1, version: 1, asset: batch.request.changes[0])]
        try await store.apply(response, batch: batch, target: "target")
        snapshot = try await store.snapshot(); XCTAssertEqual(snapshot.assets[0].name, "restored offline"); XCTAssertTrue(snapshot.journal[0].dirty)
    }
}
