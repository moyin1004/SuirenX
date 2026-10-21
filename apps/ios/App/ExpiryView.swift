import SwiftUI
import SuirenCore

private enum ExpiryFilter: String, CaseIterable { case all, pending, expired, soon, normal, inactive, archived
    var title: String { switch self { case .all: return L10n.text("ui.all"); case .pending: return L10n.text("ui.pending"); case .expired: return L10n.text("ui.expired"); case .soon: return L10n.text("ui.due_soon"); case .normal: return L10n.text("ui.normal"); case .inactive: return L10n.text("ui.finished"); case .archived: return L10n.text("ui.archived") } }
}
func expiryStatus(_ item: ExpiryItem, soonDays: Int) -> String {
    if item.archivedAt != nil { return L10n.text("ui.archived") }
    switch item.bucket(soonDays: soonDays) {
    case .expired: return L10n.count("ui.expired_days_ago", item.actualExpiryDate.distance(to: .today))
    case .today: return L10n.text("ui.expires_today")
    case .soon: return L10n.count("ui.expires_in_days", LocalDay.today.distance(to: item.actualExpiryDate))
    case .normal: return L10n.count("ui.normal_expires_in_days", LocalDay.today.distance(to: item.actualExpiryDate))
    case .inactive: return item.status == .usedUp ? L10n.text("ui.used_up") : L10n.text("ui.discarded")
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
                PageHeading(title: L10n.text("ui.supplies_expiry"), subtitle: L10n.text("ui.the_earlier_of_the_package_expiry_and_the_opened_validity"))
                VStack(spacing: 12) {
                    HStack { Text(L10n.count("count.pending", model.pendingExpiry)).font(.title2.monospacedDigit()); Spacer(); Text(L10n.count("ui.due_soon_within_days", model.settings.soonDays)).font(.caption).foregroundStyle(.secondary) }
                    Divider()
                    HStack {
                        expiryCount(L10n.text("ui.expired"), buckets: [.expired]); Spacer(); expiryCount(L10n.text("ui.due_soon"), buckets: [.today, .soon]); Spacer(); expiryCount(L10n.text("ui.normal"), buckets: [.normal])
                    }
                }.sxCard()
                ScrollView(.horizontal, showsIndicators: false) { HStack { ForEach(ExpiryFilter.allCases, id: \.self) { f in Chip(title: f.title, selected: filter == f) { filter = f } } } }
                HStack {
                    Image(systemName: "magnifyingglass")
                    TextField(L10n.text("ui.search_supplies_categories_or_locations"), text: $search).accessibilityIdentifier("expiry.search")
                    Menu { Button(L10n.text("ui.all_categories")) { category = "" }; ForEach(Array(Set(model.expiry.map(\.category))).sorted(), id: \.self) { c in Button(c) { category = c } } } label: { Label(category.isEmpty ? L10n.text("ui.category") : category, systemImage: "line.3.horizontal.decrease") }.font(.caption).frame(minHeight: 44)
                }.padding(.horizontal, 12).background(Palette.surface, in: RoundedRectangle(cornerRadius: 12))
                if visible.isEmpty { EmptyState(title: L10n.text("ui.no_supplies_yet"), detail: L10n.text("ui.add_supplies_or_adjust_the_filters")) }
                ForEach(visible) { item in
                    NavigationLink(value: RecordDestination.expiry(item.id)) {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "shippingbox").padding(12).background(Palette.inset, in: RoundedRectangle(cornerRadius: 12))
                            VStack(alignment: .leading, spacing: 7) { Text(item.name).font(.subheadline.weight(.semibold)); Text([item.category, item.location].filter { !$0.isEmpty }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary); Text(L10n.text("ui.actual_expiry" , String(describing: DisplayFormatting.day(item.actualExpiryDate)))).font(.caption).monospacedDigit() }
                            Spacer(minLength: 4); Text(expiryStatus(item, soonDays: model.settings.soonDays)).font(.caption2).foregroundStyle(item.bucket(soonDays: model.settings.soonDays) == .expired ? Color.red : .secondary)
                        }.sxCard().contentShape(Rectangle())
                    }.buttonStyle(.plain)
                }
            }.sxPage()
        }.navigationTitle(L10n.text("ui.supply_expiry")).toolbar { ToolbarItem(placement: .topBarTrailing) { Button { adding = true } label: { Image(systemName: "plus") }.accessibilityLabel(L10n.text("ui.add_supply")).accessibilityIdentifier("expiry.add") } }
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
                            InfoRow(label: L10n.text("ui.category"), value: item.category)
                            InfoRow(label: L10n.text("ui.package_expiry_date"), value: DisplayFormatting.day(item.packageExpiryDate))
                            InfoRow(label: L10n.text("ui.opened_date"), value: item.openedDate.map { DisplayFormatting.day($0) } ?? "")
                            InfoRow(label: L10n.text("ui.days_valid_after_opening"), value: item.openedValidityDays.map(String.init) ?? "")
                            InfoRow(label: L10n.text("ui.actual_expiry_date"), value: DisplayFormatting.day(item.actualExpiryDate))
                            InfoRow(label: L10n.text("ui.location"), value: item.location)
                            InfoRow(label: L10n.text("ui.notes"), value: item.notes)
                            InfoRow(label: L10n.text("ui.created_at"), value: DisplayFormatting.timestamp(item.createdAt))
                            InfoRow(label: L10n.text("ui.updated_at"), value: DisplayFormatting.timestamp(item.updatedAt))
                        }.sxCard()
                        if item.archivedAt == nil {
                            Button(L10n.text("ui.edit_supply")) { editing = true }.buttonStyle(PrimaryButtonStyle())
                            ForEach(ExpiryStatus.allCases, id: \.self) { status in
                                if item.status != status { Button(status == .inUse ? L10n.text("ui.use_again") : status == .usedUp ? L10n.text("ui.mark_as_used_up") : L10n.text("ui.mark_as_discarded")) { Task { var e = item; e.status = status; await model.mutate { try await model.store.saveExpiry(e) } } }.frame(maxWidth: .infinity, minHeight: 44) }
                            }
                        } else { Text(L10n.text("ui.archived_restore_before_editing")).font(.subheadline).foregroundStyle(.secondary) }
                        Button(item.archivedAt == nil ? L10n.text("ui.archive_record") : L10n.text("ui.restore_record")) { Task { await model.mutate { try await model.store.archive(kind: "expiry", id: id, archived: item.archivedAt == nil) } } }.frame(maxWidth: .infinity, minHeight: 44)
                        Button(L10n.text("ui.delete_supply"), role: .destructive) { deleting = true }.frame(maxWidth: .infinity, minHeight: 44)
                    }.sxPage()
                }.sheet(isPresented: $editing) { ExpiryEditor(item: item) }
            } else { EmptyState(title: L10n.text("ui.record_deleted")) }
        }.navigationTitle(L10n.text("ui.supply_details"))
            .confirmationDialog(L10n.text("ui.delete_this_supply"), isPresented: $deleting, titleVisibility: .visible) { Button(L10n.text("ui.confirm_deletion"), role: .destructive) { Task { if await model.mutate({ try await model.store.delete(kind: "expiry", id: id) }) { dismiss() } } } } message: { Text(L10n.text("ui.removes_the_local_record_and_syncs_its_deletion_when_sync")) }
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
                Section(L10n.text("ui.basic_details")) { TextField(L10n.text("ui.name"), text: $item.name).accessibilityIdentifier("expiry.name"); TextField(L10n.text("ui.category"), text: $item.category).accessibilityIdentifier("expiry.category"); TextField(L10n.text("ui.location_optional"), text: $item.location) }
                Section(L10n.text("ui.expiry")) {
                    DateField(label: L10n.text("ui.package_expiry_date"), value: $item.packageExpiryDate)
                    Toggle(L10n.text("ui.opened"), isOn: $opened)
                    if opened { DateField(label: L10n.text("ui.opened_date"), value: $openedDate, range: Date.distantPast...LocalDay.today.date.addingTimeInterval(86399)); TextField(L10n.text("ui.days_valid_after_opening"), text: $days).keyboardType(.numberPad) }
                    Text(L10n.text("ui.actual_expiry_is_the_earlier_of_package_expiry_and_opened")).font(.caption).foregroundStyle(.secondary)
                }
                Section(L10n.text("ui.notes")) { TextField(L10n.text("ui.notes_optional"), text: $item.notes, axis: .vertical).lineLimit(4...8) }
                if let validation { Text(validation).foregroundStyle(.red) }
            }.scrollDismissesKeyboard(.immediately).frame(maxWidth: 640).frame(maxWidth: .infinity).sxNavigationBackground()
                .navigationTitle(isNew ? L10n.text("ui.add_supply") : L10n.text("ui.edit_supply")).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("ui.cancel")) { dismiss() }.disabled(saving) }; ToolbarItem(placement: .confirmationAction) { Button(saving ? L10n.text("ui.saving_") : L10n.text("ui.save")) { save() }.keyboardShortcut("s", modifiers: .command).disabled(saving).accessibilityIdentifier("expiry.save") } }
        }.interactiveDismissDisabled(saving)
    }
    private func save() {
        do {
            item.openedDate = opened ? openedDate : nil; item.openedValidityDays = opened ? Int(days) : nil
            if opened && (item.openedValidityDays == nil || openedDate > .today || openedDate > item.packageExpiryDate) { throw AppPresentationError("ui.check_the_validity_period_the_opened_date_cannot_be_after") }
            try item.validate(); saving = true; validation = nil
            Task { if await model.mutate({ try await model.store.saveExpiry(item) }) { dismiss() }; saving = false }
        } catch { validation = L10n.error(error) }
    }
}
