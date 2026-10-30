import UIKit

/// A slim add-on keyboard. Type on your usual keyboard, tap 🌐 to switch here,
/// pick a swap for the name before the cursor, then tap 🌐 to go back.
final class KeyboardViewController: UIInputViewController {

    private let dict = NameDictionary.shared
    private let titleLabel = UILabel()
    private let chipStack = UIStackView()
    private let scroll = UIScrollView()
    private let accent = UIColor(red: 0.95, green: 0.60, blue: 0.29, alpha: 1)

    override func viewDidLoad() {
        super.viewDidLoad()
        let root = UIStackView()
        root.axis = .vertical
        root.spacing = 6
        root.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(root)
        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 8),
            root.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -8),
            root.topAnchor.constraint(equalTo: view.topAnchor, constant: 8),
            root.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -8),
        ])
        let height = view.heightAnchor.constraint(equalToConstant: 150)
        height.priority = .defaultHigh
        height.isActive = true

        titleLabel.font = .systemFont(ofSize: 13)
        titleLabel.textColor = .secondaryLabel
        titleLabel.numberOfLines = 1
        root.addArrangedSubview(titleLabel)

        chipStack.axis = .horizontal
        chipStack.spacing = 8
        chipStack.translatesAutoresizingMaskIntoConstraints = false
        scroll.showsHorizontalScrollIndicator = false
        scroll.addSubview(chipStack)
        NSLayoutConstraint.activate([
            chipStack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            chipStack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            chipStack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            chipStack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            chipStack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
            scroll.heightAnchor.constraint(equalToConstant: 44),
        ])
        root.addArrangedSubview(scroll)

        let bar = UIStackView()
        bar.axis = .horizontal
        bar.spacing = 6
        bar.distribution = .fillProportionally

        let globe = makeKey("🌐", weight: 1)
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        globe.isHidden = !needsInputModeSwitchKey
        bar.addArrangedSubview(globe)
        bar.addArrangedSubview(makeKey("Make it Meta friendly", weight: 3, accent: true) { [weak self] in self?.replaceAll() })
        bar.addArrangedSubview(makeKey("space", weight: 2) { [weak self] in self?.textDocumentProxy.insertText(" ") })
        bar.addArrangedSubview(makeKey("⌫", weight: 1) { [weak self] in self?.textDocumentProxy.deleteBackward() })
        bar.addArrangedSubview(makeKey("↵", weight: 1) { [weak self] in self?.textDocumentProxy.insertText("\n") })
        bar.heightAnchor.constraint(equalToConstant: 44).isActive = true
        root.addArrangedSubview(bar)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refresh()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        refresh()
    }

    override func selectionDidChange(_ textInput: UITextInput?) {
        super.selectionDidChange(textInput)
        refresh()
    }

    private var before: String { textDocumentProxy.documentContextBeforeInput ?? "" }

    private func refresh() {
        chipStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let text = before
        guard let m = dict.matchAtEnd(text) else {
            let n = dict.findAll(text).count
            titleLabel.text = n > 0
                ? "\(n) name(s) found. Tap “Make it Meta friendly”."
                : "Type a name on your usual keyboard, then tap 🌐 to come here."
            return
        }
        titleLabel.text = "“\(text[m.range])” → pick a swap (\(m.entity.alternates.count))"
        for alt in m.entity.alternates.shuffled() {
            chipStack.addArrangedSubview(makeChip(alt))
        }
        scroll.setContentOffset(.zero, animated: false)
    }

    private func swap(to alt: String) {
        let text = before
        guard let m = dict.matchAtEnd(text) else { return }
        let tail = String(text[m.range.upperBound...])
        deleteBackward(count: text[m.range.lowerBound...].count)
        textDocumentProxy.insertText(alt + tail)
        refresh()
    }

    private func replaceAll() {
        let text = before
        let out = dict.replaceAll(text)
        guard out != text else { return }
        deleteBackward(count: text.count)
        textDocumentProxy.insertText(out)
        refresh()
    }

    private func deleteBackward(count: Int) {
        for _ in 0..<count { textDocumentProxy.deleteBackward() }
    }

    // MARK: - Views

    private func makeChip(_ title: String) -> UIButton {
        var cfg = UIButton.Configuration.gray()
        cfg.title = title
        cfg.cornerStyle = .capsule
        cfg.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 14, bottom: 6, trailing: 14)
        return UIButton(configuration: cfg, primaryAction: UIAction { [weak self] _ in self?.swap(to: title) })
    }

    private func makeKey(_ title: String, weight: CGFloat, accent isAccent: Bool = false,
                         action: (() -> Void)? = nil) -> UIButton {
        var cfg = isAccent ? UIButton.Configuration.filled() : UIButton.Configuration.gray()
        cfg.title = title
        cfg.cornerStyle = .medium
        if isAccent {
            cfg.baseBackgroundColor = accent
            cfg.baseForegroundColor = .black
        }
        let b = UIButton(configuration: cfg)
        if let action = action {
            b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        }
        b.widthAnchor.constraint(greaterThanOrEqualToConstant: 36 * weight).isActive = true
        return b
    }
}
