import Foundation

public struct SuirenError: LocalizedError, Equatable {
    public let message: String
    public init(_ message: String) { self.message = message }
    public var errorDescription: String? { message }
}

/// A Gregorian date without timezone or time-of-day. Wire and backup representation is YYYY-MM-DD.
public struct LocalDay: RawRepresentable, Codable, Hashable, Comparable, Sendable {
    public let rawValue: String
    public init?(rawValue: String) {
        guard rawValue.count == 10, rawValue.range(of: #"^\d{4}-\d{2}-\d{2}$"#, options: .regularExpression) != nil,
              let date = Self.formatter.date(from: rawValue), Self.formatter.string(from: date) == rawValue else { return nil }
        self.rawValue = rawValue
    }
    private static var formatter: DateFormatter {
        let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(secondsFromGMT: 0)
        f.dateFormat = "yyyy-MM-dd"; f.isLenient = false; return f
    }
    public init(date: Date, timeZone: TimeZone = .current) {
        let f = Self.formatter; f.timeZone = timeZone; rawValue = f.string(from: date)
    }
    public static var today: Self { Self(date: Date()) }
    public var date: Date { Self.formatter.date(from: rawValue)! }
    public func adding(_ days: Int) -> Self {
        var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(secondsFromGMT: 0)!
        return Self(date: c.date(byAdding: .day, value: days, to: date)!, timeZone: c.timeZone)
    }
    public func distance(to other: Self) -> Int { Int((other.date.timeIntervalSince(date) / 86400).rounded()) }
    public static func < (lhs: Self, rhs: Self) -> Bool { lhs.rawValue < rhs.rawValue }
    public init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer().decode(String.self)
        guard let day = Self(rawValue: value) else { throw SuirenError("无效日期：\(value)") }; self = day
    }
    public func encode(to encoder: Encoder) throws { var c = encoder.singleValueContainer(); try c.encode(rawValue) }
}

public enum AssetStatus: String, Codable, CaseIterable, Sendable { case active = "Active", retired = "Retired" }
public enum ExpiryStatus: String, Codable, CaseIterable, Sendable { case inUse = "InUse", usedUp = "UsedUp", discarded = "Discarded" }
public enum ExpiryBucket: String, CaseIterable { case expired, today, soon, normal, inactive }

public struct Asset: Codable, Identifiable, Equatable, Sendable {
    public var id: String
    public var name: String
    public var priceCents: Int64
    public var purchaseDate: LocalDay
    public var status: AssetStatus = .active
    public var retiredDate: LocalDay?
    public var archivedAt: String?
    public var iconKey: String = "item"
    public var purchaseChannel: String?
    public var warrantyEndDate: LocalDay?
    public var notes: String = ""
    public var tags: [String] = []
    public var createdAt: String = ""
    public var updatedAt: String = ""
    public init(id: String = UUID().uuidString, name: String, priceCents: Int64, purchaseDate: LocalDay) {
        self.id = id; self.name = name; self.priceCents = priceCents; self.purchaseDate = purchaseDate
    }
    public func heldDays(on today: LocalDay = .today) -> Int {
        max(1, purchaseDate.distance(to: status == .retired ? (retiredDate ?? today) : today) + 1)
    }
    public func dailyCostCents(on today: LocalDay = .today) -> Int64 {
        let days = Int64(heldDays(on: today)), q = priceCents / days
        return q + (priceCents % days >= (days + 1) / 2 ? 1 : 0)
    }
    public func validate(on today: LocalDay = .today, importing: Bool = false) throws {
        guard !id.isEmpty, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              priceCents >= 0, importing || purchaseDate <= today else { throw SuirenError("请检查名称、金额和购买日期") }
        guard importing || warrantyEndDate == nil || warrantyEndDate! >= purchaseDate else { throw SuirenError("保修截止日不能早于购买日期") }
        guard (status == .active && retiredDate == nil) ||
                (status == .retired && retiredDate != nil && retiredDate! >= purchaseDate && (importing || retiredDate! <= today)) else { throw SuirenError("退役日期必须在购买日至今天之间") }
        guard notes.utf16.count <= 2000, tags.count <= 20, tags.allSatisfy({ !$0.isEmpty && $0.utf16.count <= 30 }) else { throw SuirenError("备注最多 2000 字，标签最多 20 个且每个最多 30 字") }
        try validateTimestamps([createdAt, updatedAt, archivedAt ?? ""])
    }
    enum CodingKeys: String, CodingKey { case id, name, priceCents, purchaseDate, status, retiredDate, archivedAt, iconKey, purchaseChannel, warrantyEndDate, notes, tags, createdAt, updatedAt }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id); name = try c.decode(String.self, forKey: .name)
        priceCents = try c.decode(Int64.self, forKey: .priceCents); purchaseDate = try c.decode(LocalDay.self, forKey: .purchaseDate)
        let raw = try c.decodeIfPresent(String.self, forKey: .status) ?? "Active"
        guard let s = AssetStatus(rawValue: raw) ?? (raw == "ACTIVE" ? .active : raw == "RETIRED" ? .retired : nil) else { throw SuirenError("未知资产状态") }; status = s
        retiredDate = try c.decodeIfPresent(LocalDay.self, forKey: .retiredDate); archivedAt = try c.decodeIfPresent(String.self, forKey: .archivedAt)
        iconKey = try c.decodeIfPresent(String.self, forKey: .iconKey) ?? "devices"; purchaseChannel = try c.decodeIfPresent(String.self, forKey: .purchaseChannel)
        warrantyEndDate = try c.decodeIfPresent(LocalDay.self, forKey: .warrantyEndDate); notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        tags = try c.decodeIfPresent([String].self, forKey: .tags) ?? []; createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt) ?? ""
        updatedAt = try c.decodeIfPresent(String.self, forKey: .updatedAt) ?? ""
    }
}

public struct ExpiryItem: Codable, Identifiable, Equatable, Sendable {
    public var id: String
    public var name: String
    public var category: String
    public var packageExpiryDate: LocalDay
    public var openedDate: LocalDay?
    public var openedValidityDays: Int?
    public var location: String = ""
    public var notes: String = ""
    public var status: ExpiryStatus = .inUse
    public var archivedAt: String?
    public var createdAt: String = ""
    public var updatedAt: String = ""
    public init(id: String = UUID().uuidString, name: String, category: String, packageExpiryDate: LocalDay) {
        self.id = id; self.name = name; self.category = category; self.packageExpiryDate = packageExpiryDate
    }
    public var actualExpiryDate: LocalDay {
        guard let openedDate, let openedValidityDays else { return packageExpiryDate }
        // Avoid constructing an out-of-range date for a large Android Int validity.
        if openedValidityDays - 1 >= openedDate.distance(to: packageExpiryDate) { return packageExpiryDate }
        return min(packageExpiryDate, openedDate.adding(openedValidityDays - 1))
    }
    public func bucket(on today: LocalDay = .today, soonDays: Int = 7) -> ExpiryBucket {
        guard status == .inUse, archivedAt == nil else { return .inactive }
        let end = actualExpiryDate
        return end < today ? .expired : end == today ? .today : end <= today.adding(soonDays) ? .soon : .normal
    }
    public func validate() throws {
        guard !id.isEmpty, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, name.utf16.count <= 200,
              !category.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, category.utf16.count <= 100, location.utf16.count <= 200, notes.utf16.count <= 2000 else { throw SuirenError("请检查用品名称、分类、位置和备注") }
        guard (openedDate == nil) == (openedValidityDays == nil), openedValidityDays == nil || (1...Int(Int32.max)).contains(openedValidityDays!) else { throw SuirenError("开封日期与有效天数须同时填写，有效天数须为正整数") }
        try validateTimestamps([createdAt, updatedAt, archivedAt ?? ""])
    }
    enum CodingKeys: String, CodingKey { case id, name, category, packageExpiryDate, openedDate, openedValidityDays, location, notes, status, archivedAt, createdAt, updatedAt }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id); name = try c.decode(String.self, forKey: .name)
        category = try c.decode(String.self, forKey: .category); packageExpiryDate = try c.decode(LocalDay.self, forKey: .packageExpiryDate)
        openedDate = try c.decodeIfPresent(LocalDay.self, forKey: .openedDate); openedValidityDays = try c.decodeIfPresent(Int.self, forKey: .openedValidityDays)
        location = try c.decodeIfPresent(String.self, forKey: .location) ?? ""; notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        status = try c.decodeIfPresent(ExpiryStatus.self, forKey: .status) ?? .inUse; archivedAt = try c.decodeIfPresent(String.self, forKey: .archivedAt)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt) ?? ""; updatedAt = try c.decodeIfPresent(String.self, forKey: .updatedAt) ?? ""
    }
}

public func timestamp() -> String { ISO8601DateFormatter().string(from: Date()) }
func validateTimestamps(_ values: [String]) throws {
    for value in values where !value.isEmpty {
        let f = ISO8601DateFormatter(); f.formatOptions.insert(.withFractionalSeconds)
        guard f.date(from: value) != nil || ISO8601DateFormatter().date(from: value) != nil else { throw SuirenError("时间戳无效") }
    }
}

public enum Money {
    public static func parse(_ input: String) throws -> Int64 {
        let value = input.trimmingCharacters(in: .whitespaces)
        guard value.range(of: #"^\d+(\.\d{1,2})?$"#, options: .regularExpression) != nil else { throw SuirenError("金额须为非负数字，最多两位小数") }
        let parts = value.split(separator: "."), fraction = parts.count == 2 ? String(parts[1]).padding(toLength: 2, withPad: "0", startingAt: 0) : "00"
        guard let cents = Int64(String(parts[0]) + fraction) else { throw SuirenError("金额过大") }; return cents
    }
    public static func text(_ cents: Int64) -> String { "\(cents / 100).\(String(format: "%02lld", cents % 100))" }
}

public struct ServerAddress: Codable, Equatable, Identifiable, Sendable {
    public var url: String
    public var name: String
    public var id: String { url }
    public init(url: String, name: String) { self.url = url; self.name = name }
    public static func normalize(_ input: String, allowHTTP: Bool = false) throws -> String {
        var raw = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty, !raw.contains("\\"), raw.rangeOfCharacter(from: .whitespacesAndNewlines) == nil else { throw SuirenError("请输入有效服务器地址") }
        if !raw.contains("://") {
            let host = raw.split(separator: "/").first!.split(separator: ":").first.map(String.init) ?? ""
            let local = host == "localhost" || raw.hasPrefix("[") || host.range(of: #"^[0-9.]+$"#, options: .regularExpression) != nil
            raw = "\(local ? "http" : "https")://\(raw)"
        }
        guard var c = URLComponents(string: raw), let host = c.host, !host.isEmpty, c.user == nil, c.password == nil,
              c.query == nil, c.fragment == nil, c.scheme?.lowercased() == "https" || (allowHTTP && c.scheme?.lowercased() == "http"),
              c.port == nil || (1...65535).contains(c.port!) else { throw SuirenError("地址无效；正式版本须使用 HTTPS，不能含凭据、查询参数或片段") }
        if host.range(of: #"^\d+\.\d+\.\d+\.\d+$"#, options: .regularExpression) != nil {
            guard host.split(separator: ".").allSatisfy({ Int($0).map { (0...255).contains($0) } ?? false }) else { throw SuirenError("IPv4 地址无效") }
        }
        c.scheme = c.scheme?.lowercased(); c.host = host.lowercased()
        if (c.scheme == "https" && c.port == 443) || (c.scheme == "http" && c.port == 80) { c.port = nil }
        if !c.path.hasSuffix("/") { c.path += "/" }
        guard let result = c.url?.absoluteString else { throw SuirenError("服务器地址无效") }; return result
    }
}

public struct AppSettings: Codable, Equatable, Sendable {
    public var servers: [ServerAddress] = []
    public var activeURL: String?
    public var syncEnabled = false
    public var theme = "system"
    public var soonDays = 7
    public var syncAfterChanges = true
    public var intervalMinutes = 15
    public init() {}
}

public enum AccountValidation {
    public static let passwordHint = "8–72 个字符，仅限英文字母、数字、半角符号和空格；不支持中文或换行，无大小写、数字组合要求。"
    public static func validate(username: String, password: String, confirmation: String? = nil) throws {
        guard !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, username.trimmingCharacters(in: .whitespacesAndNewlines).utf8.count <= 100 else { throw SuirenError("账号不能为空且最多 100 个 UTF-8 字节") }
        guard !password.isEmpty else { throw SuirenError("请输入密码") }
        if let confirmation {
            guard (8...72).contains(password.utf8.count), password.utf8.allSatisfy({ (32...126).contains($0) }) else { throw SuirenError(passwordHint) }
            guard password == confirmation else { throw SuirenError("两次密码不一致") }
        }
    }
}
