import Foundation
import SuirenCore

/// Resolve semantic keys explicitly so custom String-based components and dynamic labels
/// use the same native catalog as SwiftUI literals. User-entered values never become keys.
enum L10n {
    static func text(_ key: String, _ arguments: String...) -> String {
        format(key, arguments: arguments)
    }
    private static func format(_ key: String, arguments: [String]) -> String {
        let value = Bundle.main.localizedString(forKey: key, value: nil, table: nil)
        return String(format: value, locale: Locale.current, arguments: arguments.map { $0 as CVarArg })
    }
    static func count(_ key: String, _ count: Int) -> String {
        let value = Bundle.main.localizedString(forKey: key, value: nil, table: nil)
        return String(format: value, locale: Locale.current, Int64(count))
    }
    static func assetStatus(_ raw: String) -> String {
        switch raw {
        case "ACTIVE", "Active": return text("ui.active")
        case "RETIRED", "Retired": return text("ui.retired")
        default: return raw
        }
    }
    static func expiryStatus(_ raw: String) -> String {
        switch raw {
        case "IN_USE", "InUse": return text("status.expiry.in_use")
        case "USED_UP", "UsedUp": return text("ui.used_up")
        case "DISCARDED", "Discarded": return text("ui.discarded")
        default: return raw
        }
    }
    static func error(_ error: Error) -> String {
        if let known = error as? AppPresentationError { return format(known.key, arguments: known.arguments) }
        let presentation = ErrorPresentation(error)
        return format(presentation.code.rawValue, arguments: presentation.arguments)
    }
}

extension AssetFilter {
    var title: String { switch self { case .all: return L10n.text("ui.all"); case .active: return L10n.text("ui.active"); case .retired: return L10n.text("ui.retired"); case .archived: return L10n.text("ui.archived") } }
}
extension AssetSort {
    var title: String { switch self { case .purchase: return L10n.text("ui.purchase_date"); case .price: return L10n.text("ui.purchase_amount_"); case .daily: return L10n.text("ui.daily_cost"); case .held: return L10n.text("ui.days_held") } }
}

/// Known platform UI failures are explicit resources rather than free-form diagnostic text.
struct AppPresentationError: Error {
    let key: String
    let arguments: [String]
    init(_ key: String, _ arguments: String...) { self.key = key; self.arguments = arguments }
}
