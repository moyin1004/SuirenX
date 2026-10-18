import XCTest
@testable import SuirenCore

final class CoreTests: XCTestCase {
    func day(_ value: String) -> LocalDay { LocalDay(rawValue: value)! }
    func store() throws -> LocalStore {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        return try LocalStore(databaseURL: root.appendingPathComponent("local.sqlite"), backupsDirectory: root.appendingPathComponent("backups"))
    }
    func asset(_ name: String = "电脑") -> Asset { Asset(name: name, priceCents: 10001, purchaseDate: day("2026-10-01")) }
    func testArchivedSalePricePersistenceBackupJournalAndNull() async throws {
        let s = try store(), a = asset()
        try await s.saveAsset(a)
        do { try await s.updateAssetSalePrice(id: a.id, cents: 1); XCTFail("unarchived edit") } catch {}
        try await s.archive(kind: "asset", id: a.id, archived: true)
        do { try await s.updateAssetSalePrice(id: a.id, cents: -1); XCTFail("negative price") } catch {}
        for value: Int64? in [80050, 0, nil, Int64.max] {
            try await s.updateAssetSalePrice(id: a.id, cents: value)
            let snap = try await s.snapshot()
            XCTAssertEqual(snap.assets[0].salePriceCents, value)
            XCTAssertEqual(snap.assets[0].priceCents, a.priceCents)
            guard case .asset(let payload) = snap.journal[0].payload else { return XCTFail("payload") }
            XCTAssertEqual(try payload.domain().salePriceCents, value)
            let wire = try JSONSerialization.jsonObject(with: JSONEncoder().encode(payload)) as! [String: Any]
            XCTAssertNotNil(wire["sale_price_cents"])
            if value == nil { XCTAssertTrue(wire["sale_price_cents"] is NSNull) }
            let backup = try await s.exportBackup()
            let decoded = try LocalBackup.inspect(backup)
            XCTAssertEqual(decoded.assets[0].salePriceCents, value)
            try await s.restore(backup)
        }
        try await s.archive(kind: "asset", id: a.id, archived: false)
        let restored = try await s.snapshot()
        XCTAssertEqual(restored.assets[0].salePriceCents, Int64.max)
        let legacy = try JSONSerialization.jsonObject(with: JSONEncoder().encode(a)) as! [String: Any]
        XCTAssertNil(legacy["salePriceCents"])
        XCTAssertNil(try JSONDecoder().decode(Asset.self, from: JSONSerialization.data(withJSONObject: legacy)).salePriceCents)
    }
    func testOldServerCannotAcknowledgeAndEraseSalePrice() async throws {
        let s = try store(), a = asset()
        try await s.saveAsset(a); try await s.archive(kind: "asset", id: a.id, archived: true)
        try await s.updateAssetSalePrice(id: a.id, cents: 8050); try await s.enableSync(target: "old")
        let batch = try await s.freezeBatch(alreadyPulled: false)!
        var old = batch.request.changes[0]; old.sale_price_cents = nil
        var response = SyncResponse(); response.applied = [AssetChange(cursor: 1, version: 1, asset: old)]
        do { try await s.apply(response, batch: batch, target: "old"); XCTFail("old server erased sale price") } catch {}
        let snapshot = try await s.snapshot()
        XCTAssertEqual(snapshot.assets[0].salePriceCents, 8050); XCTAssertEqual(snapshot.session?.batch, batch)
        XCTAssertTrue(snapshot.journal[0].dirty)
    }
    func testDateAndMoneyBoundaries() throws {
        XCTAssertNil(LocalDay(rawValue: "2026-02-29")); XCTAssertNil(LocalDay(rawValue: "2026-2-01"))
        XCTAssertEqual(day("2024-02-28").adding(1), day("2024-02-29"))
        var a = asset(); XCTAssertEqual(a.heldDays(on: day("2026-10-03")), 3); XCTAssertEqual(a.dailyCostCents(on: day("2026-10-03")), 3334)
        a.status = .retired; a.retiredDate = day("2026-10-01"); XCTAssertEqual(a.heldDays(on: day("2026-10-17")), 1)
        a.retiredDate = day("2026-09-30"); XCTAssertThrowsError(try a.validate())
        XCTAssertEqual(try Money.parse("92233720368547758.07"), Int64.max)
        XCTAssertThrowsError(try Money.parse("92233720368547758.08")); XCTAssertThrowsError(try Money.parse("1.001"))
        XCTAssertEqual(try Money.parse("0.5"), 50)
    }
    func testOpenedExpiryAndOptionalLocation() throws {
        var e = ExpiryItem(name: "用品", category: "日用", packageExpiryDate: day("2026-12-01"))
        e.openedDate = day("2026-10-01"); e.openedValidityDays = 3
        try e.validate(); XCTAssertEqual(e.actualExpiryDate, day("2026-10-03"))
        XCTAssertEqual(e.bucket(on: day("2026-10-03")), .today)
        e.archivedAt = timestamp(); XCTAssertEqual(e.bucket(on: day("2026-10-04")), .inactive)
    }
    func testOfflinePersistenceJournalArchiveDeleteAndReopen() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let url = root.appendingPathComponent("local.sqlite"), backups = root.appendingPathComponent("backups")
        let first = try LocalStore(databaseURL: url, backupsDirectory: backups), a = asset()
        try await first.saveAsset(a); try await first.archive(kind: "asset", id: a.id, archived: true)
        do { try await first.saveAsset(a); XCTFail("archived write accepted") } catch {}
        let reopened = try LocalStore(databaseURL: url, backupsDirectory: backups)
        var snapshot = try await reopened.snapshot(); XCTAssertNotNil(snapshot.assets.first?.archivedAt); XCTAssertEqual(snapshot.pendingCount, 1)
        try await reopened.delete(kind: "asset", id: a.id)
        snapshot = try await first.snapshot(); XCTAssertTrue(snapshot.assets.isEmpty); XCTAssertTrue(snapshot.journal[0].payload.deleted)
    }
    func testFrozenBatchRetryAndInFlightEdit() async throws {
        let s = try store(); var a = asset(); try await s.saveAsset(a)
        try await s.enableSync(target: "server|user")
        let batch = try await s.freezeBatch(alreadyPulled: false)!
        a.name = "新的名称"; try await s.saveAsset(a)
        let retry = try await s.freezeBatch(alreadyPulled: false); XCTAssertEqual(batch, retry)
        var response = SyncResponse(); response.applied = [AssetChange(cursor: 1, version: 1, asset: batch.request.changes[0])]; response.next_cursor = 1
        try await s.apply(response, batch: batch, target: "server|user")
        let snap = try await s.snapshot(); XCTAssertEqual(snap.assets[0].name, "新的名称"); XCTAssertTrue(snap.journal[0].dirty); XCTAssertEqual(snap.journal[0].version, 1)
        let next = try await s.freezeBatch(alreadyPulled: true)!
        XCTAssertEqual(next.request.changes[0].name, "新的名称"); XCTAssertEqual(next.request.changes[0].base_version, 1)
    }
    func testMissingAcknowledgementRollsBackAndTargetResetKeepsTombstone() async throws {
        let s = try store(); let a = asset(); try await s.saveAsset(a); try await s.delete(kind: "asset", id: a.id)
        try await s.enableSync(target: "one"); let batch = try await s.freezeBatch(alreadyPulled: false)!
        do { try await s.apply(SyncResponse(), batch: batch, target: "one"); XCTFail("missing ack accepted") } catch {}
        var snap = try await s.snapshot(); XCTAssertEqual(snap.session?.batch, batch)
        try await s.enableSync(target: "two"); snap = try await s.snapshot()
        XCTAssertNil(snap.session?.batch); XCTAssertTrue(snap.journal[0].payload.deleted); XCTAssertEqual(snap.journal[0].version, 0)
        let backups = try await s.safetyBackups(); XCTAssertEqual(backups.count, 2)
    }
    func testConflictPausesOnlyOneRecordAndKeepsBoth() async throws {
        let s = try store(); let a = asset(), b = asset("其他")
        try await s.saveAsset(a); try await s.saveAsset(b); try await s.enableSync(target: "target")
        let batch = try await s.freezeBatch(alreadyPulled: false)!
        var remote = AssetPayload(a); remote.name = "另一端"
        var response = SyncResponse(); response.conflicts = [AssetConflict(id: a.id, base_version: 0, remote_version: 2, remote: remote)]
        response.applied = [AssetChange(cursor: 1, version: 1, asset: AssetPayload(b))]
        try await s.apply(response, batch: batch, target: "target")
        var snap = try await s.snapshot(); XCTAssertEqual(snap.conflicts.count, 1); XCTAssertEqual(snap.pendingCount, 1)
        try await s.resolve(key: "asset/\(a.id)", resolution: .both)
        snap = try await s.snapshot(); XCTAssertEqual(snap.assets.count, 3); XCTAssertTrue(snap.conflicts.isEmpty)
        XCTAssertTrue(snap.assets.contains { $0.name == "电脑（副本）" }); XCTAssertTrue(snap.assets.contains { $0.name == "另一端" })
    }
    func testBackupAndroidDefaultsValidationAndRestoreSafety() async throws {
        let s = try store(); try await s.saveAsset(asset())
        let json = #"{"format":"suirenx-local-backup","version":1,"assets":[{"id":"android","name":"旧资产","priceCents":1,"purchaseDate":"2026-01-01","status":"ACTIVE"}],"expiryItems":[]}"#
        let data = Data(json.utf8); let backup = try LocalBackup.inspect(data); XCTAssertEqual(backup.assets[0].iconKey, "devices")
        try await s.restore(data)
        let snap = try await s.snapshot(); XCTAssertEqual(snap.assets.map(\.id), ["android"]); XCTAssertEqual(snap.journal.count, 2)
        XCTAssertFalse(snap.settings.syncEnabled)
        let backups = try await s.safetyBackups(); XCTAssertEqual(backups.count, 1)
        let exported = try await s.exportBackup(); XCTAssertFalse(String(decoding: exported, as: UTF8.self).contains("token"))
        XCTAssertThrowsError(try LocalBackup.inspect(Data(json.replacingOccurrences(of: "\"version\":1", with: "\"version\":2").utf8)))
        XCTAssertThrowsError(try LocalBackup.inspect(Data(json.replacingOccurrences(of: "\"id\":\"android\"", with: "\"id\":\" \"").utf8)))
    }
    func testPasswordLegacyAndAddressSecurity() throws {
        try AccountValidation.validate(username: "user", password: "旧密码")
        try AccountValidation.validate(username: "user", password: "        ", confirmation: "        ")
        XCTAssertThrowsError(try AccountValidation.validate(username: "user", password: "中文密码123456", confirmation: "中文密码123456"))
        XCTAssertEqual(try ServerAddress.normalize("EXAMPLE.com/base"), "https://example.com/base/")
        XCTAssertThrowsError(try ServerAddress.normalize("https://name:password@example.com"))
        XCTAssertThrowsError(try ServerAddress.normalize("http://localhost"))
        XCTAssertThrowsError(try ServerAddress.normalize("999.999.999.999", allowHTTP: true))
    }
    func testWireUsesSnakeCaseNumericCentsExplicitEmptyArrays() throws {
        var request = SyncRequest(); request.changes = [AssetPayload(asset())]
        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(request)) as! [String: Any]
        XCTAssertEqual(object["expiry_cursor"] as? Int, 0); XCTAssertNotNil(object["expiry_changes"])
        let payload = (object["changes"] as! [[String: Any]])[0]
        XCTAssertEqual(payload["price_cents"] as? Int, 10001); XCTAssertEqual(payload["status"] as? String, "ACTIVE"); XCTAssertEqual(payload["retired_date"] as? String, "")
    }
    func testUTF16FieldLimitsMatchAndroidBackups() throws {
        var a = asset(); a.notes = String(repeating: "😀", count: 1000); try a.validate()
        a.notes += "😀"; XCTAssertThrowsError(try a.validate())
        a.notes = ""; a.tags = [String(repeating: "😀", count: 16)]; XCTAssertThrowsError(try a.validate())
        var item = ExpiryItem(name: String(repeating: "😀", count: 100), category: "日用", packageExpiryDate: .today)
        try item.validate(); item.name += "😀"; XCTAssertThrowsError(try item.validate())
    }
    func testExpiryConflictKeepsMaxLengthName() async throws {
        let s = try store(), name = String(repeating: "物", count: 200)
        let item = ExpiryItem(name: name, category: "日用", packageExpiryDate: .today)
        try await s.saveExpiry(item); try await s.enableSync(target: "target")
        let batch = try await s.freezeBatch(alreadyPulled: false)!
        var remote = ExpiryPayload(item); remote.name = "另一端"
        var response = SyncResponse(); response.expiry_conflicts = [ExpiryConflict(id: item.id, base_version: 0, remote_version: 1, remote: remote)]
        try await s.apply(response, batch: batch, target: "target"); try await s.resolve(key: "expiry/\(item.id)", resolution: .both)
        let snapshot = try await s.snapshot()
        XCTAssertEqual(snapshot.expiry.count, 2); XCTAssertTrue(snapshot.expiry.contains { $0.name == name })
        XCTAssertTrue(snapshot.expiry.contains { $0.name == "另一端" }); XCTAssertTrue(snapshot.conflicts.isEmpty)
    }
}
