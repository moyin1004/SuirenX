import SwiftUI
import SuirenCore

struct AssetsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Binding var query: AssetQuery
    @Binding var showSearch: Bool
    @Binding var showEditor: Bool
    @Binding var compact: Bool
    private var visible: [Asset] { query.apply(model.assets) }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                SummaryCard(assets: visible, totalCount: model.assets.count, compact: compact).onTapGesture { withAnimation { compact.toggle() } }.accessibilityAction(named: "展开或压缩总览") { compact.toggle() }
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) { filterMenu; Spacer(minLength: 0); toolbar }
                    VStack(alignment: .leading) { filterMenu; HStack { Spacer(); toolbar } }
                }
                if showSearch {
                    HStack { Image(systemName: "magnifyingglass"); TextField("搜索名称或标签", text: $query.search).accessibilityIdentifier("asset.search"); if !query.search.isEmpty { Button { query.search = "" } label: { Image(systemName: "xmark.circle.fill") }.accessibilityLabel("清除搜索") } }.padding(12).background(Palette.surface, in: RoundedRectangle(cornerRadius: 12))
                } else if !query.search.isEmpty { Text("搜索生效：\(query.search)").font(.caption).foregroundStyle(.secondary) }
                if visible.isEmpty { EmptyState(title: "暂无资产", detail: "添加第一件资产，或调整筛选条件。") }
                LazyVGrid(columns: dynamicTypeSize.isAccessibilitySize ? [GridItem(.flexible())] : [GridItem(.adaptive(minimum: 154), spacing: 10)], spacing: 10) {
                    ForEach(visible) { asset in
                        NavigationLink(value: RecordDestination.asset(asset.id)) { AssetCard(asset: asset).contentShape(Rectangle()) }.buttonStyle(.plain).accessibilityIdentifier("asset.\(asset.id)")
                    }
                }
            }.sxPage()
        }
        .sheet(isPresented: $showEditor) { AssetEditor() }
        .refreshable { await model.refresh() }
    }
    private var filterMenu: some View {
        Menu {
            Picker("资产状态", selection: $query.filter) { ForEach(AssetFilter.allCases, id: \.self) { Text($0.rawValue).tag($0) } }
        } label: { HStack(spacing: 5) { Text(query.filter.rawValue); Image(systemName: "chevron.down").font(.caption2) }.font(.subheadline.weight(.medium)).frame(minHeight: 44).padding(.horizontal, 12).background(Palette.surface, in: Capsule()) }.accessibilityIdentifier("asset.filter")
    }
    private var toolbar: some View {
        HStack(spacing: 8) {
            tagMenu
            Button { showSearch.toggle() } label: { Image(systemName: "magnifyingglass").frame(width: 44, height: 44).background(Palette.surface, in: RoundedRectangle(cornerRadius: 12)) }.accessibilityLabel(showSearch ? "收起搜索" : "搜索资产")
            Menu {
                Picker("排序字段", selection: $query.sort) { ForEach(AssetSort.allCases, id: \.self) { Text($0.rawValue).tag($0) } }
                Picker("方向", selection: $query.descending) { Text("升序").tag(false); Text("降序").tag(true) }
            } label: { Image(systemName: "arrow.up.arrow.down").frame(width: 44, height: 44).background(Palette.surface, in: RoundedRectangle(cornerRadius: 12)) }.accessibilityLabel("排序：\(query.sort.rawValue)，\(query.descending ? "降序" : "升序")")
        }.buttonStyle(.plain)
    }
    private var tagMenu: some View {
        let tags = Set(model.assets.flatMap(\.tags)).union(query.tags).sorted()
        return Menu {
            Button { query.tags.removeAll() } label: {
                if query.tags.isEmpty { Label("全部标签", systemImage: "checkmark") }
                else { Text("全部标签") }
            }.menuActionDismissBehavior(.disabled).accessibilityIdentifier("asset.tags.clear")
            Divider()
            if tags.isEmpty { Text("暂无标签") }
            ForEach(tags, id: \.self) { tag in
                Button {
                    if query.tags.contains(tag) { query.tags.remove(tag) } else { query.tags.insert(tag) }
                } label: {
                    if query.tags.contains(tag) { Label(tag, systemImage: "checkmark") }
                    else { Text(tag) }
                }.menuActionDismissBehavior(.disabled).accessibilityIdentifier("asset.tag.\(tag)")
            }
            Divider()
            Button("完成") {}.accessibilityIdentifier("asset.tags.done")
        } label: {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "line.3.horizontal.decrease").frame(width: 44, height: 44)
                    .foregroundStyle(query.tags.isEmpty ? Color.primary : Color.black)
                    .background(query.tags.isEmpty ? Palette.surface : Palette.accent, in: RoundedRectangle(cornerRadius: 12))
                if !query.tags.isEmpty {
                    Text("\(query.tags.count)").font(.caption2.weight(.semibold)).padding(3)
                        .foregroundStyle(.white).background(Color.black, in: Capsule()).offset(x: 3, y: -3)
                }
            }
        }.accessibilityLabel("标签筛选").accessibilityValue(query.tags.isEmpty ? "全部标签" : "已选 \(query.tags.count) 个标签")
            .accessibilityIdentifier("asset.tagFilter")
    }
}

private struct AssetCard: View {
    @ScaledMetric(relativeTo: .subheadline) private var nameHeight = 24
    let asset: Asset
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) { AssetImage(key: asset.iconKey, size: 36); Spacer(minLength: 4)
                Text(asset.archivedAt != nil ? "已归档" : asset.status == .active ? "服役中" : "已退役").font(.caption2).foregroundStyle(.secondary).fixedSize() }
            MarqueeName(text: asset.name).frame(height: nameHeight)
            Text("¥\(Money.text(asset.priceCents)) · 持有 \(asset.heldDays()) 天")
                .font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.5)
            Spacer(minLength: 0)
            VStack(alignment: .leading, spacing: 0) {
                Text(asset.salePriceCents.map { "卖出 ¥\(Money.text($0))" } ?? "")
                    .font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.4).frame(height: 14)
                    .accessibilityHidden(asset.salePriceCents == nil)
                HStack(alignment: .firstTextBaseline, spacing: 0) {
                    Text("¥\(Money.text(asset.dailyCostCents()))").font(.system(.subheadline, design: .monospaced))
                    Text("/天").font(.caption2).foregroundStyle(.secondary)
                }.lineLimit(1).minimumScaleFactor(0.5)
            }
        }.frame(maxWidth: .infinity, minHeight: 170, alignment: .leading).sxCard(padding: 14)
    }
}

/// Only overflowed names move. The full name remains available to VoiceOver and in details.
private struct MarqueeName: View {
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    let text: String
    @State private var textWidth: CGFloat = 0
    @State private var moving = false
    var body: some View {
        GeometryReader { geometry in
            let overflow = max(0, textWidth - geometry.size.width)
            Text(text).font(.subheadline.weight(.semibold)).fixedSize()
                .background(GeometryReader { size in Color.clear.onAppear { textWidth = size.size.width }.onChange(of: size.size.width) { _, value in textWidth = value } })
                .offset(x: moving && !reducedMotion ? -overflow : 0)
                .animation(overflow > 0 && !reducedMotion ? .linear(duration: max(3, Double(overflow / 24))).delay(1).repeatForever(autoreverses: true) : nil, value: moving)
                .onAppear { moving = true }
        }.clipped().accessibilityElement(children: .ignore).accessibilityLabel(text)
    }
}

struct AssetDetailView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let id: String
    @State private var editing = false
    @State private var retiring = false
    @State private var deleting = false
    private var asset: Asset? { model.assets.first { $0.id == id } }
    var body: some View {
        Group {
            if let asset {
                ScrollView {
                    VStack(spacing: 18) {
                        VStack(spacing: 12) { AssetImage(key: asset.iconKey, size: 66); Text(asset.name).font(.title.weight(.semibold)).multilineTextAlignment(.center).textSelection(.enabled)
                            Text(asset.archivedAt != nil ? "已归档" : asset.status == .active ? "服役中" : "已退役").font(.caption).padding(8).background(Palette.accent.opacity(0.5), in: Capsule())
                            Text("¥\(Money.text(asset.priceCents))").font(.system(.largeTitle, design: .monospaced)).lineLimit(1).minimumScaleFactor(0.4)
                        }.frame(maxWidth: .infinity).padding(.vertical, 12)
                        HStack { detailMetric("持有天数", "\(asset.heldDays()) 天"); Spacer(); detailMetric("日均成本", "¥\(Money.text(asset.dailyCostCents()))"); Spacer(); detailMetric("保修状态", asset.warrantyEndDate.map { $0 < .today ? "已过期" : "保修中" } ?? "未填写") }.sxCard()
                        VStack(spacing: 8) {
                            InfoRow(label: "购买日期", value: asset.purchaseDate.rawValue)
                            if let date = asset.retiredDate { InfoRow(label: "退役日期", value: date.rawValue) }
                            InfoRow(label: "购买渠道", value: asset.purchaseChannel ?? "")
                            InfoRow(label: "保修截止", value: asset.warrantyEndDate?.rawValue ?? "")
                            InfoRow(label: "标签", value: asset.tags.joined(separator: "、"))
                            InfoRow(label: "创建时间", value: asset.createdAt)
                            InfoRow(label: "更新时间", value: asset.updatedAt)
                            if let archived = asset.archivedAt { InfoRow(label: "归档时间", value: archived) }
                        }.sxCard()
                        VStack(alignment: .leading, spacing: 8) { Text("备注").font(.caption).foregroundStyle(.secondary); Text(asset.notes.isEmpty ? "未填写" : asset.notes).font(.subheadline).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading) }.sxCard()
                        if asset.archivedAt != nil { SalePriceEditor(asset: asset).id(asset.id) }
                        if asset.archivedAt == nil {
                            Button("编辑资产") { editing = true }.buttonStyle(PrimaryButtonStyle()).accessibilityIdentifier("asset.edit")
                            Button(asset.status == .active ? "退役" : "重新启用") {
                                if asset.status == .active {
                                    if asset.purchaseDate > .today { model.error = "购买日期在未来，请先编辑购买日期" }
                                    else { retiring = true }
                                }
                                else { Task { var updated = asset; updated.status = .active; updated.retiredDate = nil; await model.mutate { try await model.store.saveAsset(updated) } } }
                            }.frame(minHeight: 44)
                        }
                        Button(asset.archivedAt == nil ? "归档记录" : "恢复记录") { Task { await model.mutate { try await model.store.archive(kind: "asset", id: id, archived: asset.archivedAt == nil) } } }.frame(minHeight: 44)
                        Button("删除资产", role: .destructive) { deleting = true }.frame(minHeight: 44).accessibilityIdentifier("asset.delete")
                    }.sxPage()
                }.sheet(isPresented: $editing) { AssetEditor(asset: asset) }
                    .sheet(isPresented: $retiring) { RetirementSheet(asset: asset) }
            } else { EmptyState(title: "记录已删除") }
        }.navigationTitle("资产详情")
            .confirmationDialog("删除资产？此操作与归档不同。", isPresented: $deleting, titleVisibility: .visible) {
                Button("确认删除", role: .destructive) { Task { if await model.mutate({ try await model.store.delete(kind: "asset", id: id) }) { dismiss() } } }
            } message: { Text("将移除本机记录，并在开启同步后同步删除。若需可恢复，请使用归档。") }
    }
    private func detailMetric(_ label: String, _ value: String) -> some View { VStack(spacing: 6) { Text(value).font(.subheadline.weight(.semibold)).minimumScaleFactor(0.5).lineLimit(1); Text(label).font(.caption2).foregroundStyle(.secondary) } }
}

private struct SalePriceEditor: View {
    @EnvironmentObject private var model: AppModel
    let asset: Asset
    @State private var input: String
    @State private var saving = false
    @State private var error: String?
    @State private var dirty = false
    @FocusState private var priceFocused: Bool
    init(asset: Asset) { self.asset = asset; _input = State(initialValue: asset.salePriceCents.map(Money.text) ?? "") }
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("卖出价格（可选）").font(.subheadline.weight(.semibold))
            TextField("留空表示未填写", text: Binding(get: { input }, set: { input = $0; dirty = true })).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("asset.salePrice").disabled(saving).focused($priceFocused)
                .onChange(of: asset.salePriceCents) { _, value in if !dirty { input = value.map(Money.text) ?? "" } }
            Text(error ?? "支持 0 和两位小数；留空保存可清除。其它资料请恢复后编辑。")
                .font(.caption).foregroundStyle(error == nil ? Color.secondary : Color.red)
            Button(saving ? "保存中…" : "保存卖出价格") {
                do {
                    let raw = input.trimmingCharacters(in: .whitespacesAndNewlines)
                    let cents = raw.isEmpty ? nil : try Money.parse(raw)
                    error = nil; saving = true; priceFocused = false
                    Task {
                        if await model.mutate({ try await model.store.updateAssetSalePrice(id: asset.id, cents: cents) }) { dirty = false }
                        saving = false
                    }
                } catch { self.error = error.localizedDescription }
            }.buttonStyle(PrimaryButtonStyle()).disabled(saving).accessibilityIdentifier("asset.saveSalePrice")
        }.sxCard()
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    if priceFocused { Spacer(); Button("完成") { priceFocused = false }.accessibilityIdentifier("asset.salePrice.done") }
                }
            }
    }
}

private struct RetirementSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let asset: Asset
    @State private var date = LocalDay.today
    @State private var saving = false
    var body: some View {
        NavigationStack {
            Form { Section { DateField(label: "退役日期", value: $date, range: asset.purchaseDate.date...LocalDay.today.date.addingTimeInterval(86399)) } footer: { Text("持有天数包含购买日和退役日。取消不会修改记录。") } }
                .sxNavigationBackground()
                .navigationTitle("退役资产").toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() }.disabled(saving) }
                    ToolbarItem(placement: .confirmationAction) { Button("确定") { saving = true; Task { var a = asset; a.status = .retired; a.retiredDate = date; if await model.mutate({ try await model.store.saveAsset(a) }) { dismiss() }; saving = false } }.disabled(saving) }
                }
        }.presentationDetents([.medium, .large]).interactiveDismissDisabled(saving)
    }
}

struct AssetEditor: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var asset: Asset
    @State private var price: String
    @State private var tags: String
    @State private var hasWarranty: Bool
    @State private var warranty: LocalDay
    @State private var showIcons = false
    @State private var saving = false
    @State private var validation: String?
    let isNew: Bool
    init(asset: Asset? = nil) {
        let a = asset ?? Asset(name: "", priceCents: 0, purchaseDate: .today)
        _asset = State(initialValue: a); _price = State(initialValue: asset == nil ? "" : Money.text(a.priceCents))
        _tags = State(initialValue: a.tags.joined(separator: ", ")); _hasWarranty = State(initialValue: a.warrantyEndDate != nil)
        _warranty = State(initialValue: a.warrantyEndDate ?? .today); isNew = asset == nil
    }
    var body: some View {
        NavigationStack {
            Form {
                Section("资产图标") {
                    Button { showIcons = true } label: { HStack { AssetImage(key: asset.iconKey); Text(AssetIcon.find(asset.iconKey).label); Spacer(); Text("更换").foregroundStyle(.secondary) } }.accessibilityIdentifier("asset.icon")
                }
                Section("基本信息") {
                    TextField("名称", text: $asset.name).accessibilityIdentifier("asset.name")
                    TextField("购入金额", text: $price).keyboardType(.decimalPad).accessibilityIdentifier("asset.price")
                    TextField("购买渠道（可选）", text: Binding(get: { asset.purchaseChannel ?? "" }, set: { asset.purchaseChannel = $0.isEmpty ? nil : $0 }))
                }
                Section("日期与保修") {
                    DateField(label: "购买日期", value: $asset.purchaseDate, range: Date.distantPast...LocalDay.today.date.addingTimeInterval(86399))
                    Toggle("填写保修截止日", isOn: $hasWarranty)
                    if hasWarranty { DateField(label: "保修截止日", value: $warranty) }
                }
                Section("标签与备注") {
                    TextField("标签，以逗号分隔", text: $tags).accessibilityIdentifier("asset.tags")
                    TextField("备注（可选）", text: $asset.notes, axis: .vertical).lineLimit(4...8)
                }
                if let validation { Section { Text(validation).foregroundStyle(.red).accessibilityAddTraits(.updatesFrequently) } }
            }.scrollDismissesKeyboard(.immediately).frame(maxWidth: 640).frame(maxWidth: .infinity).sxNavigationBackground()
                .navigationTitle(isNew ? "新增资产" : "编辑资产").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() }.disabled(saving) }
                    ToolbarItem(placement: .confirmationAction) { Button(saving ? "保存中" : "保存") { save() }.keyboardShortcut("s", modifiers: .command).disabled(saving).accessibilityIdentifier("asset.save") }
                }.sheet(isPresented: $showIcons) { IconPicker(selected: $asset.iconKey) }
        }.interactiveDismissDisabled(saving)
    }
    private func save() {
        do {
            asset.priceCents = try Money.parse(price); asset.warrantyEndDate = hasWarranty ? warranty : nil
            asset.tags = Array(NSOrderedSet(array: tags.components(separatedBy: CharacterSet(charactersIn: ",，")).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })) as? [String] ?? []
            try asset.validate(); validation = nil; saving = true
            Task { if await model.mutate({ try await model.store.saveAsset(asset) }) { dismiss() }; saving = false }
        } catch { validation = error.localizedDescription }
    }
}

private struct IconPicker: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selected: String
    @State private var draft: String
    @State private var category = "all"
    init(selected: Binding<String>) { _selected = selected; _draft = State(initialValue: selected.wrappedValue) }
    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                HStack { ForEach([("all", "全部"), ("digital", "数码"), ("life", "生活"), ("travel", "出行")], id: \.0) { key, label in Chip(title: label, selected: key == category) { category = key } } }
                ScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 14) {
                        ForEach(AssetIcon.all.filter { category == "all" || $0.category == category }) { icon in
                            Button { draft = icon.key } label: {
                                VStack(spacing: 4) { Image(icon.image).resizable().scaledToFit().frame(width: 40, height: 40).frame(width: 54, height: 54).background(draft == icon.key ? Palette.accent : Palette.inset, in: RoundedRectangle(cornerRadius: 14)); Text(icon.label).font(.caption2).lineLimit(1).minimumScaleFactor(0.7) }
                            }.buttonStyle(.plain).accessibilityLabel(icon.label).accessibilityAddTraits(draft == icon.key ? .isSelected : [])
                        }
                    }.padding(14)
                }
            }.padding(.top, 12).frame(maxWidth: 600).frame(maxWidth: .infinity).sxNavigationBackground().navigationTitle("选择资产图标").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("确定") { selected = draft; dismiss() } } }
        }
    }
}
