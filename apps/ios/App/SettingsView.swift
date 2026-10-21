import SwiftUI
import UniformTypeIdentifiers
import SuirenCore

enum SettingsDestination: Hashable {
    case account, sync, backup, appearance, language, expiryPreferences, servers, schedule, conflicts, about, license
    case server(String)
}
struct SettingsDestinationView: View {
    @EnvironmentObject private var model: AppModel
    let destination: SettingsDestination
    @ViewBuilder var body: some View {
        switch destination {
        case .account: AccountView()
        case .sync: SyncSettingsView()
        case .backup: BackupView()
        case .appearance: AppearanceView()
        case .language: LanguageView()
        case .expiryPreferences: ExpiryPreferencesView()
        case .servers: ServersView()
        case .schedule: SyncScheduleView()
        case .conflicts: ConflictsView()
        case .about: AboutView()
        case .server(let url):
            if let server = model.settings.servers.first(where: { $0.url == url }) { ServerEditor(server: server) }
            else { EmptyState(title: L10n.text("ui.address_removed")) }
        case .license:
            ScrollView {
                Text(Bundle.main.url(forResource: "FluentEmoji-MIT", withExtension: "txt").flatMap { try? String(contentsOf: $0) } ?? "MIT License")
                    .font(.caption).textSelection(.enabled).padding()
            }.navigationTitle(L10n.text("ui.open_source_licenses"))
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text(L10n.text("ui.your_data_stays_on_this_device_you_decide_whether_to"))
                    .font(.subheadline).foregroundStyle(.secondary).padding(.vertical, 12)
                VStack(spacing: 0) {
                    NavigationLink(value: SettingsDestination.account) { settingsRow(L10n.text("ui.account"), subtitle: model.credential?.username ?? L10n.text("ui.not_signed_in"), icon: "person.crop.circle") }.accessibilityIdentifier("settings.row.账号")
                    Divider()
                    NavigationLink(value: SettingsDestination.sync) { settingsRow(L10n.text("ui.sync_settings"), subtitle: model.syncLabel, icon: "arrow.triangle.2.circlepath") }.accessibilityIdentifier("settings.row.同步设置")
                }.sxCard(padding: 0)
                VStack(spacing: 0) {
                    NavigationLink(value: SettingsDestination.backup) { settingsRow(L10n.text("ui.backup_and_restore"), subtitle: L10n.text("ui.export_preview_and_safely_restore_local_data"), icon: "externaldrive") }.accessibilityIdentifier("settings.row.备份与恢复")
                    Divider()
                    NavigationLink(value: SettingsDestination.appearance) { settingsRow(L10n.text("ui.appearance"), subtitle: model.settings.theme == "system" ? L10n.text("ui.follow_system") : model.settings.theme == "dark" ? L10n.text("ui.dark") : L10n.text("ui.light"), icon: "circle.lefthalf.filled") }.accessibilityIdentifier("settings.row.外观")
                    Divider()
                    NavigationLink(value: SettingsDestination.language) { settingsRow(L10n.text("settings.language"), subtitle: LanguageView.currentLanguage, icon: "globe") }.accessibilityIdentifier("settings.row.language")
                    Divider()
                    NavigationLink(value: SettingsDestination.expiryPreferences) { settingsRow(L10n.text("ui.expiry_reminders"), subtitle: L10n.count("ui.mark_as_due_soon_days_in_advance", model.settings.soonDays), icon: "calendar.badge.clock") }.accessibilityIdentifier("settings.row.用品临期提醒")
                }.sxCard(padding: 0)
                VStack(spacing: 0) {
                    NavigationLink(value: SettingsDestination.servers) { settingsRow(L10n.text("ui.server_addresses"), subtitle: model.settings.activeURL ?? L10n.text("ui.none_selected_optional"), icon: "network") }.accessibilityIdentifier("settings.row.服务器地址")
                    Divider()
                    NavigationLink(value: SettingsDestination.about) { settingsRow(L10n.text("ui.about"), subtitle: "SuirenX \(InstalledAppInfo.version)", icon: "info.circle") }.accessibilityIdentifier("settings.row.关于")
                }.sxCard(padding: 0)
            }.sxPage()
        }.buttonStyle(.plain)
    }
    private func settingsRow(_ title: String, subtitle: String, icon: String) -> some View {
        HStack(spacing: 12) { Image(systemName: icon).frame(width: 26); VStack(alignment: .leading, spacing: 5) { Text(title).font(.subheadline.weight(.medium)); Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2) }; Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(.secondary) }.padding(.horizontal, 16).padding(.vertical, 12).frame(minHeight: 64)
            .contentShape(Rectangle())
    }
}

private struct AccountView: View {
    @EnvironmentObject private var model: AppModel
    @State private var registering = false
    @State private var username = ""
    @State private var password = ""
    @State private var confirmation = ""
    @State private var visible = false
    @State private var busy = false
    @State private var error: String?
    @State private var confirmLogout = false
    private var invalid: String? {
        do { try AccountValidation.validate(username: username, password: password, confirmation: registering ? confirmation : nil); return nil }
        catch { return L10n.error(error) }
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                PageHeading(title: L10n.text("ui.account"), subtitle: L10n.text("ui.accounts_and_server_addresses_are_independent_signing_in_does_not"))
                if let credential = model.credential {
                    VStack(spacing: 8) { InfoRow(label: L10n.text("ui.current_account"), value: credential.username); InfoRow(label: L10n.text("ui.issuing_server"), value: credential.serverURL); InfoRow(label: L10n.text("ui.credential_expiry"), value: DisplayFormatting.timestamp(credential.expiresAt)) }.sxCard()
                    Text(L10n.text("ui.sign_out_before_switching_accounts_changing_or_deleting_an_address")).font(.subheadline).foregroundStyle(.secondary)
                    Button(L10n.text("ui.sign_out"), role: .destructive) { confirmLogout = true }.frame(maxWidth: .infinity, minHeight: 48).disabled(busy)
                } else {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(model.settings.activeURL.map { L10n.text("ui.sign_in_server" , String(describing: $0)) } ?? L10n.text("ui.add_and_select_a_server_in_server_addresses_first")).font(.caption).foregroundStyle(.secondary)
                        Picker(L10n.text("ui.action"), selection: Binding(get: { registering }, set: { value in password = ""; confirmation = ""; error = nil; registering = value })) { Text(L10n.text("ui.sign_in")).tag(false); Text(L10n.text("ui.register")).tag(true) }.pickerStyle(.segmented)
                        TextField(L10n.text("ui.account"), text: $username).textInputAutocapitalization(.never).autocorrectionDisabled().textContentType(.username).padding(12).background(Palette.inset, in: RoundedRectangle(cornerRadius: 12)).accessibilityIdentifier("account.username")
                        passwordField(L10n.text("ui.password"), value: $password)
                        if registering { Text(L10n.text("account.password_requirements")).font(.caption).foregroundStyle(.secondary); passwordField(L10n.text("ui.confirm_password"), value: $confirmation) }
                        if let error { Text(error).font(.subheadline).foregroundStyle(.red) }
                        if let invalid { Text(invalid).font(.caption).foregroundStyle(.secondary) }
                        Button(busy ? L10n.text("ui.submitting") : registering ? L10n.text("ui.register_and_sign_in") : L10n.text("ui.sign_in")) { submit() }.buttonStyle(PrimaryButtonStyle()).disabled(busy || invalid != nil || model.settings.activeURL == nil)
                    }.sxCard().disabled(busy)
                }
            }.sxPage()
        }.navigationTitle(L10n.text("ui.account")).scrollDismissesKeyboard(.interactively)
            .confirmationDialog(L10n.text("ui.sign_out_of_this_account"), isPresented: $confirmLogout, titleVisibility: .visible) { Button(L10n.text("ui.sign_out"), role: .destructive) { busy = true; Task { await model.logout(); busy = false } }.accessibilityIdentifier("account.confirmLogout") } message: { Text(L10n.text("ui.local_data_is_kept_and_sync_stops_issued_jwts_are")) }
    }
    private func passwordField(_ title: String, value: Binding<String>) -> some View {
        HStack {
            PasswordInput(text: value, title: title, secure: !visible, registering: registering, identifier: title == L10n.text("ui.password") ? "account.password" : "account.confirmation").id("\(title)-\(registering)").frame(minHeight: 44)
            Button { visible.toggle() } label: { Image(systemName: visible ? "eye.slash" : "eye").frame(minWidth: 44, minHeight: 44) }.accessibilityLabel(visible ? L10n.text("ui.hide_password") : L10n.text("ui.show_password"))
        }.padding(.leading, 12).background(Palette.inset, in: RoundedRectangle(cornerRadius: 12))
    }
    private func submit() {
        busy = true; error = nil
        Task {
            do { try await model.login(username: username, password: password, confirmation: registering ? confirmation : nil); password = ""; confirmation = "" }
            catch { self.error = L10n.error(error) }; busy = false
        }
    }
}

private struct ServersView: View {
    @EnvironmentObject private var model: AppModel
    @State private var adding = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PageHeading(title: L10n.text("ui.server_addresses"), subtitle: L10n.text("ui.adding_or_editing_an_address_requires_a_successful_health_check"))
                ForEach(model.settings.servers) { server in
                    NavigationLink(value: SettingsDestination.server(server.url)) {
                        VStack(alignment: .leading, spacing: 7) { HStack { Text(server.name.isEmpty ? L10n.text("ui.unnamed_server") : server.name).fontWeight(.medium); Spacer(); if model.settings.activeURL == server.url { Text(L10n.text("ui.selected")).font(.caption).padding(5).background(Palette.accent, in: Capsule()).foregroundStyle(.black) } }; Text(server.url).font(.caption).foregroundStyle(.secondary).textSelection(.enabled) }.sxCard()
                    }.buttonStyle(.plain)
                }
                if model.settings.servers.isEmpty { EmptyState(title: L10n.text("ui.no_servers_yet"), detail: L10n.text("ui.offline_features_are_ready_to_use")) }
                Button(L10n.text("ui.add_server")) { adding = true }.buttonStyle(PrimaryButtonStyle())
            }.sxPage()
        }.navigationTitle(L10n.text("ui.server_addresses")).sheet(isPresented: $adding) { NavigationStack { ServerEditor(server: nil) } }
    }
}

private struct ServerEditor: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let server: ServerAddress?
    @State private var name: String
    @State private var address: String
    @State private var testedURL: String?
    @State private var message: String?
    @State private var busy = false
    @State private var deleteConfirmation = false
    init(server: ServerAddress?) { self.server = server; _name = State(initialValue: server?.name ?? ""); _address = State(initialValue: server?.url ?? "") }
    private func normalized() throws -> String {
        #if DEBUG
        return try ServerAddress.normalize(address, allowHTTP: true)
        #else
        return try ServerAddress.normalize(address)
        #endif
    }
    var body: some View {
        Form {
            Section(L10n.text("ui.address_details")) { TextField(L10n.text("ui.name_optional"), text: $name); TextField(L10n.text("ui.domain_or_ip_address"), text: $address).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled().onChange(of: address) { _, _ in testedURL = nil; message = nil } }
            Section {
                Button(busy ? L10n.text("ui.testing") : L10n.text("ui.test_connection")) { test() }.disabled(busy)
                if let message { Text(message).font(.caption).foregroundStyle(testedURL != nil ? .secondary : Color.red) }
                Text(L10n.text("ui.a_successful_test_only_confirms_that_the_health_endpoint_is")).font(.caption).foregroundStyle(.secondary)
            }
            Section { Button(L10n.text("ui.save")) { save() }.disabled(busy || !canSave) }
            if let server {
                Section {
                    Button(model.settings.activeURL == server.url ? L10n.text("ui.this_server_is_selected") : L10n.text("ui.select_this_server")) { Task { var s = model.settings; s.activeURL = server.url; await model.updateSettings(s) } }.disabled(busy || model.settings.activeURL == server.url)
                    Text(L10n.text("ui.selecting_an_address_pauses_sync_it_does_not_clear_the")).font(.caption).foregroundStyle(.secondary)
                }
                Section { Button(L10n.text("ui.delete_address"), role: .destructive) { deleteConfirmation = true }.disabled(busy) }
            }
        }.frame(maxWidth: 640).frame(maxWidth: .infinity).sxNavigationBackground().navigationTitle(server == nil ? L10n.text("ui.add_server") : L10n.text("ui.server_details"))
            .toolbar { if server == nil { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("ui.cancel")) { dismiss() }.disabled(busy) } } }
            .confirmationDialog(L10n.text("ui.delete_this_address"), isPresented: $deleteConfirmation, titleVisibility: .visible) { Button(L10n.text("ui.confirm_address_deletion"), role: .destructive) { remove() } } message: { Text(L10n.text("ui.only_the_connection_settings_are_deleted_account_local_data_and")) }
    }
    private var canSave: Bool { guard let url = try? normalized() else { return false }; return testedURL == url || (server?.url == url && server?.name != name) }
    private func test() {
        do {
            let url = try normalized(); testedURL = nil; busy = true
            Task {
                do { try await model.http.health(serverURL: url); if (try? normalized()) == url { testedURL = url; message = L10n.text("ui.connected_settings_have_not_been_saved_and_sync_is_still") } }
                catch { message = L10n.error(error) }; busy = false
            }
        } catch { message = L10n.error(error) }
    }
    private func save() {
        do {
            let url = try normalized(); guard canSave else { throw AppPresentationError("ui.test_the_exact_same_address_first") }
            var settings = model.settings
            guard !settings.servers.contains(where: { $0.url == url && $0.url != server?.url }) else { throw AppPresentationError("ui.this_address_already_exists_edit_the_existing_entry") }
            let saved = ServerAddress(url: url, name: name.trimmingCharacters(in: .whitespacesAndNewlines))
            if let server, let index = settings.servers.firstIndex(where: { $0.url == server.url }) {
                settings.servers[index] = saved; if settings.activeURL == server.url { settings.activeURL = url }
            } else { settings.servers.append(saved) }
            busy = true; Task { await model.updateSettings(settings); busy = false; if model.error == nil { dismiss() } }
        } catch { message = L10n.error(error) }
    }
    private func remove() {
        guard let server else { return }; var s = model.settings; s.servers.removeAll { $0.url == server.url }
        if s.activeURL == server.url { s.activeURL = nil; s.syncEnabled = false }
        Task { await model.updateSettings(s); if model.error == nil { dismiss() } }
    }
}

private struct SyncSettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var preview = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PageHeading(title: L10n.text("ui.sync_settings"), subtitle: L10n.text("ui.local_data_is_always_available_signing_in_or_selecting_an"))
                VStack(alignment: .leading, spacing: 12) {
                    Text(model.syncLabel).font(.headline)
                    if model.syncing { ProgressView() }
                    InfoRow(label: L10n.text("ui.last_successful_sync"), value: model.snapshot?.session?.lastSyncedAt.map { DisplayFormatting.timestamp($0) } ?? L10n.text("ui.never_synced"))
                    InfoRow(label: L10n.text("ui.pending_uploads"), value: L10n.count("ui.items", model.snapshot?.pendingCount ?? 0))
                    InfoRow(label: L10n.text("ui.unresolved_conflicts"), value: L10n.count("ui.items", model.snapshot?.conflicts.count ?? 0))
                    if model.settings.syncEnabled {
                        Button(model.syncing ? L10n.text("ui.stop_this_sync") : L10n.text("ui.sync_now")) { if model.syncing { model.cancelSync() } else { model.syncNow() } }.buttonStyle(PrimaryButtonStyle())
                        Button(L10n.text("ui.pause_sync")) { Task { var s = model.settings; s.syncEnabled = false; await model.updateSettings(s) } }.frame(maxWidth: .infinity, minHeight: 44)
                    } else { Button(L10n.text("ui.preview_and_enable_sync")) { preview = true }.buttonStyle(PrimaryButtonStyle()).disabled(model.credential == nil || model.settings.activeURL == nil) }
                }.sxCard()
                NavigationLink(value: SettingsDestination.schedule) { Label(L10n.text("ui.sync_method_and_frequency"), systemImage: "clock").frame(maxWidth: .infinity, alignment: .leading).sxCard() }.buttonStyle(.plain)
                NavigationLink(value: SettingsDestination.conflicts) { Label(L10n.text("ui.resolve_conflicts" , String(describing: model.snapshot?.conflicts.count ?? 0)), systemImage: "arrow.triangle.branch").frame(maxWidth: .infinity, alignment: .leading).sxCard() }.buttonStyle(.plain)
                Text(L10n.text("ui.ios_and_ipados_decide_when_background_tasks_run_the_frequency")).font(.caption).foregroundStyle(.secondary)
            }.sxPage()
        }.navigationTitle(L10n.text("ui.sync_settings")).sheet(isPresented: $preview) {
            NavigationStack {
                VStack(alignment: .leading, spacing: 18) {
                    PageHeading(title: L10n.text("ui.before_enabling_sync"), subtitle: L10n.text("ui.a_local_safety_backup_including_previous_sync_recovery_records_will"))
                    InfoRow(label: L10n.text("ui.assets"), value: L10n.count("ui.items_including_archived", model.assets.count)); InfoRow(label: L10n.text("ui.supplies"), value: L10n.count("ui.items_including_archived", model.expiry.count))
                    InfoRow(label: L10n.text("ui.server"), value: model.settings.activeURL ?? L10n.text("ui.none_selected")); InfoRow(label: L10n.text("ui.account"), value: model.credential?.username ?? L10n.text("ui.not_signed_in"))
                    Text(L10n.text("ui.records_with_the_same_id_and_different_content_require_explicit")).font(.subheadline).foregroundStyle(.secondary)
                    Button(L10n.text("ui.enable_and_sync")) { preview = false; Task { await model.enableSync() } }.buttonStyle(PrimaryButtonStyle())
                    Spacer()
                }.padding(24).frame(maxWidth: 600).frame(maxWidth: .infinity, maxHeight: .infinity).sxNavigationBackground().navigationTitle(L10n.text("ui.sync_preview")).toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("ui.cancel")) { preview = false } } }
            }
        }
    }
}

private struct SyncScheduleView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        Form {
            Section(L10n.text("ui.sync_method")) { Picker(L10n.text("ui.trigger"), selection: Binding(get: { model.settings.syncAfterChanges }, set: { v in Task { var s = model.settings; s.syncAfterChanges = v; await model.updateSettings(s) } })) { Text(L10n.text("ui.after_each_change")).tag(true); Text(L10n.text("ui.scheduled_sync")).tag(false) } }
            Section(L10n.text("ui.preferred_frequency")) { Picker(L10n.text("ui.about_every"), selection: Binding(get: { model.settings.intervalMinutes }, set: { v in Task { var s = model.settings; s.intervalMinutes = v; await model.updateSettings(s) } })) { Text(L10n.text("ui.15_minutes")).tag(15); Text(L10n.text("ui.1_hour")).tag(60); Text(L10n.text("ui.6_hours")).tag(360); Text(L10n.text("ui.daily")).tag(1440) } }
            Section { Text(L10n.text("ui.the_system_decides_when_background_tasks_run_exact_timing_is")).font(.caption).foregroundStyle(.secondary) }
        }.navigationTitle(L10n.text("ui.sync_method_and_frequency"))
    }
}

private struct ConflictsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var pending: JournalRecord?
    @State private var resolution: ConflictResolution = .local
    @State private var confirming = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                PageHeading(title: L10n.text("ui.resolve_conflicts_"), subtitle: L10n.text("ui.only_conflicting_records_pause_other_records_continue_syncing_the_current"))
                if model.snapshot?.conflicts.isEmpty ?? true { EmptyState(title: L10n.text("ui.no_conflicts")) }
                ForEach(model.snapshot?.conflicts ?? []) { row in
                    VStack(alignment: .leading, spacing: 12) {
                        Text(row.payload.name.isEmpty ? row.payload.id : row.payload.name).font(.headline)
                        Text(L10n.text("ui.local_base_v_remote_v" , String(describing: row.version), String(describing: row.remoteVersion ?? 0))).font(.caption).foregroundStyle(.secondary)
                        let localFields = fields(row.payload), remoteFields = row.remote.map(fields)
                        ForEach(localFields, id: \.0) { key, value in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(key).font(.caption2).foregroundStyle(.secondary)
                                Text(L10n.text("ui.local" , String(describing: value))).font(.caption)
                                Text(L10n.text("ui.remote" , String(describing: remoteFields?.first { $0.0 == key }?.1 ?? L10n.text("ui.snapshot_unavailable")))).font(.caption).foregroundStyle(value == remoteFields?.first { $0.0 == key }?.1 ? Color.secondary : Color.primary)
                            }.textSelection(.enabled)
                        }
                        Button(L10n.text("ui.keep_local")) { choose(row, .local) }.buttonStyle(PrimaryButtonStyle())
                        Button(L10n.text("ui.keep_remote")) { choose(row, .remote) }.frame(maxWidth: .infinity, minHeight: 44).disabled(row.remote == nil)
                        Button(L10n.text("ui.keep_both")) { choose(row, .both) }.frame(maxWidth: .infinity, minHeight: 44).disabled(row.remote == nil)
                    }.sxCard().disabled(model.syncing)
                }
            }.sxPage()
        }.navigationTitle(L10n.text("ui.resolve_conflicts_")).confirmationDialog(L10n.text("ui.resolve_this_record"), isPresented: $confirming, titleVisibility: .visible) {
            Button(L10n.text("ui.confirm_selection")) { if let row = pending { Task { await model.mutate { try await model.store.resolve(key: row.id, resolution: resolution) } } } }
        } message: { Text(resolution == .local ? L10n.text("ui.keep_local_content_and_resubmit_against_the_latest_remote_version") : resolution == .remote ? L10n.text("ui.replace_local_content_with_the_remote_version_if_the_remote") : L10n.text("ui.save_any_retained_edits_under_a_new_id_the_original")) }
    }
    private func choose(_ row: JournalRecord, _ value: ConflictResolution) { pending = row; resolution = value; confirming = true }
    private func fields(_ payload: RecordPayload) -> [(String, String)] {
        switch payload {
        case .asset(let a): return [(L10n.text("ui.name"), a.name), (L10n.text("ui.deletion_status"), a.deleted ? L10n.text("ui.deleted") : L10n.text("ui.kept")), (L10n.text("ui.amount"), Money.text(a.price_cents)), (L10n.text("ui.purchase_date"), a.purchase_date), (L10n.text("ui.status"), L10n.assetStatus(a.status)), (L10n.text("ui.retirement_date"), a.retired_date), (L10n.text("ui.archived_at"), a.archived_at), (L10n.text("ui.icon"), AssetIcon.find(a.icon_key).label), (L10n.text("ui.purchase_channel"), a.purchase_channel), (L10n.text("ui.warranty_ends"), a.warranty_end_date), (L10n.text("ui.tags"), a.tags.joined(separator: "、")), (L10n.text("ui.notes"), a.notes)]
        case .expiry(let e): return [(L10n.text("ui.name"), e.name), (L10n.text("ui.deletion_status"), e.deleted ? L10n.text("ui.deleted") : L10n.text("ui.kept")), (L10n.text("ui.category"), e.category), (L10n.text("ui.package_expiry"), e.package_expiry_date), (L10n.text("ui.opened_on"), e.opened_date), (L10n.text("ui.validity_in_days"), String(e.opened_validity_days)), (L10n.text("ui.location"), e.location), (L10n.text("ui.status"), L10n.expiryStatus(e.status)), (L10n.text("ui.archived_at"), e.archived_at), (L10n.text("ui.notes"), e.notes)]
        }
    }
}

private struct JSONDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data = Data()) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
private struct BackupView: View {
    @EnvironmentObject private var model: AppModel
    @State private var document = JSONDocument()
    @State private var exporting = false
    @State private var importing = false
    @State private var imported: Data?
    @State private var inspected: LocalBackup?
    @State private var confirming = false
    @State private var busy = false
    @State private var status: String?
    @State private var safety: [URL] = []
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PageHeading(title: L10n.text("ui.backup_and_restore"), subtitle: L10n.text("ui.includes_assets_supplies_archived_records_and_timestamps_excludes_passwords_and"))
                InfoRow(label: L10n.text("ui.local_assets"), value: L10n.count("ui.items", model.assets.count)); InfoRow(label: L10n.text("ui.local_supplies"), value: L10n.count("ui.items", model.expiry.count))
                Button(L10n.text("ui.export_local_json")) { Task { busy = true; if await model.perform({ document = JSONDocument(data: try await model.store.exportBackup()); exporting = true }) {} ; busy = false } }.buttonStyle(PrimaryButtonStyle()).disabled(busy)
                Button(L10n.text("ui.choose_backup_and_preview")) { importing = true }.frame(maxWidth: .infinity, minHeight: 48).disabled(busy || model.syncing)
                if let inspected {
                    VStack(alignment: .leading, spacing: 12) { Text(L10n.text("ui.restore_preview")).font(.headline); Text(L10n.text("backup.preview.counts", L10n.count("ui.items", inspected.assets.count), L10n.count("ui.items", inspected.expiryItems.count))); Text(L10n.text("ui.a_safety_backup_is_created_before_restoring_restoring_replaces_local")).font(.caption).foregroundStyle(.secondary); Button(L10n.text("ui.confirm_restore")) { confirming = true }.buttonStyle(PrimaryButtonStyle()).disabled(busy || model.syncing) }.sxCard()
                }
                if let status { Text(status).font(.subheadline).foregroundStyle(.secondary) }
                if !safety.isEmpty {
                    Text(L10n.text("ui.automatic_safety_backups")).font(.headline)
                    ForEach(safety, id: \.self) { url in
                        HStack { Text(url.lastPathComponent).font(.caption).lineLimit(2); Spacer(); ShareLink(item: url) { Image(systemName: "square.and.arrow.up").frame(width: 44, height: 44) }.accessibilityLabel(L10n.text("ui.export_this_safety_backup")) }.sxCard()
                    }
                    Text(L10n.text("ui.safety_backups_include_previous_target_sync_recovery_records_and_exclude")).font(.caption).foregroundStyle(.secondary)
                }
            }.sxPage()
        }.navigationTitle(L10n.text("ui.backup_and_restore")).task { safety = (try? await model.store.safetyBackups()) ?? [] }
            .fileExporter(isPresented: $exporting, document: document, contentType: .json, defaultFilename: "suirenx-\(LocalDay.today.rawValue)") { result in
                switch result { case .success: status = L10n.text("ui.backup_exported"); case .failure(let error): model.error = L10n.error(error) }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
                do {
                    let url = try result.get(); let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                    guard size <= 64 * 1024 * 1024 else { throw AppPresentationError("ui.backup_file_is_too_large") }
                    let data = try Data(contentsOf: url); inspected = try LocalBackup.inspect(data); imported = data; status = nil
                } catch { model.error = L10n.error(error) }
            }
            .confirmationDialog(L10n.text("ui.replace_local_data"), isPresented: $confirming, titleVisibility: .visible) {
                    Button(L10n.text("ui.back_up_and_restore"), role: .destructive) { guard let imported else { return }; busy = true; Task { model.cancelSync(); if await model.mutate({ try await model.store.restore(imported) }) { status = L10n.text("ui.restored_sync_is_paused"); inspected = nil; self.imported = nil; safety = (try? await model.store.safetyBackups()) ?? []; model.scheduleBackground() }; busy = false } }
            } message: { Text(L10n.text("ui.the_file_is_validated_and_current_data_is_backed_up")) }
    }
}

private struct AppearanceView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View { Form { Picker(L10n.text("ui.appearance"), selection: Binding(get: { model.settings.theme }, set: { value in Task { var s = model.settings; s.theme = value; await model.updateSettings(s) } })) { Text(L10n.text("ui.follow_system")).tag("system"); Text(L10n.text("ui.light")).tag("light"); Text(L10n.text("ui.dark")).tag("dark") }.pickerStyle(.inline) }.navigationTitle(L10n.text("ui.appearance")) }
}
private struct ExpiryPreferencesView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View { Form { Section { Stepper(L10n.count("ui.days_in_advance", model.settings.soonDays), value: Binding(get: { model.settings.soonDays }, set: { value in Task { var s = model.settings; s.soonDays = value; await model.updateSettings(s) } }), in: 1...30) } footer: { Text(L10n.text("ui.used_for_the_overview_pending_count_and_due_soon_labels")) } }.navigationTitle(L10n.text("ui.expiry_reminders")) }
}
private enum InstalledAppInfo {
    static let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? L10n.text("ui.unknown")
    static let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? L10n.text("ui.unknown")
    static let trace: PackageTrace? = {
        guard let url = Bundle.main.url(forResource: "suirenx-build", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(PackageTrace.self, from: data)
    }()
    struct PackageTrace: Decodable {
        let channel: String
        let commit: String
        let dirty: Bool
        let built_at: String
        var code: String { String(commit.prefix(7)) + (dirty ? "-dirty" : "") }
        var localTime: String {
            guard let date = ISO8601DateFormatter().date(from: built_at) else { return L10n.text("ui.unknown") }
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "yyyy-MM-dd HH:mm"
            return formatter.string(from: date)
        }
    }
}

private struct AboutView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PageHeading(title: L10n.text("ui.suiren_suirenx"), subtitle: L10n.text("ui.personal_toolbox_local_first"))
                InfoRow(label: L10n.text("ui.version"), value: InstalledAppInfo.version).accessibilityIdentifier("about.version")
                InfoRow(label: L10n.text("ui.build"), value: InstalledAppInfo.build).accessibilityIdentifier("about.build")
                #if DEBUG
                if let trace = InstalledAppInfo.trace, trace.channel == "development" {
                    InfoRow(label: L10n.text("ui.commit"), value: trace.code).accessibilityIdentifier("about.code")
                    InfoRow(label: L10n.text("ui.built_at"), value: trace.localTime).accessibilityIdentifier("about.builtAt")
                }
                #endif
                Text(L10n.text("ui.native_iphone_and_ipad_app_assets_and_supplies_are_stored")).font(.subheadline)
                NavigationLink(L10n.text("ui.fluent_emoji_licenses"), value: SettingsDestination.license)
            }.sxPage()
        }.navigationTitle(L10n.text("ui.about"))
    }
}

private struct LanguageView: View {
    @Environment(\.openURL) private var openURL
    static var currentLanguage: String {
        let language = Bundle.main.preferredLocalizations.first ?? "zh-Hans"
        return language.hasPrefix("en") ? "English" : "简体中文"
    }
    var body: some View {
        Form {
            Section {
                InfoRow(label: L10n.text("settings.language.current"), value: Self.currentLanguage)
                Button(L10n.text("settings.language.open")) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }.accessibilityIdentifier("language.openSettings")
            } footer: { Text(L10n.text("settings.language.help")) }
        }.navigationTitle(L10n.text("settings.language"))
    }
}
