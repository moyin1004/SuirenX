import XCTest
@testable import SuirenCore

final class LocalizationTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
    }

    func testCurrencyRemainsCNYInEveryLocale() {
        let cents = Decimal(123456)
        let english = DisplayFormatting.currency(cents: cents, locale: Locale(identifier: "en_US"))
        let chinese = DisplayFormatting.currency(cents: cents, locale: Locale(identifier: "zh_Hans_CN"))
        XCTAssertTrue(english.contains("CN¥") || english.contains("CNY"), english)
        XCTAssertFalse(english.contains("$"))
        XCTAssertTrue(english.contains("1,234.56"), english)
        XCTAssertTrue(chinese.contains("1,234.56"), chinese)
        XCTAssertEqual(Money.text(123456), "1234.56", "Editable and backup decimal values stay canonical")
    }

    func testLocalizedDateNeverChangesDateOnlyValue() {
        let day = LocalDay(rawValue: "2026-01-01")!
        XCTAssertEqual(DisplayFormatting.day(day, locale: Locale(identifier: "en_US")), "Jan 1, 2026")
        XCTAssertEqual(DisplayFormatting.day(day, locale: Locale(identifier: "zh_Hans_CN")), "2026年1月1日")
        XCTAssertEqual(day.rawValue, "2026-01-01")
        XCTAssertEqual(LocalDay(date: day.date, timeZone: TimeZone(secondsFromGMT: 0)!), day)
    }

    func testStructuredErrorsPreserveArgumentsAndHaveBothTranslations() throws {
        let error = SuirenError(.invalid_date, arguments: ["2026-02-30"])
        XCTAssertEqual(error.code, .invalid_date)
        XCTAssertEqual(error.arguments, ["2026-02-30"])
        XCTAssertEqual(error.message, "无效日期：2026-02-30")
        let data = try Data(contentsOf: iosRoot.appendingPathComponent("App/Resources/Localizable.xcstrings"))
        let catalog = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(catalog["strings"] as? [String: [String: Any]])
        for code in SuirenError.Code.allCases {
            let localizations = try XCTUnwrap(strings[code.rawValue]?["localizations"] as? [String: Any], code.rawValue)
            XCTAssertNotNil(localizations["zh-Hans"], code.rawValue)
            XCTAssertNotNil(localizations["en"], code.rawValue)
        }
    }

    func testUnknownErrorsUseGenericPresentationAndRetainCause() {
        let raw = NSError(domain: "SQLite", code: 7, userInfo: [NSLocalizedDescriptionKey: "token=SECRET /private/sensitive.sqlite"])
        let presentation = ErrorPresentation(raw)
        XCTAssertEqual(presentation.code, .unexpected)
        XCTAssertTrue(presentation.arguments.isEmpty)
        XCTAssertTrue((presentation.cause as NSError) === raw)
        XCTAssertFalse(presentation.code.defaultMessage.contains("SECRET"))
        XCTAssertEqual(ErrorPresentation(SuirenError("raw diagnostic")).code, .unexpected)
    }

    func testRFC3339FractionalTimestampsUseLocaleDisplay() {
        let locale = Locale(identifier: "en_US")
        let fractional = "2026-10-20T03:04:05.123456789Z"
        let whole = "2026-10-20T03:04:05Z"
        XCTAssertEqual(DisplayFormatting.timestamp(fractional, locale: locale), DisplayFormatting.timestamp(whole, locale: locale))
        XCTAssertNotEqual(DisplayFormatting.timestamp(fractional, locale: locale), fractional)
    }

    func testCatalogCoversExplicitUIKeysAndNativePluralCategories() throws {
        let data = try Data(contentsOf: iosRoot.appendingPathComponent("App/Resources/Localizable.xcstrings"))
        let catalog = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(catalog["sourceLanguage"] as? String, "zh-Hans")
        let strings = try XCTUnwrap(catalog["strings"] as? [String: [String: Any]])
        let regex = try NSRegularExpression(pattern: #"(?:L10n\.(?:text|count)|AppPresentationError)\("([^"]+)""#)
        let files = try FileManager.default.contentsOfDirectory(at: iosRoot.appendingPathComponent("App"), includingPropertiesForKeys: nil).filter { $0.pathExtension == "swift" }
        for file in files {
            let source = try String(contentsOf: file)
            for match in regex.matches(in: source, range: NSRange(source.startIndex..., in: source)) {
                let key = String(source[Range(match.range(at: 1), in: source)!])
                let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: Any], "Missing key \(key)")
                XCTAssertNotNil(localizations["zh-Hans"], key)
                XCTAssertNotNil(localizations["en"], key)
            }
        }
        for key in ["ui.days", "ui.items", "count.pending", "ui.expires_in_days"] {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]], key)
            let english = try XCTUnwrap(localizations["en"]?["variations"] as? [String: [String: Any]], key)
            XCTAssertNotNil(english["plural"]?["one"], key)
            XCTAssertNotNil(english["plural"]?["other"], key)
        }
    }
}
