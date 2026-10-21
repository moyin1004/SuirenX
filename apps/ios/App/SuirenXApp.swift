import SwiftUI
import SuirenCore

@main struct SuirenXApp: App {
    @Environment(\.scenePhase) private var phase
    @State private var model: AppModel?
    @State private var startupError: String?
    var body: some Scene {
        WindowGroup {
            Group {
                if let model {
                    RootView().environmentObject(model)
                        .onChange(of: phase) { _, new in model.sceneChanged(new) }
                } else if let startupError {
                    ContentUnavailableView { Label(L10n.text("ui.local_data_cannot_be_opened_right_now"), systemImage: "externaldrive.badge.exclamationmark") }
                        description: { Text(startupError) } actions: { Button(L10n.text("ui.retry")) { Task { await bootstrap() } } }
                } else { ProgressView(L10n.text("ui.opening_local_data")).task { await bootstrap() } }
            }
            .tint(.primary)
        }
        .backgroundTask(.appRefresh("io.suirenx.app.sync")) {
            // A background launch may not create a WindowGroup view. Open the same persisted store here.
            if let current = try? await loadModel() { await current.runSync(); await current.scheduleBackground() }
        }
    }
    @MainActor private func bootstrap() async {
        do { let current = try await loadModel(); current.sceneChanged(.active) }
        catch { startupError = L10n.error(error) }
    }
    @MainActor private func loadModel() async throws -> AppModel {
        if let model { return model }
        var root = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("SuirenX", isDirectory: true)
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-ui-testing"), let run = ProcessInfo.processInfo.environment["SUIRENX_TEST_RUN"], UUID(uuidString: run) != nil {
            root = root.appendingPathComponent("UITests/\(run)", isDirectory: true)
        }
        #endif
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication, .posixPermissions: 0o700], ofItemAtPath: root.path)
        let store = try LocalStore(databaseURL: root.appendingPathComponent("local.sqlite"), backupsDirectory: root.appendingPathComponent("Backups"))
        let current = AppModel(store: store); self.model = current; await current.refresh(); return current
    }
}
