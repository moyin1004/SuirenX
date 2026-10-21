import SwiftUI
import SuirenCore

enum MainPage: String, CaseIterable, Identifiable {
    case overview, assets, tools, settings
    var title: String { switch self { case .overview: return L10n.text("ui.overview"); case .assets: return L10n.text("ui.assets"); case .tools: return L10n.text("ui.tools"); case .settings: return L10n.text("ui.settings") } }

    var id: Self { self }
    var icon: String { switch self { case .overview: return "house"; case .assets: return "shippingbox"; case .tools: return "square.grid.2x2"; case .settings: return "gearshape" } }
}
enum RecordDestination: Hashable {
    case asset(String), expiry(String), expiryList
}
struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var selection: MainPage = .overview
    @State private var assetQuery = AssetQuery()
    @State private var assetSearch = false
    @State private var assetCompact = false
    @State private var assetEditor = false
    @State private var path = NavigationPath()
    @State private var columnVisibility: NavigationSplitViewVisibility = .detailOnly
    @State private var compactColumn: NavigationSplitViewColumn = .detail
    var body: some View {
        GeometryReader { geometry in
            let usesSidebar = UIDevice.current.userInterfaceIdiom == .pad && horizontalSizeClass == .regular && geometry.size.width >= 700
            VStack(spacing: 0) {
                if UIDevice.current.userInterfaceIdiom == .pad { splitPages(usesSidebar: usesSidebar) }
                else { pages() }
                if !usesSidebar { bottomNavigation }
            }.frame(maxWidth: .infinity, maxHeight: .infinity).background(Palette.background.ignoresSafeArea())
        }
        .onChange(of: selection) { _, _ in path = NavigationPath(); compactColumn = .detail }
        .alert(L10n.text("ui.action_could_not_be_completed"), isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) { Button(L10n.text("ui.ok")) { model.error = nil } } message: { Text(model.error ?? "") }
        .preferredColorScheme(model.settings.theme == "light" ? .light : model.settings.theme == "dark" ? .dark : nil)
    }
    // One iPad detail hierarchy survives window-width changes, preserving unfinished forms.
    private func splitPages(usesSidebar: Bool) -> some View {
        NavigationSplitView(columnVisibility: $columnVisibility, preferredCompactColumn: $compactColumn) {
            List { ForEach(MainPage.allCases) { page in
                Button { selection = page } label: {
                    Group {
                        if dynamicTypeSize.isAccessibilitySize {
                            HStack(spacing: 12) {
                                Image(systemName: page.icon).font(.system(size: 19)).frame(width: 24)
                                Text(page.title).fixedSize(horizontal: true, vertical: false)
                            }
                        } else { Label(page.title, systemImage: page.icon) }
                    }.padding(.vertical, 10).frame(maxWidth: .infinity, alignment: .leading)
                }
                    .accessibilityLabel(page.title).accessibilityIdentifier("nav.\(page.id)").listRowBackground(selection == page ? Palette.accent.opacity(0.65) : Color.clear)
                    .accessibilityAddTraits(selection == page ? .isSelected : [])
            } }
                .navigationTitle("").navigationSplitViewColumnWidth(min: dynamicTypeSize.isAccessibilitySize ? accessibleSidebarWidth : 190, ideal: dynamicTypeSize.isAccessibilitySize ? accessibleSidebarWidth : 220, max: dynamicTypeSize.isAccessibilitySize ? accessibleSidebarWidth : 260)
                .sxNavigationBackground().toolbar(.hidden, for: .navigationBar)
                .safeAreaInset(edge: .top, spacing: 0) {
                    Text(L10n.text("ui.suiren")).font(.title2.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.75).frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                        .padding(.horizontal, 16).background(Palette.background)
                }.accessibilityHidden(!usesSidebar)
        } detail: {
            pages(usesSidebar: usesSidebar).toolbar(removing: usesSidebar ? nil : .sidebarToggle).background(Palette.background)
        }
        .navigationSplitViewStyle(.balanced)
        .onChange(of: usesSidebar, initial: true) { _, wide in
            columnVisibility = wide ? .all : .detailOnly; compactColumn = .detail
        }
    }
    private var accessibleSidebarWidth: CGFloat {
        let font = UIFont.preferredFont(forTextStyle: .body)
        let titleWidth = MainPage.allCases.map { ($0.title as NSString).size(withAttributes: [.font: font]).width }.max() ?? 0
        return max(260, ceil(titleWidth) + 96)
    }
    private var bottomNavigation: some View {
        HStack(spacing: 4) {
            ForEach(MainPage.allCases) { page in
                Button { selection = page } label: {
                    VStack(spacing: 4) {
                        Image(systemName: page.icon).font(.system(size: 19))
                        if !dynamicTypeSize.isAccessibilitySize { Text(page.title).font(.caption2) }
                    }
                        .frame(maxWidth: .infinity).frame(minHeight: 48).foregroundStyle(selection == page ? Color.black : Color.primary.opacity(0.65))
                        .background(selection == page ? Palette.accent : Color.clear, in: RoundedRectangle(cornerRadius: 14))
                }.buttonStyle(.plain).accessibilityLabel(page.title).accessibilityIdentifier("nav.\(page.id)").accessibilityAddTraits(selection == page ? .isSelected : [])
            }
        }.padding(7).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22)).overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.primary.opacity(0.08))).padding(.horizontal, 14).padding(.bottom, 8)
    }
    private func pages(usesSidebar: Bool = false) -> some View {
        NavigationStack(path: $path) {
            selectedPage.navigationTitle("").navigationBarTitleDisplayMode(.inline)
                .sxNavigationBackground()
                .toolbar(.hidden, for: .navigationBar)
                .safeAreaInset(edge: .top, spacing: 0) { mainHeader(usesSidebar: usesSidebar) }
                .navigationDestination(for: RecordDestination.self) { destination in
                    Group {
                        switch destination {
                        case .asset(let id): AssetDetailView(id: id)
                        case .expiry(let id): ExpiryDetailView(id: id)
                        case .expiryList: ExpiryView()
                        }
                    }.sxNavigationBackground().toolbar(.visible, for: .navigationBar)
                }
                .navigationDestination(for: SettingsDestination.self) { SettingsDestinationView(destination: $0).sxNavigationBackground().toolbar(.visible, for: .navigationBar) }
        }.background(Palette.background.ignoresSafeArea()).id(selection)
    }
    private func mainHeader(usesSidebar: Bool) -> some View {
        HStack(spacing: 12) {
            if usesSidebar {
                Button { columnVisibility = columnVisibility == .all ? .detailOnly : .all } label: {
                    Image(systemName: "sidebar.left").font(dynamicTypeSize.isAccessibilitySize ? .system(size: 19) : .body).frame(width: 44, height: 44)
                }.accessibilityLabel(L10n.text("ui.toggle_sidebar"))
            }
            Text(selection == .overview ? L10n.text("ui.suiren") : selection.title)
                .font(.title2.weight(.semibold)).accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("page.title")
            Spacer(minLength: 0)
            if selection == .assets {
                Button { assetEditor = true } label: { Image(systemName: "plus").font(dynamicTypeSize.isAccessibilitySize ? .system(size: 19) : .body).frame(width: 44, height: 44) }
                    .accessibilityLabel(L10n.text("ui.add_asset")).accessibilityIdentifier("asset.add")
            }
        }.buttonStyle(.plain).frame(minHeight: 56).padding(.horizontal, 16).background(Palette.background)
    }
    @ViewBuilder private var selectedPage: some View {
        switch selection {
        case .overview: OverviewView { selection = $0 }
        case .assets: AssetsView(query: $assetQuery, showSearch: $assetSearch, showEditor: $assetEditor, compact: $assetCompact)
        case .tools: ToolsView()
        case .settings: SettingsView()
        }
    }
}
struct OverviewView: View {
    @EnvironmentObject private var model: AppModel
    let select: (MainPage) -> Void
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PageHeading(title: L10n.text("ui.record_what_you_own_nsee_its_value_every_day"), subtitle: L10n.text("ui.assets_and_daily_supplies_are_safely_stored_on_this_device"))
                Button { select(.assets) } label: { SummaryCard(assets: model.assets.filter { $0.archivedAt == nil }) }.buttonStyle(.plain)
                NavigationLink(value: RecordDestination.expiryList) {
                    VStack(alignment: .leading, spacing: 12) {
                        Label(L10n.text("ui.supplies_expiry"), systemImage: "calendar").font(.subheadline.weight(.semibold))
                        HStack(alignment: .firstTextBaseline) { Text(L10n.count("count.pending", model.pendingExpiry)).font(.title2.monospacedDigit()); Spacer(); Image(systemName: "arrow.right") }
                        Text(L10n.text("ui.in_use_supplies_due_soon_today_or_already_expired")).font(.caption).foregroundStyle(.secondary)
                    }.sxCard()
                }.buttonStyle(.plain)
                HStack { Text(model.syncLabel).font(.caption).foregroundStyle(.secondary); Spacer(); Button(L10n.text("ui.sync_settings")) { select(.settings) }.font(.caption.weight(.semibold)) }.padding(.vertical, 8)
            }.sxPage()
        }.refreshable { await model.refresh() }
    }
}
struct ToolsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PageHeading(title: L10n.text("ui.your_everyday_tools_together"), subtitle: L10n.text("ui.start_with_one_tool_and_build_your_personal_workflow"))
                NavigationLink(value: RecordDestination.expiryList) {
                    HStack(spacing: 14) { Image(systemName: "shippingbox").padding(12).background(Palette.accent, in: RoundedRectangle(cornerRadius: 12)).foregroundStyle(.black)
                        VStack(alignment: .leading, spacing: 6) { Text(L10n.text("ui.supplies_expiry")).fontWeight(.semibold); Text(L10n.count("count.pending", model.pendingExpiry)).font(.caption).foregroundStyle(.secondary) }; Spacer(); Image(systemName: "chevron.right") }.sxCard()
                }.buttonStyle(.plain).accessibilityIdentifier("tools.expiry")
                Text(L10n.text("ui.more_tools_coming_soon")).font(.subheadline).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading).sxCard()
            }.sxPage()
        }
    }
}
