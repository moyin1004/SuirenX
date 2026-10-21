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
                SummaryCard(assets: visible, totalCount: model.assets.count, compact: compact).onTapGesture { withAnimation { compact.toggle() } }.accessibilityAction(named: L10n.text("ui.expand_or_collapse_overview")) { compact.toggle() }
                if dynamicTypeSize.isAccessibilitySize {
                    VStack(alignment: .leading, spacing: 8) {
                        filterMenu
                        HStack { Spacer(minLength: 0); toolbar }
                    }
                } else {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) { filterMenu; Spacer(minLength: 0); toolbar }
                    VStack(alignment: .leading) { filterMenu; HStack { Spacer(); toolbar } }
                }
                }
                if showSearch {
                    HStack { Image(systemName: "magnifyingglass"); TextField(L10n.text("ui.search_names_or_tags"), text: $query.search).accessibilityIdentifier("asset.search"); if !query.search.isEmpty { Button { query.search = "" } label: { Image(systemName: "xmark.circle.fill") }.accessibilityLabel(L10n.text("ui.clear_search")) } }.padding(12).background(Palette.surface, in: RoundedRectangle(cornerRadius: 12))
                } else if !query.search.isEmpty { Text(L10n.text("ui.search_active" , String(describing: query.search))).font(.caption).foregroundStyle(.secondary) }
                if visible.isEmpty { EmptyState(title: L10n.text("ui.no_assets_yet"), detail: L10n.text("ui.add_your_first_asset_or_adjust_the_filters")) }
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
            Picker(L10n.text("ui.asset_status"), selection: $query.filter) { ForEach(AssetFilter.allCases, id: \.self) { Text($0.title).tag($0) } }
        } label: { HStack(spacing: 5) { Text(query.filter.title); Image(systemName: "chevron.down").font(dynamicTypeSize.isAccessibilitySize ? .system(size: 12) : .caption2) }.font(.subheadline.weight(.medium)).frame(minHeight: 44).padding(.horizontal, 12).background(Palette.surface, in: Capsule()) }.accessibilityIdentifier("asset.filter")
    }
    private var toolbar: some View {
        HStack(spacing: 8) {
            tagMenu
            Button { showSearch.toggle() } label: { Image(systemName: "magnifyingglass").font(dynamicTypeSize.isAccessibilitySize ? .system(size: 19) : .body).frame(width: 44, height: 44).background(Palette.surface, in: RoundedRectangle(cornerRadius: 12)) }.accessibilityLabel(showSearch ? L10n.text("ui.hide_search") : L10n.text("ui.search_assets"))
            Menu {
                Picker(L10n.text("ui.sort_by"), selection: $query.sort) { ForEach(AssetSort.allCases, id: \.self) { Text($0.title).tag($0) } }
                Picker(L10n.text("ui.direction"), selection: $query.descending) { Text(L10n.text("ui.ascending")).tag(false); Text(L10n.text("ui.descending")).tag(true) }
            } label: { Image(systemName: "arrow.up.arrow.down").font(dynamicTypeSize.isAccessibilitySize ? .system(size: 19) : .body).frame(width: 44, height: 44).background(Palette.surface, in: RoundedRectangle(cornerRadius: 12)) }.accessibilityLabel(L10n.text("ui.sort" , String(describing: query.sort.title), String(describing: query.descending ? L10n.text("ui.descending") : L10n.text("ui.ascending"))))
        }.buttonStyle(.plain)
    }
    private var tagMenu: some View {
        let tags = Set(model.assets.flatMap(\.tags)).union(query.tags).sorted()
        return Menu {
            Button { query.tags.removeAll() } label: {
                if query.tags.isEmpty { Label(L10n.text("ui.all_tags"), systemImage: "checkmark") }
                else { Text(L10n.text("ui.all_tags")) }
            }.menuActionDismissBehavior(.disabled).accessibilityIdentifier("asset.tags.clear")
            Divider()
            if tags.isEmpty { Text(L10n.text("ui.no_tags_yet")) }
            ForEach(tags, id: \.self) { tag in
                Button {
                    if query.tags.contains(tag) { query.tags.remove(tag) } else { query.tags.insert(tag) }
                } label: {
                    if query.tags.contains(tag) { Label(tag, systemImage: "checkmark") }
                    else { Text(tag) }
                }.menuActionDismissBehavior(.disabled).accessibilityIdentifier("asset.tag.\(tag)")
            }
            Divider()
            Button(L10n.text("ui.done")) {}.accessibilityIdentifier("asset.tags.done")
        } label: {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "line.3.horizontal.decrease").font(dynamicTypeSize.isAccessibilitySize ? .system(size: 19) : .body).frame(width: 44, height: 44)
                    .foregroundStyle(query.tags.isEmpty ? Color.primary : Color.black)
                    .background(query.tags.isEmpty ? Palette.surface : Palette.accent, in: RoundedRectangle(cornerRadius: 12))
                if !query.tags.isEmpty {
                    Text("\(query.tags.count)").font(.caption2.weight(.semibold)).padding(3)
                        .foregroundStyle(.white).background(Color.black, in: Capsule()).offset(x: 3, y: -3)
                }
            }
        }.accessibilityLabel(L10n.text("ui.filter_tags")).accessibilityValue(query.tags.isEmpty ? L10n.text("ui.all_tags") : L10n.count("ui.selected_tags", query.tags.count))
            .accessibilityIdentifier("asset.tagFilter")
    }
}

private struct AssetCard: View {
    @ScaledMetric(relativeTo: .subheadline) private var nameHeight = 24
    let asset: Asset
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) { AssetImage(key: asset.iconKey, size: 36); Spacer(minLength: 4)
                Text(asset.archivedAt != nil ? L10n.text("ui.archived") : asset.status == .active ? L10n.text("ui.active") : L10n.text("ui.retired")).font(.caption2).foregroundStyle(.secondary).fixedSize() }
            MarqueeName(text: asset.name).frame(height: nameHeight)
            Text(L10n.text("asset.card.cost_held", currency(Decimal(asset.priceCents)), L10n.count("ui.days", asset.heldDays())))
                .font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.5)
            Spacer(minLength: 0)
            VStack(alignment: .leading, spacing: 0) {
                Text(asset.salePriceCents.map { L10n.text("asset.card.sold", currency(Decimal($0))) } ?? "")
                    .font(.caption2).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.4).frame(height: 14)
                    .accessibilityHidden(asset.salePriceCents == nil)
                HStack(alignment: .firstTextBaseline, spacing: 0) {
                    Text(currency(Decimal(asset.dailyCostCents()))).font(.system(.subheadline, design: .monospaced))
                    Text(L10n.text("ui.day")).font(.caption2).foregroundStyle(.secondary)
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
                            Text(asset.archivedAt != nil ? L10n.text("ui.archived") : asset.status == .active ? L10n.text("ui.active") : L10n.text("ui.retired")).font(.caption).padding(8).background(Palette.accent.opacity(0.5), in: Capsule())
                            Text(currency(Decimal(asset.priceCents))).font(.system(.largeTitle, design: .monospaced)).lineLimit(1).minimumScaleFactor(0.4)
                        }.frame(maxWidth: .infinity).padding(.vertical, 12)
                        HStack { detailMetric(L10n.text("ui.days_held"), L10n.count("ui.days", asset.heldDays())); Spacer(); detailMetric(L10n.text("ui.daily_cost"), currency(Decimal(asset.dailyCostCents()))); Spacer(); detailMetric(L10n.text("ui.warranty_status"), asset.warrantyEndDate.map { $0 < .today ? L10n.text("ui.expired") : L10n.text("ui.under_warranty") } ?? L10n.text("ui.not_provided")) }.sxCard()
                        VStack(spacing: 8) {
                            InfoRow(label: L10n.text("ui.purchase_date"), value: DisplayFormatting.day(asset.purchaseDate))
                            if let date = asset.retiredDate { InfoRow(label: L10n.text("ui.retirement_date"), value: DisplayFormatting.day(date)) }
                            InfoRow(label: L10n.text("ui.purchase_channel"), value: asset.purchaseChannel ?? "")
                            InfoRow(label: L10n.text("ui.warranty_ends"), value: asset.warrantyEndDate.map { DisplayFormatting.day($0) } ?? "")
                            InfoRow(label: L10n.text("ui.tags"), value: asset.tags.joined(separator: "、"))
                            InfoRow(label: L10n.text("ui.created_at"), value: DisplayFormatting.timestamp(asset.createdAt))
                            InfoRow(label: L10n.text("ui.updated_at"), value: DisplayFormatting.timestamp(asset.updatedAt))
                            if let archived = asset.archivedAt { InfoRow(label: L10n.text("ui.archived_at"), value: DisplayFormatting.timestamp(archived)) }
                        }.sxCard()
                        VStack(alignment: .leading, spacing: 8) { Text(L10n.text("ui.notes")).font(.caption).foregroundStyle(.secondary); Text(asset.notes.isEmpty ? L10n.text("ui.not_provided") : asset.notes).font(.subheadline).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading) }.sxCard()
                        if asset.archivedAt != nil { SalePriceEditor(asset: asset).id(asset.id) }
                        if asset.archivedAt == nil {
                            Button(L10n.text("ui.edit_asset")) { editing = true }.buttonStyle(PrimaryButtonStyle()).accessibilityIdentifier("asset.edit")
                            Button(asset.status == .active ? L10n.text("ui.retire") : L10n.text("ui.reactivate")) {
                                if asset.status == .active {
                                    if asset.purchaseDate > .today { model.error = L10n.text("ui.the_purchase_date_is_in_the_future_edit_it_first") }
                                    else { retiring = true }
                                }
                                else { Task { var updated = asset; updated.status = .active; updated.retiredDate = nil; await model.mutate { try await model.store.saveAsset(updated) } } }
                            }.frame(minHeight: 44)
                        }
                        Button(asset.archivedAt == nil ? L10n.text("ui.archive_record") : L10n.text("ui.restore_record")) { Task { await model.mutate { try await model.store.archive(kind: "asset", id: id, archived: asset.archivedAt == nil) } } }.frame(minHeight: 44)
                        Button(L10n.text("ui.delete_asset"), role: .destructive) { deleting = true }.frame(minHeight: 44).accessibilityIdentifier("asset.delete")
                    }.sxPage()
                }.sheet(isPresented: $editing) { AssetEditor(asset: asset) }
                    .sheet(isPresented: $retiring) { RetirementSheet(asset: asset) }
            } else { EmptyState(title: L10n.text("ui.record_deleted")) }
        }.navigationTitle(L10n.text("ui.asset_details"))
            .confirmationDialog(L10n.text("ui.delete_this_asset_this_differs_from_archiving"), isPresented: $deleting, titleVisibility: .visible) {
                Button(L10n.text("ui.confirm_deletion"), role: .destructive) { Task { if await model.mutate({ try await model.store.delete(kind: "asset", id: id) }) { dismiss() } } }
            } message: { Text(L10n.text("ui.removes_the_local_record_and_syncs_its_deletion_when_sync")) }
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
            Text(L10n.text("ui.sale_price_optional")).font(.subheadline.weight(.semibold))
            TextField(L10n.text("ui.leave_blank_if_not_provided"), text: Binding(get: { input }, set: { input = $0; dirty = true })).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("asset.salePrice").disabled(saving).focused($priceFocused)
                .onChange(of: asset.salePriceCents) { _, value in if !dirty { input = value.map(Money.text) ?? "" } }
            Text(error ?? L10n.text("ui.zero_and_two_decimal_places_are_supported_save_a_blank"))
                .font(.caption).foregroundStyle(error == nil ? Color.secondary : Color.red)
            Button(saving ? L10n.text("ui.saving") : L10n.text("ui.save_sale_price")) {
                do {
                    let raw = input.trimmingCharacters(in: .whitespacesAndNewlines)
                    let cents = raw.isEmpty ? nil : try Money.parse(raw)
                    error = nil; saving = true; priceFocused = false
                    Task {
                        if await model.mutate({ try await model.store.updateAssetSalePrice(id: asset.id, cents: cents) }) { dirty = false }
                        saving = false
                    }
                } catch { self.error = L10n.error(error) }
            }.buttonStyle(PrimaryButtonStyle()).disabled(saving).accessibilityIdentifier("asset.saveSalePrice")
        }.sxCard()
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    if priceFocused { Spacer(); Button(L10n.text("ui.done")) { priceFocused = false }.accessibilityIdentifier("asset.salePrice.done") }
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
            Form { Section { DateField(label: L10n.text("ui.retirement_date"), value: $date, range: asset.purchaseDate.date...LocalDay.today.date.addingTimeInterval(86399)) } footer: { Text(L10n.text("ui.days_held_include_both_the_purchase_and_retirement_dates_canceling")) } }
                .sxNavigationBackground()
                .navigationTitle(L10n.text("ui.retire_asset")).toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(L10n.text("ui.cancel")) { dismiss() }.disabled(saving) }
                    ToolbarItem(placement: .confirmationAction) { Button(L10n.text("ui.confirm")) { saving = true; Task { var a = asset; a.status = .retired; a.retiredDate = date; if await model.mutate({ try await model.store.saveAsset(a) }) { dismiss() }; saving = false } }.disabled(saving) }
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
                Section(L10n.text("ui.asset_icon")) {
                    Button { showIcons = true } label: { HStack { AssetImage(key: asset.iconKey); Text(AssetIcon.find(asset.iconKey).label); Spacer(); Text(L10n.text("ui.change")).foregroundStyle(.secondary) } }.accessibilityIdentifier("asset.icon")
                }
                Section(L10n.text("ui.basic_details")) {
                    TextField(L10n.text("ui.name"), text: $asset.name).accessibilityIdentifier("asset.name")
                    TextField(L10n.text("ui.purchase_amount"), text: $price).keyboardType(.decimalPad).accessibilityIdentifier("asset.price")
                    TextField(L10n.text("ui.purchase_channel_optional"), text: Binding(get: { asset.purchaseChannel ?? "" }, set: { asset.purchaseChannel = $0.isEmpty ? nil : $0 }))
                }
                Section(L10n.text("ui.dates_and_warranty")) {
                    DateField(label: L10n.text("ui.purchase_date"), value: $asset.purchaseDate, range: Date.distantPast...LocalDay.today.date.addingTimeInterval(86399))
                    Toggle(L10n.text("ui.set_warranty_end_date"), isOn: $hasWarranty)
                    if hasWarranty { DateField(label: L10n.text("ui.warranty_end_date"), value: $warranty) }
                }
                Section(L10n.text("ui.tags_and_notes")) {
                    TextField(L10n.text("ui.tags_separated_by_commas"), text: $tags).accessibilityIdentifier("asset.tags")
                    TextField(L10n.text("ui.notes_optional"), text: $asset.notes, axis: .vertical).lineLimit(4...8)
                }
                if let validation { Section { Text(validation).foregroundStyle(.red).accessibilityAddTraits(.updatesFrequently) } }
            }.scrollDismissesKeyboard(.immediately).frame(maxWidth: 640).frame(maxWidth: .infinity).sxNavigationBackground()
                .navigationTitle(isNew ? L10n.text("ui.add_asset") : L10n.text("ui.edit_asset")).navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(L10n.text("ui.cancel")) { dismiss() }.disabled(saving) }
                    ToolbarItem(placement: .confirmationAction) { Button(saving ? L10n.text("ui.saving_") : L10n.text("ui.save")) { save() }.keyboardShortcut("s", modifiers: .command).disabled(saving).accessibilityIdentifier("asset.save") }
                }.sheet(isPresented: $showIcons) { IconPicker(selected: $asset.iconKey) }
        }.interactiveDismissDisabled(saving)
    }
    private func save() {
        do {
            asset.priceCents = try Money.parse(price); asset.warrantyEndDate = hasWarranty ? warranty : nil
            asset.tags = Array(NSOrderedSet(array: tags.components(separatedBy: CharacterSet(charactersIn: ",，")).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty })) as? [String] ?? []
            try asset.validate(); validation = nil; saving = true
            Task { if await model.mutate({ try await model.store.saveAsset(asset) }) { dismiss() }; saving = false }
        } catch { validation = L10n.error(error) }
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
                HStack { ForEach([("all", L10n.text("ui.all")), ("digital", L10n.text("ui.digital")), ("life", L10n.text("ui.home")), ("travel", L10n.text("ui.travel"))], id: \.0) { key, label in Chip(title: label, selected: key == category) { category = key } } }
                ScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 14) {
                        ForEach(AssetIcon.all.filter { category == "all" || $0.category == category }) { icon in
                            Button { draft = icon.key } label: {
                                VStack(spacing: 4) { Image(icon.image).resizable().scaledToFit().frame(width: 40, height: 40).frame(width: 54, height: 54).background(draft == icon.key ? Palette.accent : Palette.inset, in: RoundedRectangle(cornerRadius: 14)); Text(icon.label).font(.caption2).lineLimit(1).minimumScaleFactor(0.7) }
                            }.buttonStyle(.plain).accessibilityLabel(icon.label).accessibilityAddTraits(draft == icon.key ? .isSelected : [])
                        }
                    }.padding(14)
                }
            }.padding(.top, 12).frame(maxWidth: 600).frame(maxWidth: .infinity).sxNavigationBackground().navigationTitle(L10n.text("ui.choose_asset_icon")).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("ui.cancel")) { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button(L10n.text("ui.confirm")) { selected = draft; dismiss() } } }
        }
    }
}
