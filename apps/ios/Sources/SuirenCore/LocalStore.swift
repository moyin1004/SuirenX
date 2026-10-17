import Foundation

public struct LocalSnapshot: Sendable {
    public var assets: [Asset]
    public var expiry: [ExpiryItem]
    public var journal: [JournalRecord]
    public var session: SyncSession?
    public var settings: AppSettings
    public var pendingCount: Int { journal.filter(\.dirty).count }
    public var conflicts: [JournalRecord] { journal.filter { $0.remoteVersion != nil } }
}

public struct LocalBackup: Codable, Sendable {
    public var format = "suirenx-local-backup"
    public var version = 1
    public var exportedAt: String
    public var assets: [Asset]
    public var expiryItems: [ExpiryItem]
    enum CodingKeys: String, CodingKey { case format, version, exportedAt, assets, expiryItems }
    init(assets: [Asset], expiry: [ExpiryItem]) { exportedAt = timestamp(); self.assets = assets; expiryItems = expiry }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        format = try c.decodeIfPresent(String.self, forKey: .format) ?? "suirenx-local-backup"
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        exportedAt = try c.decodeIfPresent(String.self, forKey: .exportedAt) ?? ""
        assets = try c.decodeIfPresent([Asset].self, forKey: .assets) ?? []
        expiryItems = try c.decodeIfPresent([ExpiryItem].self, forKey: .expiryItems) ?? []
    }
    public static func inspect(_ data: Data) throws -> Self {
        guard data.count <= 64 * 1024 * 1024 else { throw SuirenError("备份文件过大") }
        let backup = try JSONDecoder().decode(Self.self, from: data)
        guard backup.format == "suirenx-local-backup", backup.version == 1 else { throw SuirenError("不支持的备份格式或版本") }
        guard Set(backup.assets.map(\.id)).count == backup.assets.count,
              Set(backup.expiryItems.map(\.id)).count == backup.expiryItems.count,
              backup.assets.allSatisfy({ !$0.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }),
              backup.expiryItems.allSatisfy({ !$0.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) else { throw SuirenError("备份包含重复或空编号") }
        for asset in backup.assets { try asset.validate(importing: true) }
        for item in backup.expiryItems { try item.validate() }
        return backup
    }
}

/// Every mutation, journal update and cursor acknowledgement commits atomically.
public actor LocalStore {
    private let database: SQLiteDatabase
    private let backupsDirectory: URL
    public init(databaseURL: URL, backupsDirectory: URL) throws {
        database = try SQLiteDatabase(url: databaseURL); self.backupsDirectory = backupsDirectory
    }
    public func snapshot() throws -> LocalSnapshot {
        let s = try database.load()
        return LocalSnapshot(assets: s.assets.values.sorted { $0.createdAt > $1.createdAt },
                             expiry: s.expiry.values.sorted { $0.actualExpiryDate < $1.actualExpiryDate },
                             journal: s.journal.values.sorted { $0.id < $1.id }, session: s.session, settings: s.settings)
    }
    private func transaction<T>(_ action: (inout DatabaseState) throws -> T) throws -> T {
        var candidate = try database.load(); let result = try action(&candidate); try database.save(candidate); return result
    }
    private func journal(_ payload: RecordPayload, in state: inout DatabaseState) {
        var row = state.journal[payload.key] ?? JournalRecord(payload: payload)
        row.payload = payload; row.revision = UUID().uuidString; row.dirty = true
        state.journal[payload.key] = row
    }
    public func saveAsset(_ input: Asset) throws {
        var asset = input; asset.name = asset.name.trimmingCharacters(in: .whitespacesAndNewlines)
        try asset.validate()
        try transaction { state in
            if let old = state.assets[asset.id] {
                guard old.archivedAt == nil else { throw SuirenError("归档资产请先恢复") }
                asset.createdAt = old.createdAt
            } else if asset.createdAt.isEmpty { asset.createdAt = timestamp() }
            asset.updatedAt = timestamp(); state.assets[asset.id] = asset; journal(.asset(AssetPayload(asset)), in: &state)
        }
    }
    public func saveExpiry(_ input: ExpiryItem) throws {
        var item = input; item.name = item.name.trimmingCharacters(in: .whitespacesAndNewlines)
        item.category = item.category.trimmingCharacters(in: .whitespacesAndNewlines); item.location = item.location.trimmingCharacters(in: .whitespacesAndNewlines)
        try item.validate()
        guard item.openedDate == nil || (item.openedDate! <= .today && item.openedDate! <= item.packageExpiryDate) else { throw SuirenError("开封日不能晚于今天或包装到期日") }
        try transaction { state in
            if let old = state.expiry[item.id] {
                guard old.archivedAt == nil else { throw SuirenError("归档用品请先恢复") }; item.createdAt = old.createdAt
            } else if item.createdAt.isEmpty { item.createdAt = timestamp() }
            item.updatedAt = timestamp(); state.expiry[item.id] = item; journal(.expiry(ExpiryPayload(item)), in: &state)
        }
    }
    public func archive(kind: String, id: String, archived: Bool) throws {
        try transaction { state in
            if kind == "asset", var a = state.assets[id] {
                a.archivedAt = archived ? (a.archivedAt ?? timestamp()) : nil; a.updatedAt = timestamp()
                state.assets[id] = a; journal(.asset(AssetPayload(a)), in: &state)
            } else if kind == "expiry", var e = state.expiry[id] {
                e.archivedAt = archived ? (e.archivedAt ?? timestamp()) : nil; e.updatedAt = timestamp()
                state.expiry[id] = e; journal(.expiry(ExpiryPayload(e)), in: &state)
            } else { throw SuirenError("记录不存在") }
        }
    }
    public func delete(kind: String, id: String) throws {
        try transaction { state in
            if kind == "asset", let a = state.assets.removeValue(forKey: id) { journal(.asset(AssetPayload(a, deleted: true)), in: &state) }
            else if kind == "expiry", let e = state.expiry.removeValue(forKey: id) { journal(.expiry(ExpiryPayload(e, deleted: true)), in: &state) }
        }
    }
    public func updateSettings(_ settings: AppSettings) throws {
        guard ["system", "light", "dark"].contains(settings.theme), (1...30).contains(settings.soonDays),
              [15, 60, 360, 1440].contains(settings.intervalMinutes) else { throw SuirenError("设置值无效") }
        try transaction { state in
            var next = settings
            if next.activeURL != state.settings.activeURL { next.syncEnabled = false }
            state.settings = next
        }
    }
    public func exportBackup() throws -> Data {
        let state = try database.load()
        let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(LocalBackup(assets: Array(state.assets.values), expiry: Array(state.expiry.values)))
    }
    @discardableResult public func safetyBackup(reason: String) throws -> URL {
        let state = try database.load()
        var object = try JSONSerialization.jsonObject(with: exportBackup()) as! [String: Any]
        // Recovery metadata contains business payloads and target versions, never credentials.
        var recovery: [String: Any] = ["records": try JSONSerialization.jsonObject(with: JSONEncoder().encode(Array(state.journal.values)))]
        if let session = state.session { recovery["session"] = try JSONSerialization.jsonObject(with: JSONEncoder().encode(session)) }
        object["syncRecovery"] = recovery
        let data = try JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys])
        try FileManager.default.createDirectory(at: backupsDirectory, withIntermediateDirectories: true)
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: backupsDirectory.path)
        let url = backupsDirectory.appendingPathComponent("\(reason)-\(UUID().uuidString).json")
        try data.write(to: url, options: .atomic)
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: url.path)
        return url
    }
    public func safetyBackups() throws -> [URL] {
        guard FileManager.default.fileExists(atPath: backupsDirectory.path) else { return [] }
        return try FileManager.default.contentsOfDirectory(at: backupsDirectory, includingPropertiesForKeys: [.creationDateKey]).filter { $0.pathExtension == "json" }.sorted { $0.lastPathComponent > $1.lastPathComponent }
    }
    public func restore(_ data: Data) throws {
        let backup = try LocalBackup.inspect(data)
        try safetyBackup(reason: "pre-restore")
        try transaction { state in
            for a in state.assets.values { journal(.asset(AssetPayload(a, deleted: true)), in: &state) }
            for e in state.expiry.values { journal(.expiry(ExpiryPayload(e, deleted: true)), in: &state) }
            state.assets = Dictionary(uniqueKeysWithValues: backup.assets.map { ($0.id, $0) })
            state.expiry = Dictionary(uniqueKeysWithValues: backup.expiryItems.map { ($0.id, $0) })
            for a in backup.assets { journal(.asset(AssetPayload(a)), in: &state) }
            for e in backup.expiryItems { journal(.expiry(ExpiryPayload(e)), in: &state) }
            state.settings.syncEnabled = false
        }
    }
    /// Called only after an explicit enable preview. Saving a URL or logging in does not enroll data.
    public func enableSync(target: String) throws {
        let current = try database.load()
        if current.session?.target != target { try safetyBackup(reason: "pre-sync-target") }
        try transaction { state in
            if state.session?.target != target {
                for key in Array(state.journal.keys) {
                    state.journal[key]?.version = 0; state.journal[key]?.dirty = true
                    state.journal[key]?.revision = UUID().uuidString; state.journal[key]?.remote = nil; state.journal[key]?.remoteVersion = nil
                }
                for a in state.assets.values where state.journal["asset/\(a.id)"] == nil { journal(.asset(AssetPayload(a)), in: &state) }
                for e in state.expiry.values where state.journal["expiry/\(e.id)"] == nil { journal(.expiry(ExpiryPayload(e)), in: &state) }
                state.session = SyncSession(target: target)
            }
            state.settings.syncEnabled = true
        }
    }
    public func freezeBatch(alreadyPulled: Bool) throws -> FrozenBatch? {
        try transaction { state in
            guard var session = state.session, state.settings.syncEnabled else { throw SuirenError("请先开启同步") }
            if let batch = session.batch { return batch }
            let pending = state.journal.values.filter { $0.dirty && $0.remoteVersion == nil }.sorted { $0.id < $1.id }.prefix(100)
            if pending.isEmpty && alreadyPulled { return nil }
            var request = SyncRequest(); request.cursor = session.cursor; request.expiry_cursor = session.expiryCursor
            for row in pending {
                switch row.payload {
                case .asset(var a): a.base_version = row.version; request.changes.append(a)
                case .expiry(var e): e.base_version = row.version; request.expiry_changes.append(e)
                }
            }
            let batch = FrozenBatch(request: request, revisions: Dictionary(uniqueKeysWithValues: pending.map { ($0.id, $0.revision) }))
            session.batch = batch; state.session = session; return batch
        }
    }
    private func write(_ payload: RecordPayload, in state: inout DatabaseState) throws {
        switch payload {
        case .asset(let a):
            if a.deleted { state.assets.removeValue(forKey: a.id) }
            else { var domain = try a.domain(); domain.createdAt = state.assets[a.id]?.createdAt ?? timestamp(); domain.updatedAt = timestamp(); state.assets[a.id] = domain }
        case .expiry(let e):
            if e.deleted { state.expiry.removeValue(forKey: e.id) }
            else { var domain = try e.domain(); domain.createdAt = state.expiry[e.id]?.createdAt ?? timestamp(); domain.updatedAt = timestamp(); state.expiry[e.id] = domain }
        }
    }
    public func apply(_ response: SyncResponse, batch: FrozenBatch, target: String) throws {
        try transaction { state in
            guard var session = state.session, session.target == target, session.batch == batch else { throw SuirenError("同步目标已改变，未应用迟到响应") }
            let accounted = Set(response.applied.map { "asset/\($0.asset.id)" } + response.applied_expiry.map { "expiry/\($0.item.id)" } + response.conflicts.map { "asset/\($0.id)" } + response.expiry_conflicts.map { "expiry/\($0.id)" })
            guard batch.revisions.keys.allSatisfy(accounted.contains) else { throw SuirenError("服务器响应缺少确认，原批次已保留") }
            func accept(_ payload: RecordPayload, version: Int64) throws {
                guard var current = state.journal[payload.key] else { return }
                if current.revision == batch.revisions[payload.key] {
                    try write(payload, in: &state); current.payload = payload; current.dirty = false; current.remote = nil; current.remoteVersion = nil
                }
                current.version = version; state.journal[payload.key] = current
            }
            func pull(_ payload: RecordPayload, version: Int64) throws {
                if let current = state.journal[payload.key], current.dirty || current.version >= version { return }
                try write(payload, in: &state); var row = JournalRecord(payload: payload); row.version = version; row.dirty = false; state.journal[payload.key] = row
            }
            for c in response.applied { var a = c.asset; a.deleted = a.deleted || !c.deleted_at.isEmpty; try accept(.asset(a), version: c.version) }
            for c in response.applied_expiry { var e = c.item; e.deleted = e.deleted || !c.deleted_at.isEmpty; try accept(.expiry(e), version: c.version) }
            for c in response.conflicts { let key = "asset/\(c.id)"; state.journal[key]?.remoteVersion = c.remote_version; state.journal[key]?.remote = c.remote.map(RecordPayload.asset) }
            for c in response.expiry_conflicts { let key = "expiry/\(c.id)"; state.journal[key]?.remoteVersion = c.remote_version; state.journal[key]?.remote = c.remote.map(RecordPayload.expiry) }
            for c in response.changes { var a = c.asset; a.deleted = a.deleted || !c.deleted_at.isEmpty; try pull(.asset(a), version: c.version) }
            for c in response.expiry_changes { var e = c.item; e.deleted = e.deleted || !c.deleted_at.isEmpty; try pull(.expiry(e), version: c.version) }
            session.cursor = max(session.cursor, response.next_cursor); session.expiryCursor = max(session.expiryCursor, response.next_expiry_cursor)
            session.batch = nil; state.session = session
        }
    }
    public func markSynced(target: String) throws {
        try transaction { state in
            guard state.session?.target == target else { throw SuirenError("同步目标已改变") }; state.session?.lastSyncedAt = timestamp()
        }
    }
    public func resolve(key: String, resolution: ConflictResolution) throws {
        try transaction { state in
            guard state.session?.batch == nil else { throw SuirenError("请先重试未确认批次") }
            guard var row = state.journal[key], let version = row.remoteVersion else { throw SuirenError("该冲突已处理") }
            if resolution == .local {
                row.version = version; row.dirty = true; row.revision = UUID().uuidString
            } else {
                guard let remote = row.remote else { throw SuirenError("另一端快照不可用，请检查账号归属") }
                if resolution == .both {
                    let source = row.payload.deleted ? remote : row.payload
                    guard !source.deleted else { throw SuirenError("双方均已删除，没有可另存内容") }
                    let copy = source.copy(id: UUID().uuidString); try write(copy, in: &state); journal(copy, in: &state)
                }
                if resolution == .both && row.payload.deleted {
                    row.version = version; row.dirty = true; row.revision = UUID().uuidString
                } else { try write(remote, in: &state); row.payload = remote; row.version = version; row.dirty = false }
            }
            row.remote = nil; row.remoteVersion = nil; state.journal[key] = row
        }
    }
}
