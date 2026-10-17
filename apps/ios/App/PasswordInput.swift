import SwiftUI

/// Keep the native editing buffer and AutoFill context stable when the eye button changes visibility.
struct PasswordInput: UIViewRepresentable {
    @Binding var text: String
    let title: String
    let secure: Bool
    let registering: Bool
    let identifier: String
    private var contentType: UITextContentType {
        registering ? .newPassword : .password
    }
    func makeUIView(context: Context) -> UITextField {
        let view = UITextField()
        view.text = text
        view.placeholder = title; view.isSecureTextEntry = secure; view.clearsOnBeginEditing = false
        view.autocorrectionType = .no; view.autocapitalizationType = .none; view.spellCheckingType = .no
        view.textContentType = contentType
        view.font = UIFont.preferredFont(forTextStyle: .body); view.adjustsFontForContentSizeCategory = true
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        view.accessibilityIdentifier = identifier
        view.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .editingChanged)
        return view
    }
    func updateUIView(_ view: UITextField, context: Context) {
        context.coordinator.parent = self
        if view.textContentType != contentType { view.textContentType = contentType }
        if view.isEnabled != context.environment.isEnabled { view.isEnabled = context.environment.isEnabled }
        if view.isSecureTextEntry != secure {
            let buffer = view.text
            view.isSecureTextEntry = secure; view.text = buffer
        }
        if !view.isFirstResponder, view.text != text { view.text = text }
    }
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    final class Coordinator: NSObject {
        var parent: PasswordInput
        init(_ parent: PasswordInput) { self.parent = parent }
        @objc func changed(_ sender: UITextField) {
            parent.text = sender.text ?? ""
        }
    }
}
