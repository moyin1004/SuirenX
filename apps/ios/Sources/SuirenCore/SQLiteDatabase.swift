import Foundation
import CSQLite

struct DatabaseState: Codable {
    var assets: [String: Asset] = [:]
    var expiry: [String: ExpiryItem] = [:]
    var journal: [String: JournalRecord] = [:]
    var session: SyncSession?
    var settings = AppSettings()
}

/// Owned exclusively by LocalStore's actor. SQLite is the persisted source of truth.
final class SQLiteDatabase {
    private var handle: OpaquePointer?
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    init(url: URL) throws {
        if url.path != ":memory:" { try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true) }
        guard sqlite3_open_v2(url.path, &handle, SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX, nil) == SQLITE_OK else {
            sqlite3_close(handle); handle = nil; throw SuirenError(.could_not_open_the_local_database)
        }
        do {
            try execute("PRAGMA foreign_keys=ON")
            try execute("PRAGMA journal_mode=WAL")
            sqlite3_busy_timeout(handle, 5000)
            // Immutable v1 migration. Future changes append a new version, never reset data.
            try execute("CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY)")
            let version = try scalar("SELECT COALESCE(MAX(version),0) FROM schema_migrations")
            guard version <= 1 else { throw SuirenError(.this_database_is_from_a_newer_version_and_cannot_be) }
            if version == 0 {
                try execute("BEGIN IMMEDIATE")
                do {
                    for name in ["assets", "expiry", "journal", "metadata"] {
                        try execute("CREATE TABLE \(name)(id TEXT PRIMARY KEY NOT NULL, payload BLOB NOT NULL)")
                    }
                    try execute("INSERT INTO schema_migrations(version) VALUES(1)")
                    try execute("COMMIT")
                } catch { try? execute("ROLLBACK"); throw error }
            }
        } catch { sqlite3_close(handle); handle = nil; throw error }
    }
    deinit { sqlite3_close(handle) }
    private func failure(_ code: SuirenError.Code, arguments: [String] = []) -> SuirenError {
        SuirenError(code, arguments: arguments, technicalDetail: handle.map { String(cString: sqlite3_errmsg($0)) })
    }
    private func execute(_ sql: String) throws {
        guard sqlite3_exec(handle, sql, nil, nil, nil) == SQLITE_OK else { throw failure(.local_database_transaction_failed) }
    }
    private func scalar(_ sql: String) throws -> Int {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &statement, nil) == SQLITE_OK else { throw failure(.could_not_read_the_database_version) }
        defer { sqlite3_finalize(statement) }
        guard sqlite3_step(statement) == SQLITE_ROW else { throw failure(.could_not_read_the_database_version) }
        return Int(sqlite3_column_int(statement, 0))
    }
    private func rows<T: Decodable>(_ table: String, _: T.Type) throws -> [String: T] {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(handle, "SELECT id,payload FROM \(table)", -1, &statement, nil) == SQLITE_OK else { throw failure(.could_not_read, arguments: [String(describing: table)]) }
        defer { sqlite3_finalize(statement) }
        var result: [String: T] = [:]
        while true {
            let code = sqlite3_step(statement)
            if code == SQLITE_DONE { break }
            guard code == SQLITE_ROW, let key = sqlite3_column_text(statement, 0), let bytes = sqlite3_column_blob(statement, 1) else { throw failure(.could_not_read, arguments: [String(describing: table)]) }
            result[String(cString: key)] = try decoder.decode(T.self, from: Data(bytes: bytes, count: Int(sqlite3_column_bytes(statement, 1))))
        }
        return result
    }
    private func replace<T: Encodable>(_ table: String, _ values: [String: T]) throws {
        try execute("DELETE FROM \(table)")
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(handle, "INSERT INTO \(table)(id,payload) VALUES(?,?)", -1, &statement, nil) == SQLITE_OK else { throw failure(.could_not_save, arguments: [String(describing: table)]) }
        defer { sqlite3_finalize(statement) }
        for (id, value) in values {
            let data = try encoder.encode(value)
            let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
            guard sqlite3_bind_text(statement, 1, id, -1, transient) == SQLITE_OK else { throw failure(.could_not_bind_the_record_id) }
            let code = data.withUnsafeBytes { sqlite3_bind_blob(statement, 2, $0.baseAddress, Int32($0.count), transient) }
            guard code == SQLITE_OK, sqlite3_step(statement) == SQLITE_DONE else { throw failure(.could_not_save, arguments: [String(describing: table)]) }
            sqlite3_reset(statement); sqlite3_clear_bindings(statement)
        }
    }
    func load() throws -> DatabaseState {
        var state = DatabaseState()
        state.assets = try rows("assets", Asset.self); state.expiry = try rows("expiry", ExpiryItem.self)
        state.journal = try rows("journal", JournalRecord.self)
        let meta = try rows("metadata", Data.self)
        if let data = meta["settings"] { state.settings = try decoder.decode(AppSettings.self, from: data) }
        if let data = meta["session"] { state.session = try decoder.decode(SyncSession.self, from: data) }
        return state
    }
    func save(_ state: DatabaseState) throws {
        try execute("BEGIN IMMEDIATE")
        do {
            try replace("assets", state.assets); try replace("expiry", state.expiry); try replace("journal", state.journal)
            var meta = ["settings": try encoder.encode(state.settings)]
            if let session = state.session { meta["session"] = try encoder.encode(session) }
            try replace("metadata", meta); try execute("COMMIT")
        } catch { try? execute("ROLLBACK"); throw error }
    }
}
