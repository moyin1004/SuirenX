import Foundation

// Names deliberately match the IDL's HTTP JSON, including explicit zeros and arrays.
public struct AssetPayload: Codable, Equatable, Sendable {
    public var id: String
    public var base_version: Int64 = 0
    public var deleted = false
    public var name = ""
    public var sale_price_cents: Int64?
    public var price_cents: Int64 = 0
    public var purchase_date = ""
    public var status = "ACTIVE"
    public var image_url = ""
    public var retired_date = ""
    public var archived_at = ""
    public var icon_key = "item"
    public var purchase_channel = ""
    public var warranty_end_date = ""
    public var notes = ""
    public var tags: [String] = []
    enum CodingKeys: String, CodingKey { case id, base_version, deleted, name, sale_price_cents, price_cents, purchase_date, status, image_url, retired_date, archived_at, icon_key, purchase_channel, warranty_end_date, notes, tags }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        base_version = try c.decodeIfPresent(Int64.self, forKey: .base_version) ?? 0
        deleted = try c.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        sale_price_cents = try c.decodeIfPresent(Int64.self, forKey: .sale_price_cents)
        price_cents = try c.decodeIfPresent(Int64.self, forKey: .price_cents) ?? 0
        purchase_date = try c.decodeIfPresent(String.self, forKey: .purchase_date) ?? ""
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? "ACTIVE"
        image_url = try c.decodeIfPresent(String.self, forKey: .image_url) ?? ""
        retired_date = try c.decodeIfPresent(String.self, forKey: .retired_date) ?? ""
        archived_at = try c.decodeIfPresent(String.self, forKey: .archived_at) ?? ""
        icon_key = try c.decodeIfPresent(String.self, forKey: .icon_key) ?? "devices"
        purchase_channel = try c.decodeIfPresent(String.self, forKey: .purchase_channel) ?? ""
        warranty_end_date = try c.decodeIfPresent(String.self, forKey: .warranty_end_date) ?? ""
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        tags = try c.decodeIfPresent([String].self, forKey: .tags) ?? []
    }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(base_version, forKey: .base_version)
        try c.encode(deleted, forKey: .deleted)
        try c.encode(name, forKey: .name)
        try c.encode(sale_price_cents, forKey: .sale_price_cents)
        try c.encode(price_cents, forKey: .price_cents)
        try c.encode(purchase_date, forKey: .purchase_date)
        try c.encode(status, forKey: .status)
        try c.encode(image_url, forKey: .image_url)
        try c.encode(retired_date, forKey: .retired_date)
        try c.encode(archived_at, forKey: .archived_at)
        try c.encode(icon_key, forKey: .icon_key)
        try c.encode(purchase_channel, forKey: .purchase_channel)
        try c.encode(warranty_end_date, forKey: .warranty_end_date)
        try c.encode(notes, forKey: .notes)
        try c.encode(tags, forKey: .tags)
    }
    public init(_ asset: Asset, version: Int64 = 0, deleted: Bool = false) {
        id = asset.id; base_version = version; self.deleted = deleted; name = asset.name
        sale_price_cents = asset.salePriceCents
        price_cents = asset.priceCents; purchase_date = asset.purchaseDate.rawValue
        status = asset.status == .active ? "ACTIVE" : "RETIRED"; retired_date = asset.retiredDate?.rawValue ?? ""
        archived_at = asset.archivedAt ?? ""; icon_key = asset.iconKey; purchase_channel = asset.purchaseChannel ?? ""
        warranty_end_date = asset.warrantyEndDate?.rawValue ?? ""; notes = asset.notes; tags = asset.tags
    }
    public func domain() throws -> Asset {
        guard let day = LocalDay(rawValue: purchase_date), status == "ACTIVE" || status == "RETIRED" else { throw SuirenError("服务器资产日期或状态无效") }
        var a = Asset(id: id, name: name, priceCents: price_cents, purchaseDate: day)
        a.salePriceCents = sale_price_cents
        a.status = status == "ACTIVE" ? .active : .retired
        if !retired_date.isEmpty { guard let d = LocalDay(rawValue: retired_date) else { throw SuirenError("服务器退役日期无效") }; a.retiredDate = d }
        if !warranty_end_date.isEmpty { guard let d = LocalDay(rawValue: warranty_end_date) else { throw SuirenError("服务器保修日期无效") }; a.warrantyEndDate = d }
        a.archivedAt = archived_at.isEmpty ? nil : archived_at; a.iconKey = icon_key
        a.purchaseChannel = purchase_channel.isEmpty ? nil : purchase_channel; a.notes = notes; a.tags = tags
        try a.validate(importing: true); return a
    }
}

public struct ExpiryPayload: Codable, Equatable, Sendable {
    public var id: String
    public var base_version: Int64 = 0
    public var deleted = false
    public var name = ""
    public var category = ""
    public var package_expiry_date = ""
    public var opened_date = ""
    public var opened_validity_days = 0
    public var location = ""
    public var notes = ""
    public var status = "IN_USE"
    public var archived_at = ""
    enum CodingKeys: String, CodingKey { case id, base_version, deleted, name, category, package_expiry_date, opened_date, opened_validity_days, location, notes, status, archived_at }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        base_version = try c.decodeIfPresent(Int64.self, forKey: .base_version) ?? 0
        deleted = try c.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        category = try c.decodeIfPresent(String.self, forKey: .category) ?? ""
        package_expiry_date = try c.decodeIfPresent(String.self, forKey: .package_expiry_date) ?? ""
        opened_date = try c.decodeIfPresent(String.self, forKey: .opened_date) ?? ""
        opened_validity_days = try c.decodeIfPresent(Int.self, forKey: .opened_validity_days) ?? 0
        location = try c.decodeIfPresent(String.self, forKey: .location) ?? ""
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? "IN_USE"
        archived_at = try c.decodeIfPresent(String.self, forKey: .archived_at) ?? ""
    }
    public init(_ item: ExpiryItem, version: Int64 = 0, deleted: Bool = false) {
        id = item.id; base_version = version; self.deleted = deleted; name = item.name; category = item.category
        package_expiry_date = item.packageExpiryDate.rawValue; opened_date = item.openedDate?.rawValue ?? ""
        opened_validity_days = item.openedValidityDays ?? 0; location = item.location; notes = item.notes
        status = item.status == .inUse ? "IN_USE" : item.status == .usedUp ? "USED_UP" : "DISCARDED"; archived_at = item.archivedAt ?? ""
    }
    public func domain() throws -> ExpiryItem {
        guard let day = LocalDay(rawValue: package_expiry_date), ["IN_USE", "USED_UP", "DISCARDED"].contains(status) else { throw SuirenError("服务器用品日期或状态无效") }
        var e = ExpiryItem(id: id, name: name, category: category, packageExpiryDate: day)
        if !opened_date.isEmpty { guard let d = LocalDay(rawValue: opened_date) else { throw SuirenError("服务器开封日期无效") }; e.openedDate = d }
        e.openedValidityDays = opened_validity_days == 0 ? nil : opened_validity_days
        e.location = location; e.notes = notes; e.status = status == "IN_USE" ? .inUse : status == "USED_UP" ? .usedUp : .discarded
        e.archivedAt = archived_at.isEmpty ? nil : archived_at; try e.validate(); return e
    }
}

public struct SyncRequest: Codable, Equatable, Sendable {
    public var cursor: Int64 = 0
    public var expiry_cursor: Int64 = 0
    public var idempotency_key: String = UUID().uuidString
    public var changes: [AssetPayload] = []
    public var expiry_changes: [ExpiryPayload] = []
    public init() {}
}
public struct AssetChange: Codable, Sendable {
    public var cursor: Int64
    public var version: Int64
    public var deleted_at: String
    public var asset: AssetPayload
    public init(cursor: Int64, version: Int64, deleted_at: String = "", asset: AssetPayload) {
        self.cursor = cursor; self.version = version; self.deleted_at = deleted_at; self.asset = asset
    }
}
public struct ExpiryChange: Codable, Sendable {
    public var cursor: Int64
    public var version: Int64
    public var deleted_at: String
    public var item: ExpiryPayload
    public init(cursor: Int64, version: Int64, deleted_at: String = "", item: ExpiryPayload) {
        self.cursor = cursor; self.version = version; self.deleted_at = deleted_at; self.item = item
    }
}
public struct AssetConflict: Codable, Sendable {
    public var id: String
    public var base_version: Int64
    public var remote_version: Int64
    public var remote: AssetPayload?
    public init(id: String, base_version: Int64, remote_version: Int64, remote: AssetPayload?) {
        self.id = id; self.base_version = base_version; self.remote_version = remote_version; self.remote = remote
    }
}
public struct ExpiryConflict: Codable, Sendable {
    public var id: String
    public var base_version: Int64
    public var remote_version: Int64
    public var remote: ExpiryPayload?
}
public struct SyncResponse: Codable, Sendable {
    public var next_cursor: Int64 = 0
    public var next_expiry_cursor: Int64 = 0
    public var applied: [AssetChange] = []
    public var changes: [AssetChange] = []
    public var applied_expiry: [ExpiryChange] = []
    public var expiry_changes: [ExpiryChange] = []
    public var conflicts: [AssetConflict] = []
    public var expiry_conflicts: [ExpiryConflict] = []
    public init() {}
}

public enum RecordPayload: Codable, Equatable, Sendable {
    case asset(AssetPayload), expiry(ExpiryPayload)
    public var id: String { switch self { case .asset(let a): return a.id; case .expiry(let e): return e.id } }
    public var kind: String { switch self { case .asset: return "asset"; case .expiry: return "expiry" } }
    public var key: String { "\(kind)/\(id)" }
    public var name: String { switch self { case .asset(let a): return a.name; case .expiry(let e): return e.name } }
    public var deleted: Bool { switch self { case .asset(let a): return a.deleted; case .expiry(let e): return e.deleted } }
    func copy(id: String) -> Self {
        let suffix = "（副本）"
        switch self {
        case .asset(var a): a.id = id; a.name += suffix; a.base_version = 0; return .asset(a)
        case .expiry(var e):
            e.id = id
            // A maximum-length name stays intact; the new record id distinguishes its copy.
            if e.name.utf16.count + suffix.utf16.count <= 200 { e.name += suffix }
            e.base_version = 0; return .expiry(e)
        }
    }
}
public struct JournalRecord: Codable, Equatable, Identifiable, Sendable {
    public var payload: RecordPayload
    public var revision = UUID().uuidString
    public var version: Int64 = 0
    public var dirty = true
    public var remoteVersion: Int64?
    public var remote: RecordPayload?
    public var id: String { payload.key }
}
public struct FrozenBatch: Codable, Equatable, Sendable {
    public var request: SyncRequest
    public var revisions: [String: String]
}
public struct SyncSession: Codable, Equatable, Sendable {
    public var target: String
    public var cursor: Int64 = 0
    public var expiryCursor: Int64 = 0
    public var batch: FrozenBatch?
    public var lastSyncedAt: String?
}
public enum ConflictResolution: Sendable { case local, remote, both }
