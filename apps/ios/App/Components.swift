import SwiftUI
import SuirenCore

enum Palette {
    static let accent = Color(red: 0.69, green: 0.92, blue: 0.07)
    static let background = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 0.12, green: 0.12, blue: 0.11, alpha: 1) : UIColor(red: 0.967, green: 0.967, blue: 0.948, alpha: 1) })
    static let surface = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.17, alpha: 1) : .white })
    static let inset = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.22, alpha: 1) : UIColor(red: 0.945, green: 0.945, blue: 0.93, alpha: 1) })
}
extension View {
    func sxCard(padding: CGFloat = 16) -> some View {
        self.padding(padding).background(Palette.surface, in: RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.primary.opacity(0.09)))
    }
    func sxPage() -> some View { self.frame(maxWidth: 1000).padding(.horizontal, 16).padding(.bottom, 24).frame(maxWidth: .infinity).background(Palette.background) }
    func sxNavigationBackground() -> some View {
        self.scrollContentBackground(.hidden)
            .background(Palette.background.ignoresSafeArea())
            .toolbarBackground(Palette.background, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
    }
}
struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity).frame(minHeight: 48)
            .foregroundStyle(Palette.surface).background(Color.primary.opacity(configuration.isPressed ? 0.7 : 1), in: RoundedRectangle(cornerRadius: 14))
            .opacity(enabled ? 1 : 0.4)
    }
}
struct PageHeading: View {
    let title: String; var subtitle: String = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.system(.largeTitle, design: .default).weight(.semibold))
            if !subtitle.isEmpty { Text(subtitle).font(.subheadline).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true) }
        }.frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 12)
    }
}
struct InfoRow: View {
    let label: String; let value: String
    var body: some View { LabeledContent { Text(value.isEmpty ? "未填写" : value).multilineTextAlignment(.trailing).textSelection(.enabled) } label: { Text(label).foregroundStyle(.secondary) }.font(.subheadline).padding(.vertical, 4) }
}
struct Chip: View {
    let title: String; var selected = false; let action: () -> Void
    var body: some View {
        Button(action: action) { Text(title).font(.caption.weight(selected ? .semibold : .regular)).padding(.horizontal, 12).frame(minHeight: 44)
                .foregroundStyle(selected ? Palette.surface : Color.primary).background(selected ? Color.primary : Palette.surface, in: Capsule())
                .overlay(Capsule().strokeBorder(.primary.opacity(0.1))) }
            .buttonStyle(.plain).accessibilityAddTraits(selected ? .isSelected : [])
    }
}
struct AssetImage: View {
    let key: String; var size: CGFloat = 44
    var body: some View { Image(AssetIcon.find(key).image).resizable().scaledToFit().frame(width: size, height: size).padding(7).background(Palette.inset, in: RoundedRectangle(cornerRadius: 14)).accessibilityLabel(AssetIcon.find(key).label) }
}
func currency(_ cents: Decimal) -> String {
    let f = NumberFormatter(); f.numberStyle = .decimal; f.minimumFractionDigits = 2; f.maximumFractionDigits = 2
    return "¥" + (f.string(from: (cents / 100) as NSDecimalNumber) ?? "0.00")
}
struct SummaryCard: View {
    let assets: [Asset]; var totalCount: Int?; var compact = false
    var body: some View {
        let summary = AssetSummary(assets)
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 7) { Circle().fill(Palette.accent).frame(width: 6, height: 6); Text("资产概览").font(.caption.weight(.semibold)); if let totalCount { Text("\(summary.count)/\(totalCount)").font(.caption.monospacedDigit()).foregroundStyle(.secondary) } }
            HStack(alignment: .firstTextBaseline, spacing: 16) {
                amount("总资产金额", currency(summary.totalCents), large: true)
                Spacer(minLength: 0); amount("日均成本", currency(summary.dailyCents), large: false)
            }
            if !compact {
                ViewThatFits(in: .horizontal) {
                    HStack { metric("服役中", "\(summary.active)"); Spacer(); metric("已退役", "\(summary.retired)"); Spacer(); metric("最长持有天", "\(summary.longestDays)") }
                    VStack(alignment: .leading, spacing: 8) { metric("服役中", "\(summary.active)"); metric("已退役", "\(summary.retired)"); metric("最长持有天", "\(summary.longestDays)") }
                }.padding(10).frame(maxWidth: .infinity).background(Palette.inset, in: RoundedRectangle(cornerRadius: 12))
            }
        }.sxCard()
    }
    private func amount(_ label: String, _ value: String, large: Bool) -> some View {
        VStack(alignment: large ? .leading : .trailing, spacing: 8) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.system(large ? .title : .title2, design: .monospaced).weight(.medium)).lineLimit(1).minimumScaleFactor(0.4).accessibilityLabel("\(label) \(value)")
        }
    }
    private func metric(_ label: String, _ value: String) -> some View { HStack(spacing: 4) { Text(label).foregroundStyle(.secondary); Text(value).fontWeight(.semibold).monospacedDigit() }.font(.caption2).fixedSize() }
}
struct DateField: View {
    let label: String; @Binding var value: LocalDay; var range: ClosedRange<Date>? = nil
    private var selection: Binding<Date> { Binding(get: { value.date.addingTimeInterval(12 * 3600) }, set: { value = LocalDay(date: $0, timeZone: TimeZone(secondsFromGMT: 0)!) }) }
    var body: some View {
        if let range { DatePicker(label, selection: selection, in: range, displayedComponents: .date).environment(\.timeZone, TimeZone(secondsFromGMT: 0)!) }
        else { DatePicker(label, selection: selection, displayedComponents: .date).environment(\.timeZone, TimeZone(secondsFromGMT: 0)!) }
    }
}
struct EmptyState: View {
    let title: String; var detail = ""
    var body: some View { ContentUnavailableView(title, systemImage: "shippingbox", description: Text(detail)).frame(minHeight: 200) }
}
