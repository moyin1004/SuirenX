import XCTest

@MainActor final class SuirenXUITests: XCTestCase {
    private var app: XCUIApplication!
    private func launch(extraArguments: [String] = []) {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication(); app.launchArguments = ["-ui-testing", "-AppleLanguages", "(zh-Hans)", "-AppleLocale", "zh_CN"]
        app.launchArguments += extraArguments
        app.launchEnvironment["SUIRENX_TEST_RUN"] = UUID().uuidString
        app.launch()
        XCTAssertTrue(app.buttons["nav.assets"].waitForExistence(timeout: 15))
    }
    private func shot(_ name: String) {
        // Application bounds can lag a rotation/window resize; capture the real display.
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
    private func tapText(_ text: String) {
        let label = app.staticTexts[text].firstMatch
        let target = label.exists ? label : app.buttons[text].firstMatch
        if !target.isHittable { app.swipeUp() }
        XCTAssertTrue(target.waitForExistence(timeout: 5)); target.tap()
    }
    private func goBack() { app.navigationBars.buttons["BackButton"].firstMatch.tap() }
    private func tapControl(_ element: XCUIElement) {
        XCTAssertTrue(element.waitForExistence(timeout: 5))
        if element.isHittable { element.tap(); return }
        // UIKit's sheet toolbar can report an invalid AX hit point while its frame is visible.
        let frame = element.frame
        app.windows.firstMatch.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: frame.midX, dy: frame.midY)).tap()
    }
    private func saveEditor(_ identifier: String) {
        let save = app.buttons[identifier]
        // iPad's floating sheet can move between keyboard dismissal and the synthesized tap.
        // Re-resolve the toolbar after that transition instead of reusing its former frame.
        for _ in 0..<2 {
            if !save.exists { return }
            let frame = save.frame
            app.windows.firstMatch.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: frame.midX, dy: frame.midY)).tap()
            if save.waitForNonExistence(timeout: 2) { return }
        }
        XCTAssertTrue(save.waitForNonExistence(timeout: 5))
    }
    private func dismissKeyboard() {
        let assetForm = app.collectionViews.containing(.textField, identifier: "asset.price").firstMatch
        let form = assetForm.exists ? assetForm : app.collectionViews.containing(.textField, identifier: "expiry.category").firstMatch
        let hide = app.buttons["Hide keyboard"]
        if hide.exists && app.windows.firstMatch.frame.intersects(app.keyboards.firstMatch.frame) {
            let window = app.windows.firstMatch, frame = hide.frame
            if window.frame.contains(CGPoint(x: frame.midX, y: frame.midY)) { tapControl(hide) }
            else { window.coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.96)).tap() }
        } else if form.exists { form.swipeDown() }
        let hidden = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == false"), object: app.keyboards.firstMatch)
        XCTAssertEqual(XCTWaiter.wait(for: [hidden], timeout: 5), .completed)
    }
    private func record(_ name: String) -> XCUIElement { app.buttons.matching(NSPredicate(format: "label CONTAINS %@", name)).firstMatch }
    private func reveal(_ element: XCUIElement) {
        for _ in 0..<12 {
            let window = app.windows.firstMatch.frame
            let scroll = app.scrollViews.allElementsBoundByIndex.first { $0.isHittable && $0.frame.height > 100 && window.intersects($0.frame) }
            let visible = (scroll?.frame ?? window).intersection(window).insetBy(dx: 0, dy: 8)
            if element.exists && element.isHittable && visible.contains(CGPoint(x: element.frame.midX, y: element.frame.midY)) { return }
            if let scroll { scroll.swipeUp() }
            else { app.swipeUp() }
        }
        XCTAssertTrue(element.waitForExistence(timeout: 5)); XCTAssertTrue(element.isHittable)
    }
    func testBilingualLocalizationKeepsUserDataAndNativePluralRules() {
        verifyBilingualLocalization()
    }
    func testBilingualLocalizationAtLargestDynamicType() {
        verifyBilingualLocalization(largeText: true)
    }
    private func verifyBilingualLocalization(largeText: Bool = false) {
        let run = UUID().uuidString
        func launchLanguage(_ language: String, locale: String) {
            app = XCUIApplication()
            app.launchArguments = ["-ui-testing", "-AppleLanguages", "(\(language))", "-AppleLocale", locale]
            app.launchEnvironment["SUIRENX_TEST_RUN"] = run
            if largeText { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
            app.launch()
            XCTAssertTrue(app.buttons["nav.assets"].waitForExistence(timeout: 15))
        }
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        launchLanguage("en", locale: "en_US")
        XCTAssertTrue(app.staticTexts["0 items need attention"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Assets and daily supplies are safely stored on this device."].exists)
        app.buttons["nav.assets"].tap(); app.buttons["asset.add"].tap()
        let name = app.textFields["asset.name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5)); name.tap(); name.typeText("Original user name")
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertEqual(name.value as? String, "Original user name", "Returning to the app keeps the unfinished draft")
        app.buttons["asset.save"].tap()
        let validation = app.staticTexts["Enter a nonnegative amount with at most two decimal places"]
        reveal(validation); XCTAssertTrue(validation.exists)
        let price = app.textFields["asset.price"]
        // At accessibility text sizes the validation section is several screens below
        // the amount field. Scroll the real Form until that field is realized again.
        for _ in 0..<12 {
            if price.exists && price.isHittable { break }
            app.collectionViews.firstMatch.swipeDown()
        }
        XCTAssertTrue(price.waitForExistence(timeout: 5)); XCTAssertTrue(price.isHittable)
        price.tap(); price.typeText("100")
        saveEditor("asset.save")
        reveal(record("Original user name"))
        XCTAssertTrue(record("Original user name").exists)
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Held for 1 day")).firstMatch.exists)
        XCTAssertFalse(app.alerts.firstMatch.exists, "Localized UI acceptance must not hide a credential or runtime error")
        if largeText {
            let window = app.windows.firstMatch.frame
            for element in [record("Original user name"), app.buttons["asset.add"], app.buttons["asset.filter"], app.buttons["asset.tagFilter"]] {
                XCTAssertGreaterThanOrEqual(element.frame.minX, window.minX - 1, "Large text must stay inside the viewport")
                XCTAssertLessThanOrEqual(element.frame.maxX, window.maxX + 1, "Large text must stay inside the viewport")
            }
            XCTAssertEqual(app.buttons["nav.overview"].label, "Overview")
            XCTAssertEqual(app.buttons["nav.settings"].label, "Settings")
        }
        shot("localization-english-asset-and-singular")
        app.buttons["nav.settings"].tap()
        let language = app.buttons["settings.row.language"]; reveal(language); language.tap()
        XCTAssertTrue(app.staticTexts["English"].waitForExistence(timeout: 5))
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.buttons["language.openSettings"].waitForExistence(timeout: 5))
        shot("localization-english-language-landscape")
        XCUIDevice.shared.orientation = .portrait
        app.buttons["language.openSettings"].tap()
        app.activate()
        XCTAssertTrue(app.buttons["language.openSettings"].waitForExistence(timeout: 5), "Returning from Settings preserves the navigation destination")
        shot("localization-english-language-settings")
        app.terminate()
        launchLanguage("zh-Hans", locale: "zh_CN")
        app.buttons["nav.assets"].tap()
        reveal(record("Original user name"))
        XCTAssertTrue(record("Original user name").exists, "Language changes never translate or discard user data")
        XCTAssertTrue(app.staticTexts["资产概览"].exists)
        shot("localization-chinese-user-data-preserved")
        app.terminate()
        launchLanguage("fr", locale: "fr_FR")
        app.buttons["nav.assets"].tap()
        XCTAssertTrue(app.staticTexts["资产概览"].waitForExistence(timeout: 5), "Unsupported languages fall back to the Chinese development language")
        reveal(record("Original user name")); XCTAssertTrue(record("Original user name").exists)
    }

    func testIPadWindowResizeAndDraftPersistence() throws {
        guard ProcessInfo.processInfo.environment["SUIRENX_IOS_WINDOWING_TESTS"] == "1" else { throw XCTSkip("Requires a dedicated iPadOS Windowed Apps environment; UI layout alone is not window-resize evidence") }
        launch()
        guard app.windows.firstMatch.frame.width >= 700 else { throw XCTSkip("iPad windowing acceptance") }
        XCUIDevice.shared.orientation = .landscapeLeft
        let original = app.windows.firstMatch.frame
        defer {
            let window = app.windows.firstMatch, current = window.frame
            window.coordinate(withNormalizedOffset: CGVector(dx: 0.99, dy: 0.99)).press(forDuration: 0.1, thenDragTo: window.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: original.maxX - current.minX - 4, dy: original.maxY - current.minY - 4)))
            XCUIDevice.shared.orientation = .portrait
        }
        app.buttons["nav.assets"].tap(); app.buttons["asset.add"].tap()
        app.textFields["asset.name"].tap(); app.textFields["asset.name"].typeText("分屏草稿")
        app.textFields["asset.price"].tap(); app.textFields["asset.price"].typeText("12")
        // Drag the real system resize handle; no app frame override is used.
        let window = app.windows.firstMatch
        window.coordinate(withNormalizedOffset: CGVector(dx: 0.99, dy: 0.99)).press(forDuration: 0.1, thenDragTo: window.coordinate(withNormalizedOffset: CGVector(dx: 0.49, dy: 0.95)))
        let narrow = XCTNSPredicateExpectation(predicate: NSPredicate { [self] _, _ in app.windows.firstMatch.frame.width < 700 }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [narrow], timeout: 10), .completed)
        XCTAssertEqual(app.textFields["asset.name"].value as? String, "分屏草稿")
        XCTAssertEqual(app.textFields["asset.price"].value as? String, "12"); shot("ipad-real-tiled-draft")
        app.typeKey("s", modifierFlags: .command)
        XCTAssertTrue(app.textFields["asset.name"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.buttons["asset.save"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(record("分屏草稿").waitForExistence(timeout: 5)); shot("ipad-real-tiled-saved")
        tapControl(app.buttons["nav.tools"]); tapControl(app.buttons["tools.expiry"])
        XCTAssertTrue(app.buttons["expiry.add"].waitForExistence(timeout: 5)); shot("ipad-real-tiled-tools")
    }
    func testSettingsRowsRespondAcrossEntireRow() {
        launch()
        if app.windows.firstMatch.frame.width >= 700 { XCUIDevice.shared.orientation = .landscapeLeft }
        defer { XCUIDevice.shared.orientation = .portrait }
        app.buttons["nav.settings"].tap()
        let titles = ["账号", "同步设置", "备份与恢复", "外观", "用品临期提醒", "服务器地址", "关于"]
        // Exercise the Spacer and both padded edges, outside the text and chevron.
        let points = [CGVector(dx: 0.75, dy: 0.5), CGVector(dx: 0.02, dy: 0.08), CGVector(dx: 0.98, dy: 0.92)]
        shot("settings-full-row-before-navigation")
        for title in titles {
            for point in points {
                let row = app.buttons["settings.row.\(title)"]
                reveal(row)
                row.coordinate(withNormalizedOffset: point).tap()
                XCTAssertTrue(app.navigationBars[title].waitForExistence(timeout: 5), "\(title) must open from \(point)")
                goBack()
                XCTAssertTrue(row.waitForExistence(timeout: 5))
            }
        }
        shot("settings-full-row-all-destinations-verified")
    }
    func testBackupExportAndRestore() throws {
        launch()
        app.buttons["nav.assets"].tap(); app.buttons["asset.add"].tap()
        app.textFields["asset.name"].tap(); app.textFields["asset.name"].typeText("备份资产")
        app.textFields["asset.price"].tap(); app.textFields["asset.price"].typeText("12")
        dismissKeyboard(); tapControl(app.buttons["asset.save"])
        XCTAssertTrue(record("备份资产").waitForExistence(timeout: 5))
        app.buttons["nav.settings"].tap(); tapText("备份与恢复")
        app.buttons["导出本机 JSON"].tap()
        let filename = app.textFields["DOCPicker.filenameTextField"]
        XCTAssertTrue(filename.waitForExistence(timeout: 10))
        let local = app.cells.matching(NSPredicate(format: "identifier BEGINSWITH %@", "DOC.sidebar.item.我的")).firstMatch
        if local.exists { local.tap() }
        let name = "ui-backup-" + UUID().uuidString.prefix(8)
        filename.tap()
        let previous = filename.value as? String ?? ""
        filename.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: previous.count) + name)
        app.buttons["保存"].tap()
        XCTAssertTrue(app.staticTexts["备份已导出"].waitForExistence(timeout: 10))
        app.buttons["nav.assets"].tap(); record("备份资产").tap(); reveal(app.buttons["asset.delete"])
        app.buttons["asset.delete"].tap(); tapControl(app.buttons["确认删除"])
        XCTAssertTrue(app.buttons["asset.add"].waitForExistence(timeout: 5)); XCTAssertFalse(record("备份资产").exists)
        app.buttons["nav.settings"].tap(); tapText("备份与恢复"); app.buttons["选择备份并预览"].tap()
        let file = app.cells.matching(NSPredicate(format: "identifier BEGINSWITH %@", String(name))).firstMatch
        XCTAssertTrue(file.waitForExistence(timeout: 10))
        // In the iPad Files grid, open the document icon rather than its editable filename.
        if app.windows.firstMatch.frame.width >= 700 { file.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)).tap() }
        else { file.tap() }
        XCTAssertTrue(app.buttons["确认恢复"].waitForExistence(timeout: 10)); shot("backup-validated-preview")
        app.buttons["确认恢复"].tap(); app.buttons["创建安全备份并恢复"].tap()
        XCTAssertTrue(app.staticTexts["恢复完成，同步已暂停"].waitForExistence(timeout: 10))
        app.buttons["nav.assets"].tap(); XCTAssertTrue(record("备份资产").waitForExistence(timeout: 5)); shot("backup-restored-after-delete")
    }
    func testAccessibilityTextAndSidebarSelection() throws {
        launch(extraArguments: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        app.buttons["nav.assets"].tap(); app.buttons["asset.add"].tap()
        app.textFields["asset.name"].tap(); app.textFields["asset.name"].typeText("大字号资产")
        app.textFields["asset.price"].tap(); app.textFields["asset.price"].typeText("1")
        if app.windows.firstMatch.frame.width >= 700 {
            app.typeKey("s", modifierFlags: .command)
            XCTAssertTrue(app.buttons["asset.save"].waitForNonExistence(timeout: 5))
        } else { saveEditor("asset.save") }
        reveal(record("大字号资产")); XCTAssertTrue(record("大字号资产").exists); shot("accessibility-largest-assets")
        let frame = record("大字号资产").frame
        XCTAssertGreaterThan(frame.width, 100); XCTAssertLessThanOrEqual(frame.maxX, app.frame.maxX + 1)
        reveal(record("大字号资产")); tapControl(record("大字号资产")); reveal(app.buttons["asset.edit"])
        app.buttons["nav.settings"].tap(); tapText("关于")
        XCTAssertTrue(app.staticTexts["0.1.3 (1)"].waitForExistence(timeout: 5)); shot("accessibility-largest-about")
        app.buttons["nav.overview"].tap(); XCTAssertFalse(app.staticTexts["0.1.3 (1)"].exists)
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.buttons["nav.assets"].waitForExistence(timeout: 5)); shot("accessibility-largest-landscape")
        XCUIDevice.shared.orientation = .portrait
    }
    func testArchivedSalePriceAndCard() throws {
        launch()
        app.buttons["nav.assets"].tap()
        app.buttons["asset.filter"].tap(); app.buttons["全部"].tap()
        app.buttons["asset.add"].tap()
        app.textFields["asset.name"].tap(); app.textFields["asset.name"].typeText("卖出价格验收设备")
        app.textFields["asset.price"].tap(); app.textFields["asset.price"].typeText("168.50")
        dismissKeyboard(); tapControl(app.buttons["asset.save"])
        let card = record("卖出价格验收设备")
        XCTAssertTrue(card.waitForExistence(timeout: 5)); XCTAssertFalse(card.label.contains("卖出 ¥"))
        XCTAssertTrue(card.label.contains("/天")); let originalSize = card.frame.size
        card.tap(); reveal(app.buttons["归档记录"]); app.buttons["归档记录"].tap()
        let input = app.textFields["asset.salePrice"]
        reveal(input); input.tap(); input.typeText("80.50")
        if app.buttons["asset.salePrice.done"].exists { tapControl(app.buttons["asset.salePrice.done"]) }; reveal(app.buttons["asset.saveSalePrice"]); tapControl(app.buttons["asset.saveSalePrice"])
        shot("sale-price-editor")
        goBack()
        XCTAssertTrue(record("卖出 ¥80.50").waitForExistence(timeout: 5))
        XCTAssertEqual(card.frame.width, originalSize.width, accuracy: 1)
        XCTAssertEqual(card.frame.height, originalSize.height, accuracy: 1)
        XCTAssertTrue(card.label.contains("/天")); shot("sale-price-card")
        card.tap(); reveal(input); input.tap()
        input.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: (input.value as? String ?? "").count) + "0")
        if app.buttons["asset.salePrice.done"].exists { tapControl(app.buttons["asset.salePrice.done"]) }; reveal(app.buttons["asset.saveSalePrice"]); tapControl(app.buttons["asset.saveSalePrice"]); goBack()
        XCTAssertTrue(record("卖出 ¥0.00").waitForExistence(timeout: 5)); shot("sale-zero-card")
        card.tap(); reveal(input); input.tap(); input.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: (input.value as? String ?? "").count))
        if app.buttons["asset.salePrice.done"].exists { tapControl(app.buttons["asset.salePrice.done"]) }; reveal(app.buttons["asset.saveSalePrice"]); tapControl(app.buttons["asset.saveSalePrice"]); goBack()
        XCTAssertTrue(card.waitForExistence(timeout: 5)); XCTAssertFalse(card.label.contains("卖出 ¥")); shot("sale-empty-card")
    }
    func testOfflineAssetAndExpiryRoundTrip() throws {
        launch()
        app.buttons["nav.assets"].tap(); app.buttons["asset.add"].tap()
        app.buttons["asset.icon"].tap(); app.buttons["数码"].tap(); app.buttons["手机"].firstMatch.tap(); app.buttons["确定"].tap()
        app.textFields["asset.name"].tap(); app.textFields["asset.name"].typeText("验收资产")
        app.textFields["asset.price"].tap(); app.textFields["asset.price"].typeText("168.50")
        dismissKeyboard()
        tapControl(app.buttons["asset.save"])
        XCTAssertTrue(record("验收资产").waitForExistence(timeout: 5)); shot("assets-created")
        record("验收资产").tap()
        XCTAssertTrue(app.buttons["asset.edit"].waitForExistence(timeout: 5))
        app.swipeUp(); app.buttons["归档记录"].tap()
        XCTAssertTrue(app.buttons["恢复记录"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["asset.edit"].exists)
        app.buttons["恢复记录"].tap(); XCTAssertTrue(app.buttons["asset.edit"].waitForExistence(timeout: 5))
        app.buttons["asset.edit"].tap()
        app.textFields["购买渠道（可选）"].tap(); app.textFields["购买渠道（可选）"].typeText("验收渠道")
        dismissKeyboard(); tapControl(app.buttons["asset.save"])
        XCTAssertTrue(app.staticTexts["验收渠道"].waitForExistence(timeout: 5))
        reveal(app.buttons["退役"]); app.buttons["退役"].tap(); app.buttons["取消"].tap()
        XCTAssertTrue(app.buttons["退役"].waitForExistence(timeout: 5))
        app.buttons["退役"].tap(); tapControl(app.buttons["确定"])
        XCTAssertTrue(app.buttons["重新启用"].waitForExistence(timeout: 5)); app.buttons["重新启用"].tap()
        XCTAssertTrue(app.buttons["退役"].waitForExistence(timeout: 5)); shot("asset-edit-retirement-reactivation")
        app.buttons["nav.tools"].tap(); app.buttons["tools.expiry"].tap(); app.buttons["expiry.add"].tap()
        app.textFields["expiry.name"].tap(); app.textFields["expiry.name"].typeText("验收用品")
        app.textFields["expiry.category"].tap(); app.textFields["expiry.category"].typeText("日用")
        dismissKeyboard()
        tapControl(app.buttons["expiry.save"])
        XCTAssertTrue(record("验收用品").waitForExistence(timeout: 5)); shot("expiry-created-empty-location")
        record("验收用品").tap(); reveal(app.buttons["标为已用完"]); app.buttons["标为已用完"].tap()
        XCTAssertTrue(app.buttons["重新使用"].waitForExistence(timeout: 5)); app.buttons["重新使用"].tap()
        app.buttons["归档记录"].tap(); XCTAssertTrue(app.buttons["恢复记录"].waitForExistence(timeout: 5)); XCTAssertFalse(app.buttons["编辑用品"].exists)
        app.buttons["恢复记录"].tap(); XCTAssertTrue(app.buttons["编辑用品"].waitForExistence(timeout: 5))
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["nav.assets"].waitForExistence(timeout: 10)); app.buttons["nav.assets"].tap()
        XCTAssertTrue(record("验收资产").waitForExistence(timeout: 5)); shot("persisted-after-relaunch")
        record("验收资产").tap(); reveal(app.buttons["asset.delete"]); app.buttons["asset.delete"].tap()
        if app.buttons["取消"].exists { app.buttons["取消"].tap() }
        else { app.navigationBars.staticTexts["资产详情"].firstMatch.tap() }
        XCTAssertTrue(app.buttons["asset.edit"].exists)
        app.buttons["asset.delete"].tap(); tapControl(app.buttons["确认删除"])
        XCTAssertTrue(app.buttons["asset.add"].waitForExistence(timeout: 5)); XCTAssertFalse(record("验收资产").exists)
        app.buttons["nav.tools"].tap(); app.buttons["tools.expiry"].tap(); record("验收用品").tap()
        reveal(app.buttons["删除用品"]); app.buttons["删除用品"].tap(); tapControl(app.buttons["确认删除"])
        XCTAssertTrue(app.buttons["expiry.add"].waitForExistence(timeout: 5)); XCTAssertFalse(record("验收用品").exists)
    }
    func testSettingsNavigationAndAdaptiveLayout() throws {
        launch()
        app.buttons["nav.settings"].tap(); tapText("同步设置")
        XCTAssertTrue(app.buttons["预览并开启同步"].waitForExistence(timeout: 5)); XCTAssertFalse(app.buttons["预览并开启同步"].isEnabled)
        shot("sync-disabled-real-empty-state")
        goBack(); tapText("账号")
        XCTAssertTrue(app.textFields["account.username"].waitForExistence(timeout: 5)); shot("account-no-server")
        goBack(); tapText("服务器地址")
        XCTAssertTrue(app.buttons["添加服务器"].waitForExistence(timeout: 5)); app.buttons["添加服务器"].tap()
        XCTAssertTrue(app.buttons["测试连接"].waitForExistence(timeout: 5)); XCTAssertFalse(app.buttons["保存"].isEnabled)
        app.buttons["取消"].tap(); goBack(); tapText("外观"); tapText("深色"); goBack()
        XCTAssertTrue(app.staticTexts["深色"].waitForExistence(timeout: 5)); shot("appearance-dark")
        app.terminate(); app.launch(); XCTAssertTrue(app.buttons["nav.settings"].waitForExistence(timeout: 10)); app.buttons["nav.settings"].tap()
        XCTAssertTrue(app.staticTexts["深色"].waitForExistence(timeout: 5))
        tapText("外观"); tapText("浅色"); goBack(); shot("appearance-light")
        app.buttons["nav.overview"].tap()
        XCUIDevice.shared.orientation = .landscapeLeft
        shot("adaptive-landscape")
        XCUIDevice.shared.orientation = .portrait
    }
    func testAboutDisplaysInstalledVersionAndBuild() throws {
        launch(); app.buttons["nav.settings"].tap()
        let summary = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "SuirenX ")).firstMatch
        XCTAssertTrue(summary.waitForExistence(timeout: 5))
        let version = String(summary.label.dropFirst("SuirenX ".count))
        XCTAssertFalse(version.isEmpty)
        XCTAssertNotEqual(version, "未知")
        tapText("关于")
        XCTAssertTrue(app.staticTexts["版本号"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Build 号"].exists)
        XCTAssertTrue(app.staticTexts[version].exists)
        XCTAssertFalse(app.staticTexts["未知"].exists)
        if let expectedVersion = ProcessInfo.processInfo.environment["SUIRENX_EXPECTED_VERSION"] {
            XCTAssertEqual(version, expectedVersion)
        }
        if let expectedBuild = ProcessInfo.processInfo.environment["SUIRENX_EXPECTED_BUILD"] {
            XCTAssertTrue(app.staticTexts[expectedBuild].exists)
        }
        #if DEBUG
            XCTAssertTrue(app.staticTexts["代码"].exists)
            XCTAssertTrue(app.staticTexts["打包时间"].exists)
            XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "[0-9a-f]{7}(-dirty)?")).firstMatch.exists)
            XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}")).firstMatch.exists)
        #else
        XCTAssertFalse(app.staticTexts["代码"].exists)
        XCTAssertFalse(app.staticTexts["打包时间"].exists)
        #endif
        XCTAssertTrue(app.buttons["Fluent Emoji 开源许可"].exists)
        shot("about-installed-metadata-light")
        goBack(); tapText("外观"); tapText("深色"); goBack(); tapText("关于")
        shot("about-installed-metadata-dark")
    }
    func testMainPageHeadersAndBackgrounds() throws {
        launch()
        for (page, title) in [("overview", "燧人"), ("assets", "资产"), ("tools", "工具"), ("settings", "设置")] {
            app.buttons["nav.\(page)"].tap()
            let heading = app.staticTexts["page.title"]
            XCTAssertTrue(heading.waitForExistence(timeout: 5))
            XCTAssertEqual(heading.label, title)
            let window = app.windows.firstMatch.frame
            let leadingLimit = window.minX + (window.width >= 700 ? window.width / 2 : 70)
            XCTAssertLessThan(heading.frame.minX, leadingLimit, "Title should remain on the leading side of the page")
            XCTAssertGreaterThan(heading.frame.width, 40, "Title must remain visible rather than collapse into an overflow menu")
            XCTAssertLessThan(heading.frame.midY, window.minY + 140, "Title should be at the top of the page")
            XCTAssertFalse(app.scrollViews.staticTexts[page == "overview" ? "总览" : title].exists)
            shot("main-\(page)-light")
        }
        tapText("外观"); tapText("深色"); goBack()
        shot("main-settings-dark")
        for page in ["overview", "assets", "tools"] {
            app.buttons["nav.\(page)"].tap(); shot("main-\(page)-dark")
        }
    }
    func testTagFilterMenuPreservesIntersectionAcrossPagesAndSearch() throws {
        launch(); app.buttons["nav.assets"].tap()
        for (name, tags) in [("标签电脑", "工作,常用"), ("标签阅读器", "工作")] {
            app.buttons["asset.add"].tap()
            app.textFields["asset.name"].tap(); app.textFields["asset.name"].typeText(name)
            app.textFields["asset.price"].tap(); app.textFields["asset.price"].typeText("100")
            dismissKeyboard()
            reveal(app.textFields["asset.tags"]); app.textFields["asset.tags"].tap(); app.textFields["asset.tags"].typeText(tags)
            dismissKeyboard(); saveEditor("asset.save")
            XCTAssertTrue(record(name).waitForExistence(timeout: 5))
        }
        XCTAssertFalse(app.buttons["全部标签"].exists)
        app.buttons["asset.tagFilter"].tap()
        app.buttons["asset.tag.工作"].tap(); app.buttons["asset.tag.常用"].tap()
        shot("asset-tag-menu")
        app.buttons["asset.tags.done"].tap()
        XCTAssertTrue(record("标签电脑").exists); XCTAssertFalse(record("标签阅读器").exists)
        XCTAssertEqual(app.buttons["asset.tagFilter"].value as? String, "已选 2 个标签")
        shot("asset-tags-selected")
        app.buttons["nav.settings"].tap(); app.buttons["nav.assets"].tap()
        XCTAssertEqual(app.buttons["asset.tagFilter"].value as? String, "已选 2 个标签")
        app.buttons["搜索资产"].tap(); app.textFields["asset.search"].tap(); app.textFields["asset.search"].typeText("电脑")
        app.buttons["收起搜索"].tap()
        app.buttons["asset.tagFilter"].tap(); app.buttons["asset.tags.clear"].tap(); app.buttons["asset.tags.done"].tap()
        XCTAssertTrue(record("标签电脑").exists); XCTAssertFalse(record("标签阅读器").exists)
        XCTAssertTrue(app.staticTexts["搜索生效：电脑"].exists)
    }
    private func post(_ server: String, path: String, body: [String: Any], token: String? = nil) async throws -> [String: Any] {
        let base = try XCTUnwrap(URL(string: server))
        guard ["127.0.0.1", "localhost", "::1"].contains(base.host ?? "") else { throw XCTSkip("HTTP UI acceptance must use an isolated loopback server") }
        var request = URLRequest(url: base.appendingPathComponent(path)); request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let session = URLSession(configuration: .ephemeral); defer { session.invalidateAndCancel() }
        let (data, response) = try await session.data(for: request)
        let status = try XCTUnwrap(response as? HTTPURLResponse).statusCode
        XCTAssertTrue((200...299).contains(status), "HTTP \(status) at \(path)")
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
    func testExplicitServerAccountAndSyncEnrollment() async throws {
        guard let server = ProcessInfo.processInfo.environment["SUIRENX_IOS_TEST_SERVER"] else { throw XCTSkip("Requires isolated HTTP server") }
        launch()
        app.buttons["nav.assets"].tap(); app.buttons["asset.add"].tap()
        app.textFields["asset.name"].tap(); app.textFields["asset.name"].typeText("待同步资产")
        app.textFields["asset.price"].tap(); app.textFields["asset.price"].typeText("1")
        dismissKeyboard(); tapControl(app.buttons["asset.save"])
        XCTAssertTrue(record("待同步资产").waitForExistence(timeout: 5))
        app.buttons["nav.settings"].tap(); tapText("服务器地址"); app.buttons["添加服务器"].tap()
        app.textFields["名称（可选）"].tap(); app.textFields["名称（可选）"].typeText("验收服务器")
        app.textFields["域名或 IP 地址"].tap(); app.textFields["域名或 IP 地址"].typeText(server)
        app.buttons["测试连接"].tap()
        XCTAssertTrue(app.staticTexts["连接成功，尚未保存或开启同步。"].waitForExistence(timeout: 15))
        app.buttons["保存"].tap(); tapText("验收服务器"); app.buttons["选择此服务器"].tap()
        XCTAssertTrue(app.buttons["已选择此服务器"].waitForExistence(timeout: 5))
        goBack(); goBack()
        tapText("账号")
        tapControl(app.secureTextFields["account.password"]); app.typeText("password")
        app.buttons["显示密码"].firstMatch.tap()
        XCTAssertEqual(app.textFields["account.password"].value as? String, "password", "Single login field must preserve secure input")
        app.buttons["隐藏密码"].firstMatch.tap(); app.buttons["注册"].tap()
        let username = "ui_" + UUID().uuidString.replacingOccurrences(of: "-", with: "")
        app.textFields["account.username"].tap(); app.textFields["account.username"].typeText(username)
        tapControl(app.secureTextFields["account.password"])
        // A system strong-password offer can replace the software keyboard on either device family.
        if app.buttons["GenerateStrongPasswordButton"].waitForExistence(timeout: 3) { app.buttons["关闭"].firstMatch.tap() }
        // Send keyboard events to the focused input without SecureTextField's replacement helper.
        app.typeText("password")
        app.buttons["显示密码"].firstMatch.tap()
        XCTAssertEqual(app.textFields["account.password"].value as? String, "password")
        tapControl(app.textFields["account.confirmation"])
        if let existing = app.textFields["account.confirmation"].value as? String, existing != "确认密码", !existing.isEmpty {
            app.textFields["account.confirmation"].typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: existing.count))
        }
        app.textFields["account.confirmation"].typeText("password")
        app.buttons["隐藏密码"].firstMatch.tap()
        app.buttons["注册并登录"].tap(); XCTAssertTrue(app.staticTexts[username].waitForExistence(timeout: 15))
        goBack(); tapText("同步设置")
        XCTAssertTrue(app.buttons["预览并开启同步"].waitForExistence(timeout: 5)); shot("login-does-not-upload")
        app.buttons["预览并开启同步"].tap(); app.buttons["确认开启并同步"].tap()
        XCTAssertTrue(app.buttons["暂停同步"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["已同步"].waitForExistence(timeout: 15)); shot("explicit-sync-enabled")
        let login = try await post(server, path: "api/v1/auth/login", body: ["username": username, "password": "password"])
        let token = try XCTUnwrap(login["access_token"] as? String)
        let pulled = try await post(server, path: "api/v1/sync/assets", body: ["cursor": 0, "expiry_cursor": 0, "idempotency_key": UUID().uuidString, "changes": [], "expiry_changes": []], token: token)
        let changes = try XCTUnwrap(pulled["changes"] as? [[String: Any]])
        let first = try XCTUnwrap(changes.first)
        var remote = try XCTUnwrap(first["asset"] as? [String: Any])
        remote["base_version"] = try XCTUnwrap(first["version"]); remote["name"] = "另一端资产"; remote["deleted"] = false; remote["tags"] = ["远端", "验收"]
        app.buttons["暂停同步"].tap(); XCTAssertTrue(app.buttons["预览并开启同步"].waitForExistence(timeout: 5))
        app.buttons["nav.assets"].tap(); record("待同步资产").tap(); reveal(app.buttons["asset.edit"]); app.buttons["asset.edit"].tap()
        let nameField = app.textFields["asset.name"]; nameField.tap()
        let oldName = nameField.value as? String ?? ""
        nameField.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: oldName.count) + "本机冲突资产")
        app.textFields["asset.price"].tap(); dismissKeyboard(); tapControl(app.buttons["asset.save"])
        XCTAssertTrue(app.staticTexts["本机冲突资产"].waitForExistence(timeout: 5))
        _ = try await post(server, path: "api/v1/sync/assets", body: ["cursor": pulled["next_cursor"] ?? 0, "expiry_cursor": pulled["next_expiry_cursor"] ?? 0, "idempotency_key": UUID().uuidString, "changes": [remote], "expiry_changes": []], token: token)
        app.buttons["nav.settings"].tap(); tapText("同步设置")
        app.buttons["预览并开启同步"].tap(); app.buttons["确认开启并同步"].tap()
        let conflicts = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "冲突处理（1）")).firstMatch
        XCTAssertTrue(conflicts.waitForExistence(timeout: 15)); reveal(conflicts); conflicts.tap()
        XCTAssertTrue(app.staticTexts["本机：本机冲突资产"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["另一端：另一端资产"].exists); shot("conflict-real-http-two-versions")
        reveal(app.buttons["两份都保留"]); app.buttons["两份都保留"].tap(); tapControl(app.buttons["确认选择"].firstMatch)
        app.buttons["nav.assets"].tap()
        XCTAssertTrue(record("另一端资产").waitForExistence(timeout: 5)); XCTAssertTrue(record("本机冲突资产（副本）").waitForExistence(timeout: 5)); shot("conflict-kept-both-native")
        app.buttons["asset.tagFilter"].tap(); app.buttons["asset.tag.远端"].tap(); app.buttons["asset.tag.验收"].tap(); app.buttons["asset.tags.done"].tap()
        XCTAssertFalse(record("本机冲突资产（副本）").exists)
        app.buttons["搜索资产"].tap(); app.textFields["asset.search"].tap(); app.textFields["asset.search"].typeText("另一端")
        app.buttons["nav.settings"].tap(); app.buttons["nav.assets"].tap()
        XCTAssertEqual(app.textFields["asset.search"].value as? String, "另一端")
        XCTAssertTrue(record("另一端资产").exists); XCTAssertFalse(record("本机冲突资产（副本）").exists)
        app.buttons["收起搜索"].tap(); XCTAssertTrue(app.staticTexts["搜索生效：另一端"].exists)
        app.buttons["排序：购买日期，降序"].tap(); tapControl(app.buttons["购买金额"])
        XCTAssertTrue(app.buttons["排序：购买金额，降序"].waitForExistence(timeout: 5)); shot("asset-query-preserved-across-pages")
        app.buttons["搜索资产"].tap(); app.buttons["清除搜索"].tap(); app.buttons["收起搜索"].tap()
        app.buttons["asset.tagFilter"].tap(); app.buttons["asset.tags.clear"].tap(); app.buttons["asset.tags.done"].tap()
        XCTAssertTrue(record("本机冲突资产（副本）").waitForExistence(timeout: 5))
        app.buttons["nav.settings"].tap(); tapText("同步设置"); XCTAssertTrue(app.staticTexts["已同步"].waitForExistence(timeout: 15))
        goBack(); tapText("账号"); app.buttons["退出账号"].tap()
        XCTAssertTrue(app.buttons["account.confirmLogout"].firstMatch.waitForExistence(timeout: 5)); app.buttons["account.confirmLogout"].firstMatch.tap()
        XCTAssertTrue(app.textFields["account.username"].waitForExistence(timeout: 10))
        app.buttons["nav.assets"].tap(); XCTAssertTrue(record("另一端资产").waitForExistence(timeout: 5)); XCTAssertTrue(record("本机冲突资产（副本）").exists)
    }
}
