import SwiftUI
import SuirenCore

private enum ExpiryFilter: String, CaseIterable { case all = "全部", pending = "待处理", expired = "已过期", soon = "临期", normal = "正常", inactive = "已结束", archived = "已归档" }
func expiryStatus(_ item: ExpiryItem, soonDays: Int) -> String {
    if item.archivedAt != nil { return "已归档" }
    switch item.bucket(soonDays: soonDays) {
    case .expired: return "已过期 \(item.actualExpiryDate.distance(to: .today)) 天"
    case .today: return "今天到期"
    case .soon: return "\(LocalDay.today.distance(to: item.actualExpiryDate)) 天后到期"
    case .normal: return "正常 · \(LocalDay.today.distance(to: item.actualExpiryDate)) 天后到期"
    case .inactive: return item.status == .usedUp ? "已用完" : "已丢弃"
    }
}
struct ExpiryView: View {
    @EnvironmentObject private var model: AppModel
    @State private var filter: ExpiryFilter = .all
    @State private var search = ""
    @State private var category = ""
    @State private var adding = false
    private var visible: [ExpiryItem] {
        model.expiry.filter { item in
            let bucket = item.bucket(soonDays: model.settings.soonDays)
            let matches: Bool
            switch filter {
            case .archived: matches = item.archivedAt != nil
            case .all: matches = item.archivedAt == nil
            case .pending: matches = [.expired, .today, .soon].contains(bucket)
            case .expired: matches = bucket == .expired
            case .soon: matches = [.today, .soon].contains(bucket)
            case .normal: matches = bucket == .normal
            case .inactive: matches = item.archivedAt == nil && bucket == .inactive
            }
            return matches && (category.isEmpty || category == item.category) && (search.isEmpty || "\(item.name) \(item.category) \(item.location)".localizedCaseInsensitiveContains(search))
        }.sorted { a, b in
            let order: [ExpiryBucket] = [.expired, .today, .soon, .normal, .inactive]
            let left = order.firstIndex(of: a.bucket(soonDays: model.settings.soonDays))!, right = order.firstIndex(of: b.bucket(soonDays: model.settings.soonDays))!
            return left == right ? a.actualExpiryDate < b.actualExpiryDate : left < right
        }
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                PageHeading(title: "用品 / 保质期", subtitle: "包装日期与开封期限，取更早的日期。")
                VStack(spacing: 12) {
                    HStack { Text("\(model.pendingExpiry)").font(.largeTitle.monospacedDigit()); Text("项待处理").foregroundStyle(.secondary); Spacer(); Text("临期 \(model.settings.soonDays) 天").font(.caption).foregroundStyle(.secondary) }
                    Divider()
                    HStack {
                        expiryCount("已过期", buckets: [.expired]); Spacer(); expiryCount("临期", buckets: [.today, .soon]); Spacer(); expiryCount("正常", buckets: [.normal])
                    }
                }.sxCard()
                ScrollView(.horizontal, showsIndicators: false) { HStack { ForEach(ExpiryFilter.allCases, id: \.self) { f in Chip(title: f.rawValue, selected: filter == f) { filter = f } } } }
                HStack {
                    Image(systemName: "magnifyingglass")
                    TextField("搜索用品、分类或位置", text: $search).accessibilityIdentifier("expiry.search")
                    Menu { Button("全部分类") { category = "" }; ForEach(Array(Set(model.expiry.map(\.category))).sorted(), id: \.self) { c in Button(c) { category = c } } } label: { Label(category.isEmpty ? "分类" : category, systemImage: "line.3.horizontal.decrease") }.font(.caption).frame(minHeight: 44)
                }.padding(.horizontal, 12).background(Palette.surface, in: RoundedRectangle(cornerRadius: 12))
                if visible.isEmpty { EmptyState(title: "暂无用品", detail: "添加用品，或调整筛选条件。") }
                ForEach(visible) { item in
                    NavigationLink(value: RecordDestination.expiry(item.id)) {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "shippingbox").padding(12).background(Palette.inset, in: RoundedRectangle(cornerRadius: 12))
                            VStack(alignment: .leading, spacing: 7) { Text(item.name).font(.subheadline.weight(.semibold)); Text([item.category, item.location].filter { !$0.isEmpty }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary); Text("实际到期 \(item.actualExpiryDate.rawValue)").font(.caption).monospacedDigit() }
                            Spacer(minLength: 4); Text(expiryStatus(item, soonDays: model.settings.soonDays)).font(.caption2).foregroundStyle(item.bucket(soonDays: model.settings.soonDays) == .expired ? Color.red : .secondary)
                        }.sxCard().contentShape(Rectangle())
                    }.buttonStyle(.plain)
                }
            }.sxPage()
        }.navigationTitle("用品期限").toolbar { ToolbarItem(placement: .topBarTrailing) { Button { adding = true } label: { Image(systemName: "plus") }.accessibilityLabel("新增用品").accessibilityIdentifier("expiry.add") } }
            .sheet(isPresented: $adding) { ExpiryEditor() }.refreshable { await model.refresh() }
    }
    private func expiryCount(_ title: String, buckets: [ExpiryBucket]) -> some View {
        VStack(spacing: 5) { Text("\(model.expiry.filter { buckets.contains($0.bucket(soonDays: model.settings.soonDays)) }.count)").font(.headline.monospacedDigit()); Text(title).font(.caption).foregroundStyle(.secondary) }
    }
}

struct ExpiryDetailView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let id: String
    @State private var editing = false
    @State private var deleting = false
    private var item: ExpiryItem? { model.expiry.first { $0.id == id } }
    var body: some View {
        Group {
            if let item {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        PageHeading(title: item.name, subtitle: expiryStatus(item, soonDays: model.settings.soonDays))
                        VStack(spacing: 8) {
                            InfoRow(label: "分类", value: item.category)
                            InfoRow(label: "包装到期日", value: item.packageExpiryDate.rawValue)
                            InfoRow(label: "开封日期", value: item.openedDate?.rawValue ?? "")
                            InfoRow(label: "开封后有效天数", value: item.openedValidityDays.map(String.init) ?? "")
                            InfoRow(label: "实际到期日", value: item.actualExpiryDate.rawValue)
                            InfoRow(label: "位置", value: item.location)
                            InfoRow(label: "备注", value: item.notes)
                            InfoRow(label: "创建时间", value: item.createdAt)
                            InfoRow(label: "更新时间", value: item.updatedAt)
                        }.sxCard()
                        if item.archivedAt == nil {
                            Button("编辑用品") { editing = true }.buttonStyle(PrimaryButtonStyle())
                            ForEach(ExpiryStatus.allCases, id: \.self) { status in
                                if item.status != status { Button(status == .inUse ? "重新使用" : status == .usedUp ? "标为已用完" : "标为已丢弃") { Task { var e = item; e.status = status; await model.mutate { try await model.store.saveExpiry(e) } } }.frame(maxWidth: .infinity, minHeight: 44) }
                            }
                        } else { Text("已归档，恢复后可编辑。").font(.subheadline).foregroundStyle(.secondary) }
                        Button(item.archivedAt == nil ? "归档记录" : "恢复记录") { Task { await model.mutate { try await model.store.archive(kind: "expiry", id: id, archived: item.archivedAt == nil) } } }.frame(maxWidth: .infinity, minHeight: 44)
                        Button("删除用品", role: .destructive) { deleting = true }.frame(maxWidth: .infinity, minHeight: 44)
                    }.sxPage()
                }.sheet(isPresented: $editing) { ExpiryEditor(item: item) }
            } else { EmptyState(title: "记录已删除") }
        }.navigationTitle("用品详情")
            .confirmationDialog("删除用品？", isPresented: $deleting, titleVisibility: .visible) { Button("确认删除", role: .destructive) { Task { if await model.mutate({ try await model.store.delete(kind: "expiry", id: id) }) { dismiss() } } } } message: { Text("将移除本机记录，并在开启同步后同步删除。若需可恢复，请使用归档。") }
    }
}

private struct ExpiryEditor: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var item: ExpiryItem
    @State private var opened: Bool
    @State private var openedDate: LocalDay
    @State private var days: String
    @State private var saving = false
    @State private var validation: String?
    let isNew: Bool
    init(item: ExpiryItem? = nil) {
        _item = State(initialValue: item ?? ExpiryItem(name: "", category: "", packageExpiryDate: .today))
        _opened = State(initialValue: item?.openedDate != nil); _openedDate = State(initialValue: item?.openedDate ?? .today)
        _days = State(initialValue: item?.openedValidityDays.map(String.init) ?? ""); isNew = item == nil
    }
    var body: some View {
        NavigationStack {
            Form {
                Section("基本信息") { TextField("名称", text: $item.name).accessibilityIdentifier("expiry.name"); TextField("分类", text: $item.category).accessibilityIdentifier("expiry.category"); TextField("位置（可选）", text: $item.location) }
                Section("期限") {
                    DateField(label: "包装到期日", value: $item.packageExpiryDate)
                    Toggle("已开封", isOn: $opened)
                    if opened { DateField(label: "开封日期", value: $openedDate, range: Date.distantPast...LocalDay.today.date.addingTimeInterval(86399)); TextField("开封后有效天数", text: $days).keyboardType(.numberPad) }
                    Text("实际到期日为包装日期与开封有效期的较早日期，开封日计为第 1 天。").font(.caption).foregroundStyle(.secondary)
                }
                Section("备注") { TextField("备注（可选）", text: $item.notes, axis: .vertical).lineLimit(4...8) }
                if let validation { Text(validation).foregroundStyle(.red) }
            }.scrollDismissesKeyboard(.immediately).frame(maxWidth: 640).frame(maxWidth: .infinity).sxNavigationBackground()
                .navigationTitle(isNew ? "新增用品" : "编辑用品").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() }.disabled(saving) }; ToolbarItem(placement: .confirmationAction) { Button(saving ? "保存中" : "保存") { save() }.keyboardShortcut("s", modifiers: .command).disabled(saving).accessibilityIdentifier("expiry.save") } }
        }.interactiveDismissDisabled(saving)
    }
    private func save() {
        do {
            item.openedDate = opened ? openedDate : nil; item.openedValidityDays = opened ? Int(days) : nil
            if opened && (item.openedValidityDays == nil || openedDate > .today || openedDate > item.packageExpiryDate) { throw SuirenError("请检查有效天数；开封日不能晚于今天或包装到期日") }
            try item.validate(); saving = true; validation = nil
            Task { if await model.mutate({ try await model.store.saveExpiry(item) }) { dismiss() }; saving = false }
        } catch { validation = error.localizedDescription }
    }
}
