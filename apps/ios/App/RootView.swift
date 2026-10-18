import SwiftUI
import SuirenCore

enum MainPage: String, CaseIterable, Identifiable {
    case overview = "总览", assets = "资产", tools = "工具", settings = "设置"
    var id: Self { self }
    var icon: String { switch self { case .overview: return "house"; case .assets: return "shippingbox"; case .tools: return "square.grid.2x2"; case .settings: return "gearshape" } }
}
enum RecordDestination: Hashable {
    case asset(String), expiry(String), expiryList
}
struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
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
        .alert("操作未完成", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) { Button("好") { model.error = nil } } message: { Text(model.error ?? "") }
        .preferredColorScheme(model.settings.theme == "light" ? .light : model.settings.theme == "dark" ? .dark : nil)
    }
    // One iPad detail hierarchy survives window-width changes, preserving unfinished forms.
    private func splitPages(usesSidebar: Bool) -> some View {
        NavigationSplitView(columnVisibility: $columnVisibility, preferredCompactColumn: $compactColumn) {
            List { ForEach(MainPage.allCases) { page in
                Button { selection = page } label: { Label(page.rawValue, systemImage: page.icon).padding(.vertical, 10).frame(maxWidth: .infinity, alignment: .leading) }
                    .accessibilityIdentifier("nav.\(page.id)").listRowBackground(selection == page ? Palette.accent.opacity(0.65) : Color.clear)
                    .accessibilityAddTraits(selection == page ? .isSelected : [])
            } }
                .navigationTitle("").navigationSplitViewColumnWidth(min: 190, ideal: 220, max: 260)
                .sxNavigationBackground().toolbar(.hidden, for: .navigationBar)
                .safeAreaInset(edge: .top, spacing: 0) {
                    Text("燧人").font(.title2.weight(.semibold)).frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
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
    private var bottomNavigation: some View {
        HStack(spacing: 4) {
            ForEach(MainPage.allCases) { page in
                Button { selection = page } label: {
                    VStack(spacing: 4) { Image(systemName: page.icon).font(.system(size: 19)); Text(page.rawValue).font(.caption2) }
                        .frame(maxWidth: .infinity).frame(minHeight: 48).foregroundStyle(selection == page ? Color.black : Color.primary.opacity(0.65))
                        .background(selection == page ? Palette.accent : Color.clear, in: RoundedRectangle(cornerRadius: 14))
                }.buttonStyle(.plain).accessibilityIdentifier("nav.\(page.id)").accessibilityAddTraits(selection == page ? .isSelected : [])
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
                    Image(systemName: "sidebar.left").frame(width: 44, height: 44)
                }.accessibilityLabel("切换侧栏")
            }
            Text(selection == .overview ? "燧人" : selection.rawValue)
                .font(.title2.weight(.semibold)).accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("page.title")
            Spacer(minLength: 0)
            if selection == .assets {
                Button { assetEditor = true } label: { Image(systemName: "plus").frame(width: 44, height: 44) }
                    .accessibilityLabel("新增资产").accessibilityIdentifier("asset.add")
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
                PageHeading(title: "记录拥有，\n看见每一天的价值", subtitle: "资产与日常用品，都在本机妥善保存。")
                Button { select(.assets) } label: { SummaryCard(assets: model.assets.filter { $0.archivedAt == nil }) }.buttonStyle(.plain)
                NavigationLink(value: RecordDestination.expiryList) {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("用品 / 保质期", systemImage: "calendar").font(.subheadline.weight(.semibold))
                        HStack(alignment: .firstTextBaseline) { Text("\(model.pendingExpiry)").font(.largeTitle.monospacedDigit()); Text("项待处理").font(.subheadline).foregroundStyle(.secondary); Spacer(); Image(systemName: "arrow.right") }
                        Text("临期、今天到期和已过期的在用用品").font(.caption).foregroundStyle(.secondary)
                    }.sxCard()
                }.buttonStyle(.plain)
                HStack { Text(model.syncLabel).font(.caption).foregroundStyle(.secondary); Spacer(); Button("同步设置") { select(.settings) }.font(.caption.weight(.semibold)) }.padding(.vertical, 8)
            }.sxPage()
        }.refreshable { await model.refresh() }
    }
}
struct ToolsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PageHeading(title: "把日常工具放在一起", subtitle: "从一个工具开始，继续扩展你的个人工作流。")
                NavigationLink(value: RecordDestination.expiryList) {
                    HStack(spacing: 14) { Image(systemName: "shippingbox").padding(12).background(Palette.accent, in: RoundedRectangle(cornerRadius: 12)).foregroundStyle(.black)
                        VStack(alignment: .leading, spacing: 6) { Text("用品 / 保质期").fontWeight(.semibold); Text("\(model.pendingExpiry) 项待处理").font(.caption).foregroundStyle(.secondary) }; Spacer(); Image(systemName: "chevron.right") }.sxCard()
                }.buttonStyle(.plain).accessibilityIdentifier("tools.expiry")
                Text("更多工具 · 即将加入").font(.subheadline).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading).sxCard()
            }.sxPage()
        }
    }
}
