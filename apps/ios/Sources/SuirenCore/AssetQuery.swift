import Foundation

public enum AssetFilter: String, CaseIterable { case all, active, retired, archived }
public enum AssetSort: String, CaseIterable { case purchase, price, daily, held }
public struct AssetQuery {
    public var filter: AssetFilter = .active
    public var sort: AssetSort = .purchase
    public var descending = true
    public var tags: Set<String> = []
    public var search = ""
    public init() {}
    public func apply(_ assets: [Asset], today: LocalDay = .today) -> [Asset] {
        assets.filter { a in
            let status = filter == .all || (filter == .archived ? a.archivedAt != nil : a.archivedAt == nil && (filter == .active ? a.status == .active : a.status == .retired))
            let text = search.trimmingCharacters(in: .whitespacesAndNewlines)
            return status && tags.isSubset(of: Set(a.tags)) && (text.isEmpty || ([a.name] + a.tags).joined(separator: " ").localizedCaseInsensitiveContains(text))
        }.sorted { a, b in
            let left: Int64, right: Int64
            switch sort {
            case .purchase: left = Int64(a.purchaseDate.date.timeIntervalSince1970); right = Int64(b.purchaseDate.date.timeIntervalSince1970)
            case .price: left = a.priceCents; right = b.priceCents
            case .daily: left = a.dailyCostCents(on: today); right = b.dailyCostCents(on: today)
            case .held: left = Int64(a.heldDays(on: today)); right = Int64(b.heldDays(on: today))
            }
            return left == right ? a.id < b.id : descending ? left > right : left < right
        }
    }
}
public struct AssetSummary {
    public var count: Int
    public var totalCents: Decimal
    public var dailyCents: Decimal
    public var active: Int
    public var retired: Int
    public var longestDays: Int
    public init(_ assets: [Asset], today: LocalDay = .today) {
        count = assets.count; totalCents = assets.reduce(Decimal(0)) { $0 + Decimal($1.priceCents) }
        dailyCents = assets.filter { $0.archivedAt == nil && $0.status == .active }.reduce(Decimal(0)) { $0 + Decimal($1.dailyCostCents(on: today)) }
        active = assets.filter { $0.archivedAt == nil && $0.status == .active }.count
        retired = assets.filter { $0.archivedAt == nil && $0.status == .retired }.count
        longestDays = assets.map { $0.heldDays(on: today) }.max() ?? 0
    }
}
