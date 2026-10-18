import SwiftUI
import UniformTypeIdentifiers
import SuirenCore

enum SettingsDestination: Hashable {
    case account, sync, backup, appearance, expiryPreferences, servers, schedule, conflicts, about, license
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
        case .expiryPreferences: ExpiryPreferencesView()
        case .servers: ServersView()
        case .schedule: SyncScheduleView()
        case .conflicts: ConflictsView()
        case .about: AboutView()
        case .server(let url):
            if let server = model.settings.servers.first(where: { $0.url == url }) { ServerEditor(server: server) }
            else { EmptyState(title: "地址已删除") }
        case .license:
            ScrollView {
                Text(Bundle.main.url(forResource: "FluentEmoji-MIT", withExtension: "txt").flatMap { try? String(contentsOf: $0) } ?? "MIT License")
                    .font(.caption).textSelection(.enabled).padding()
            }.navigationTitle("开源许可")
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("数据始终保存在本机，同步由你决定。")
                    .font(.subheadline).foregroundStyle(.secondary).padding(.vertical, 12)
                VStack(spacing: 0) {
                    NavigationLink(value: SettingsDestination.account) { settingsRow("账号", subtitle: model.credential?.username ?? "未登录", icon: "person.crop.circle") }.accessibilityIdentifier("settings.row.账号")
                    Divider()
                    NavigationLink(value: SettingsDestination.sync) { settingsRow("同步设置", subtitle: model.syncLabel, icon: "arrow.triangle.2.circlepath") }.accessibilityIdentifier("settings.row.同步设置")
                }.sxCard(padding: 0)
                VStack(spacing: 0) {
                    NavigationLink(value: SettingsDestination.backup) { settingsRow("备份与恢复", subtitle: "导出、预览和安全恢复本机数据", icon: "externaldrive") }.accessibilityIdentifier("settings.row.备份与恢复")
                    Divider()
                    NavigationLink(value: SettingsDestination.appearance) { settingsRow("外观", subtitle: model.settings.theme == "system" ? "跟随系统" : model.settings.theme == "dark" ? "深色" : "浅色", icon: "circle.lefthalf.filled") }.accessibilityIdentifier("settings.row.外观")
                    Divider()
                    NavigationLink(value: SettingsDestination.expiryPreferences) { settingsRow("用品临期提醒", subtitle: "提前 \(model.settings.soonDays) 天标记临期", icon: "calendar.badge.clock") }.accessibilityIdentifier("settings.row.用品临期提醒")
                }.sxCard(padding: 0)
                VStack(spacing: 0) {
                    NavigationLink(value: SettingsDestination.servers) { settingsRow("服务器地址", subtitle: model.settings.activeURL ?? "未选择 · 可选", icon: "network") }.accessibilityIdentifier("settings.row.服务器地址")
                    Divider()
                    NavigationLink(value: SettingsDestination.about) { settingsRow("关于", subtitle: "SuirenX \(InstalledAppInfo.version)", icon: "info.circle") }.accessibilityIdentifier("settings.row.关于")
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
        catch { return error.localizedDescription }
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                PageHeading(title: "账号", subtitle: "账号与服务器地址独立管理。登录不会开始上传。")
                if let credential = model.credential {
                    VStack(spacing: 8) { InfoRow(label: "当前账号", value: credential.username); InfoRow(label: "签发服务器", value: credential.serverURL); InfoRow(label: "凭据到期", value: credential.expiresAt) }.sxCard()
                    Text("切换账号前请先退出。更改或删除地址不会清除账号。退出只清除此设备凭据，已签发令牌仍可在其 30 天有效期内使用。").font(.subheadline).foregroundStyle(.secondary)
                    Button("退出账号", role: .destructive) { confirmLogout = true }.frame(maxWidth: .infinity, minHeight: 48).disabled(busy)
                } else {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(model.settings.activeURL.map { "登录服务器：\($0)" } ?? "请先在服务器地址设置中添加并选择服务器。").font(.caption).foregroundStyle(.secondary)
                        Picker("操作", selection: Binding(get: { registering }, set: { value in password = ""; confirmation = ""; error = nil; registering = value })) { Text("登录").tag(false); Text("注册").tag(true) }.pickerStyle(.segmented)
                        TextField("账号", text: $username).textInputAutocapitalization(.never).autocorrectionDisabled().textContentType(.username).padding(12).background(Palette.inset, in: RoundedRectangle(cornerRadius: 12)).accessibilityIdentifier("account.username")
                        passwordField("密码", value: $password)
                        if registering { Text(AccountValidation.passwordHint).font(.caption).foregroundStyle(.secondary); passwordField("确认密码", value: $confirmation) }
                        if let error { Text(error).font(.subheadline).foregroundStyle(.red) }
                        if let invalid { Text(invalid).font(.caption).foregroundStyle(.secondary) }
                        Button(busy ? "正在提交…" : registering ? "注册并登录" : "登录") { submit() }.buttonStyle(PrimaryButtonStyle()).disabled(busy || invalid != nil || model.settings.activeURL == nil)
                    }.sxCard().disabled(busy)
                }
            }.sxPage()
        }.navigationTitle("账号").scrollDismissesKeyboard(.interactively)
            .confirmationDialog("退出当前账号？", isPresented: $confirmLogout, titleVisibility: .visible) { Button("退出账号", role: .destructive) { busy = true; Task { await model.logout(); busy = false } }.accessibilityIdentifier("account.confirmLogout") } message: { Text("本机数据保留，同步停止。此操作不撤销已签发 JWT。") }
    }
    private func passwordField(_ title: String, value: Binding<String>) -> some View {
        HStack {
            PasswordInput(text: value, title: title, secure: !visible, registering: registering, identifier: title == "密码" ? "account.password" : "account.confirmation").id("\(title)-\(registering)").frame(minHeight: 44)
            Button { visible.toggle() } label: { Image(systemName: visible ? "eye.slash" : "eye").frame(minWidth: 44, minHeight: 44) }.accessibilityLabel(visible ? "隐藏密码" : "显示密码")
        }.padding(.leading, 12).background(Palette.inset, in: RoundedRectangle(cornerRadius: 12))
    }
    private func submit() {
        busy = true; error = nil
        Task {
            do { try await model.login(username: username, password: password, confirmation: registering ? confirmation : nil); password = ""; confirmation = "" }
            catch { self.error = error.localizedDescription }; busy = false
        }
    }
}

private struct ServersView: View {
    @EnvironmentObject private var model: AppModel
    @State private var adding = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PageHeading(title: "服务器地址", subtitle: "添加或修改地址前需通过该地址的健康测试。测试不登录，也不上传。")
                ForEach(model.settings.servers) { server in
                    NavigationLink(value: SettingsDestination.server(server.url)) {
                        VStack(alignment: .leading, spacing: 7) { HStack { Text(server.name.isEmpty ? "未命名服务器" : server.name).fontWeight(.medium); Spacer(); if model.settings.activeURL == server.url { Text("当前选择").font(.caption).padding(5).background(Palette.accent, in: Capsule()).foregroundStyle(.black) } }; Text(server.url).font(.caption).foregroundStyle(.secondary).textSelection(.enabled) }.sxCard()
                    }.buttonStyle(.plain)
                }
                if model.settings.servers.isEmpty { EmptyState(title: "尚无服务器", detail: "离线功能可直接使用。") }
                Button("添加服务器") { adding = true }.buttonStyle(PrimaryButtonStyle())
            }.sxPage()
        }.navigationTitle("服务器地址").sheet(isPresented: $adding) { NavigationStack { ServerEditor(server: nil) } }
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
            Section("地址信息") { TextField("名称（可选）", text: $name); TextField("域名或 IP 地址", text: $address).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled().onChange(of: address) { _, _ in testedURL = nil; message = nil } }
            Section {
                Button(busy ? "测试中…" : "测试连接") { test() }.disabled(busy)
                if let message { Text(message).font(.caption).foregroundStyle(testedURL != nil ? .secondary : Color.red) }
                Text("测试成功仅表示此健康接口可达，不代表账号或同步已验证。").font(.caption).foregroundStyle(.secondary)
            }
            Section { Button("保存") { save() }.disabled(busy || !canSave) }
            if let server {
                Section {
                    Button(model.settings.activeURL == server.url ? "已选择此服务器" : "选择此服务器") { Task { var s = model.settings; s.activeURL = server.url; await model.updateSettings(s) } }.disabled(busy || model.settings.activeURL == server.url)
                    Text("选择地址会暂停同步，不会清除账号或自动上传。").font(.caption).foregroundStyle(.secondary)
                }
                Section { Button("删除地址", role: .destructive) { deleteConfirmation = true }.disabled(busy) }
            }
        }.frame(maxWidth: 640).frame(maxWidth: .infinity).sxNavigationBackground().navigationTitle(server == nil ? "添加服务器" : "服务器详情")
            .toolbar { if server == nil { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() }.disabled(busy) } } }
            .confirmationDialog("删除此地址？", isPresented: $deleteConfirmation, titleVisibility: .visible) { Button("确认删除地址", role: .destructive) { remove() } } message: { Text("仅删除连接配置。账号、本机业务数据和同步记录保留；删除当前地址将暂停同步且不自动选择其他地址。") }
    }
    private var canSave: Bool { guard let url = try? normalized() else { return false }; return testedURL == url || (server?.url == url && server?.name != name) }
    private func test() {
        do {
            let url = try normalized(); testedURL = nil; busy = true
            Task {
                do { try await model.http.health(serverURL: url); if (try? normalized()) == url { testedURL = url; message = "连接成功，尚未保存或开启同步。" } }
                catch { message = error.localizedDescription }; busy = false
            }
        } catch { message = error.localizedDescription }
    }
    private func save() {
        do {
            let url = try normalized(); guard canSave else { throw SuirenError("请先测试完全相同的地址") }
            var settings = model.settings
            guard !settings.servers.contains(where: { $0.url == url && $0.url != server?.url }) else { throw SuirenError("地址已存在，请编辑现有条目") }
            let saved = ServerAddress(url: url, name: name.trimmingCharacters(in: .whitespacesAndNewlines))
            if let server, let index = settings.servers.firstIndex(where: { $0.url == server.url }) {
                settings.servers[index] = saved; if settings.activeURL == server.url { settings.activeURL = url }
            } else { settings.servers.append(saved) }
            busy = true; Task { await model.updateSettings(settings); busy = false; if model.error == nil { dismiss() } }
        } catch { message = error.localizedDescription }
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
                PageHeading(title: "同步设置", subtitle: "本机数据始终可用，登录或选择地址本身不会上传。")
                VStack(alignment: .leading, spacing: 12) {
                    Text(model.syncLabel).font(.headline)
                    if model.syncing { ProgressView() }
                    InfoRow(label: "上次成功", value: model.snapshot?.session?.lastSyncedAt ?? "尚未同步")
                    InfoRow(label: "待发送", value: "\(model.snapshot?.pendingCount ?? 0) 项")
                    InfoRow(label: "待处理冲突", value: "\(model.snapshot?.conflicts.count ?? 0) 项")
                    if model.settings.syncEnabled {
                        Button(model.syncing ? "停止本次同步" : "立即同步") { if model.syncing { model.cancelSync() } else { model.syncNow() } }.buttonStyle(PrimaryButtonStyle())
                        Button("暂停同步") { Task { var s = model.settings; s.syncEnabled = false; await model.updateSettings(s) } }.frame(maxWidth: .infinity, minHeight: 44)
                    } else { Button("预览并开启同步") { preview = true }.buttonStyle(PrimaryButtonStyle()).disabled(model.credential == nil || model.settings.activeURL == nil) }
                }.sxCard()
                NavigationLink(value: SettingsDestination.schedule) { Label("同步方式与频率", systemImage: "clock").frame(maxWidth: .infinity, alignment: .leading).sxCard() }.buttonStyle(.plain)
                NavigationLink(value: SettingsDestination.conflicts) { Label("冲突处理（\(model.snapshot?.conflicts.count ?? 0)）", systemImage: "arrow.triangle.branch").frame(maxWidth: .infinity, alignment: .leading).sxCard() }.buttonStyle(.plain)
                Text("iOS 与 iPadOS 由系统决定后台执行时机，频率为期望值；省电、网络和后台刷新设置可能延迟同步。可随时使用立即同步。").font(.caption).foregroundStyle(.secondary)
            }.sxPage()
        }.navigationTitle("同步设置").sheet(isPresented: $preview) {
            NavigationStack {
                VStack(alignment: .leading, spacing: 18) {
                    PageHeading(title: "开启同步前确认", subtitle: "将先自动创建含旧同步恢复记录的本机安全备份。")
                    InfoRow(label: "资产", value: "\(model.assets.count) 项（含归档）"); InfoRow(label: "用品", value: "\(model.expiry.count) 项（含归档）")
                    InfoRow(label: "服务器", value: model.settings.activeURL ?? "未选择"); InfoRow(label: "账号", value: model.credential?.username ?? "未登录")
                    Text("相同编号的数据差异将进入显式冲突处理。切换同步目标会重建目标专属版本与游标，本机记录和删除记录保留。").font(.subheadline).foregroundStyle(.secondary)
                    Button("确认开启并同步") { preview = false; Task { await model.enableSync() } }.buttonStyle(PrimaryButtonStyle())
                    Spacer()
                }.padding(24).frame(maxWidth: 600).frame(maxWidth: .infinity, maxHeight: .infinity).sxNavigationBackground().navigationTitle("同步预览").toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { preview = false } } }
            }
        }
    }
}

private struct SyncScheduleView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        Form {
            Section("同步方式") { Picker("触发方式", selection: Binding(get: { model.settings.syncAfterChanges }, set: { v in Task { var s = model.settings; s.syncAfterChanges = v; await model.updateSettings(s) } })) { Text("每次修改后同步").tag(true); Text("定时同步").tag(false) } }
            Section("期望频率") { Picker("约每", selection: Binding(get: { model.settings.intervalMinutes }, set: { v in Task { var s = model.settings; s.intervalMinutes = v; await model.updateSettings(s) } })) { Text("15 分钟").tag(15); Text("1 小时").tag(60); Text("6 小时").tag(360); Text("每天").tag(1440) } }
            Section { Text("系统决定后台运行时机，无法保证准点。修改后模式会短暂合并连续修改，并申请约 15 分钟后的后台补偿。").font(.caption).foregroundStyle(.secondary) }
        }.navigationTitle("同步方式与频率")
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
                PageHeading(title: "冲突处理", subtitle: "仅暂停发生冲突的记录；其他记录继续同步。双方修改时间不可从当前协议核实。")
                if model.snapshot?.conflicts.isEmpty ?? true { EmptyState(title: "暂无冲突") }
                ForEach(model.snapshot?.conflicts ?? []) { row in
                    VStack(alignment: .leading, spacing: 12) {
                        Text(row.payload.name.isEmpty ? row.payload.id : row.payload.name).font(.headline)
                        Text("本机基线 v\(row.version) · 另一端 v\(row.remoteVersion ?? 0)").font(.caption).foregroundStyle(.secondary)
                        let localFields = fields(row.payload), remoteFields = row.remote.map(fields)
                        ForEach(localFields, id: \.0) { key, value in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(key).font(.caption2).foregroundStyle(.secondary)
                                Text("本机：\(value)").font(.caption)
                                Text("另一端：\(remoteFields?.first { $0.0 == key }?.1 ?? "快照不可用")").font(.caption).foregroundStyle(value == remoteFields?.first { $0.0 == key }?.1 ? Color.secondary : Color.primary)
                            }.textSelection(.enabled)
                        }
                        Button("保留本机") { choose(row, .local) }.buttonStyle(PrimaryButtonStyle())
                        Button("保留另一端") { choose(row, .remote) }.frame(maxWidth: .infinity, minHeight: 44).disabled(row.remote == nil)
                        Button("两份都保留") { choose(row, .both) }.frame(maxWidth: .infinity, minHeight: 44).disabled(row.remote == nil)
                    }.sxCard().disabled(model.syncing)
                }
            }.sxPage()
        }.navigationTitle("冲突处理").confirmationDialog("确认处理此记录？", isPresented: $confirming, titleVisibility: .visible) {
            Button("确认选择") { if let row = pending { Task { await model.mutate { try await model.store.resolve(key: row.id, resolution: resolution) } } } }
        } message: { Text(resolution == .local ? "保留本机内容，基于另一端最新版本再次提交。" : resolution == .remote ? "将用另一端版本替换本机内容；若另一端已删除，本机也将删除。" : "将可保留的修改版另存为新编号，原记录按另一端版本或本机删除处理。") }
    }
    private func choose(_ row: JournalRecord, _ value: ConflictResolution) { pending = row; resolution = value; confirming = true }
    private func fields(_ payload: RecordPayload) -> [(String, String)] {
        switch payload {
        case .asset(let a): return [("名称", a.name), ("删除状态", a.deleted ? "已删除" : "保留"), ("金额", Money.text(a.price_cents)), ("购买日期", a.purchase_date), ("状态", a.status), ("退役日期", a.retired_date), ("归档时间", a.archived_at), ("图标", AssetIcon.find(a.icon_key).label), ("购买渠道", a.purchase_channel), ("保修截止", a.warranty_end_date), ("标签", a.tags.joined(separator: "、")), ("备注", a.notes)]
        case .expiry(let e): return [("名称", e.name), ("删除状态", e.deleted ? "已删除" : "保留"), ("分类", e.category), ("包装到期", e.package_expiry_date), ("开封日", e.opened_date), ("有效天数", String(e.opened_validity_days)), ("位置", e.location), ("状态", e.status), ("归档时间", e.archived_at), ("备注", e.notes)]
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
                PageHeading(title: "备份与恢复", subtitle: "包含资产、用品、归档和时间，不包含账号密码或访问令牌。兼容 Android 本地备份 v1。")
                InfoRow(label: "本机资产", value: "\(model.assets.count) 项"); InfoRow(label: "本机用品", value: "\(model.expiry.count) 项")
                Button("导出本机 JSON") { Task { busy = true; if await model.perform({ document = JSONDocument(data: try await model.store.exportBackup()); exporting = true }) {} ; busy = false } }.buttonStyle(PrimaryButtonStyle()).disabled(busy)
                Button("选择备份并预览") { importing = true }.frame(maxWidth: .infinity, minHeight: 48).disabled(busy || model.syncing)
                if let inspected {
                    VStack(alignment: .leading, spacing: 12) { Text("恢复预览").font(.headline); Text("资产 \(inspected.assets.count) 项 · 用品 \(inspected.expiryItems.count) 项"); Text("恢复前自动创建安全备份；恢复将替换本机业务记录并暂停同步。").font(.caption).foregroundStyle(.secondary); Button("确认恢复") { confirming = true }.buttonStyle(PrimaryButtonStyle()).disabled(busy || model.syncing) }.sxCard()
                }
                if let status { Text(status).font(.subheadline).foregroundStyle(.secondary) }
                if !safety.isEmpty {
                    Text("自动安全备份").font(.headline)
                    ForEach(safety, id: \.self) { url in
                        HStack { Text(url.lastPathComponent).font(.caption).lineLimit(2); Spacer(); ShareLink(item: url) { Image(systemName: "square.and.arrow.up").frame(width: 44, height: 44) }.accessibilityLabel("导出此安全备份") }.sxCard()
                    }
                    Text("安全备份包含旧目标的同步恢复记录，不包含凭据；恢复时只使用业务数据。").font(.caption).foregroundStyle(.secondary)
                }
            }.sxPage()
        }.navigationTitle("备份与恢复").task { safety = (try? await model.store.safetyBackups()) ?? [] }
            .fileExporter(isPresented: $exporting, document: document, contentType: .json, defaultFilename: "suirenx-\(LocalDay.today.rawValue)") { result in
                switch result { case .success: status = "备份已导出"; case .failure(let error): model.error = error.localizedDescription }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
                do {
                    let url = try result.get(); let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                    guard size <= 64 * 1024 * 1024 else { throw SuirenError("备份文件过大") }
                    let data = try Data(contentsOf: url); inspected = try LocalBackup.inspect(data); imported = data; status = nil
                } catch { model.error = error.localizedDescription }
            }
            .confirmationDialog("替换本机数据？", isPresented: $confirming, titleVisibility: .visible) {
                    Button("创建安全备份并恢复", role: .destructive) { guard let imported else { return }; busy = true; Task { model.cancelSync(); if await model.mutate({ try await model.store.restore(imported) }) { status = "恢复完成，同步已暂停"; inspected = nil; self.imported = nil; safety = (try? await model.store.safetyBackups()) ?? []; model.scheduleBackground() }; busy = false } }
            } message: { Text("会先校验文件并自动备份现有数据，事务失败保留原数据。凭据不会从备份导入。") }
    }
}

private struct AppearanceView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View { Form { Picker("外观", selection: Binding(get: { model.settings.theme }, set: { value in Task { var s = model.settings; s.theme = value; await model.updateSettings(s) } })) { Text("跟随系统").tag("system"); Text("浅色").tag("light"); Text("深色").tag("dark") }.pickerStyle(.inline) }.navigationTitle("外观") }
}
private struct ExpiryPreferencesView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View { Form { Section { Stepper("提前 \(model.settings.soonDays) 天", value: Binding(get: { model.settings.soonDays }, set: { value in Task { var s = model.settings; s.soonDays = value; await model.updateSettings(s) } }), in: 1...30) } footer: { Text("用于首页待处理数量与用品临期标记。当前与 Android 一致，尚未提供系统通知推送。") } }.navigationTitle("用品临期提醒") }
}
private enum InstalledAppInfo {
    static let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "未知"
    static let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "未知"
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
            guard let date = ISO8601DateFormatter().date(from: built_at) else { return "未知" }
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
                PageHeading(title: "燧人 · SuirenX", subtitle: "个人工具箱 · 本地优先")
                InfoRow(label: "版本号", value: InstalledAppInfo.version).accessibilityIdentifier("about.version")
                InfoRow(label: "Build 号", value: InstalledAppInfo.build).accessibilityIdentifier("about.build")
                #if DEBUG
                if let trace = InstalledAppInfo.trace, trace.channel == "development" {
                    InfoRow(label: "代码", value: trace.code).accessibilityIdentifier("about.code")
                    InfoRow(label: "打包时间", value: trace.localTime).accessibilityIdentifier("about.builtAt")
                }
                #endif
                Text("原生 iPhone / iPad 应用。资产和用品存于本机，仅在明确开启同步后发送到所选服务器。").font(.subheadline)
                NavigationLink("Fluent Emoji 开源许可", value: SettingsDestination.license)
            }.sxPage()
        }.navigationTitle("关于")
    }
}
