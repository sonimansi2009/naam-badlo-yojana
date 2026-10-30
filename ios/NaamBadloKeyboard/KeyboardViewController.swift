import UIKit

/// Naam Chupao keyboard. Type on your usual keyboard, tap 🌐 to switch here,
/// tap "Make it Meta friendly" (again for fresh names) or pick a code name.
final class KeyboardViewController: UIInputViewController {

    private let dict = NameDictionary.shared
    private let titleLabel = UILabel()
    private let banner = UILabel()
    private let bannerBox = UIView()
    private let chipStack = UIStackView()
    private let scroll = UIScrollView()
    private var metaKey: UIButton!
    private var lastKey = ""

    override func viewDidLoad() {
        super.viewDidLoad()
        let bg = GradientView([Brand.bgTop, Brand.bg])
        bg.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(bg)
        NSLayoutConstraint.activate([
            bg.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bg.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bg.topAnchor.constraint(equalTo: view.topAnchor),
            bg.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])

        let root = UIStackView()
        root.axis = .vertical
        root.spacing = 8
        root.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(root)
        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            root.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),
            root.topAnchor.constraint(equalTo: view.topAnchor, constant: 12),
            root.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -8),
        ])
        let height = view.heightAnchor.constraint(equalToConstant: 222)
        height.priority = .defaultHigh
        height.isActive = true

        titleLabel.font = Brand.sans(13.5, .medium)
        titleLabel.textColor = Brand.muted
        root.addArrangedSubview(titleLabel)

        chipStack.axis = .horizontal
        chipStack.spacing = 6
        chipStack.translatesAutoresizingMaskIntoConstraints = false
        scroll.showsHorizontalScrollIndicator = false
        scroll.addSubview(chipStack)
        NSLayoutConstraint.activate([
            chipStack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            chipStack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            chipStack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            chipStack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            chipStack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
            scroll.heightAnchor.constraint(equalToConstant: 42),
        ])
        root.addArrangedSubview(scroll)

        metaKey = makeCTA("Make it Meta friendly") { [weak self] in self?.makeFriendly() }
        metaKey.heightAnchor.constraint(equalToConstant: 52).isActive = true
        root.addArrangedSubview(metaKey)

        let bar = UIStackView()
        bar.axis = .horizontal
        bar.spacing = 6
        let globe = makeKey("🌐", action: nil)
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        globe.isHidden = !needsInputModeSwitchKey
        let space = makeKey("space") { [weak self] in self?.textDocumentProxy.insertText(" ") }
        let del = makeKey("⌫") { [weak self] in self?.textDocumentProxy.deleteBackward() }
        let ret = makeKey("↵") { [weak self] in self?.textDocumentProxy.insertText("\n") }
        [globe, space, del, ret].forEach { bar.addArrangedSubview($0) }
        [globe, del, ret].forEach { $0.widthAnchor.constraint(equalToConstant: 56).isActive = true }
        bar.heightAnchor.constraint(equalToConstant: 44).isActive = true
        root.addArrangedSubview(bar)

        // Congratulations banner, floats over the top of the keyboard
        bannerBox.backgroundColor = Brand.black
        bannerBox.layer.cornerRadius = 18
        bannerBox.alpha = 0
        bannerBox.translatesAutoresizingMaskIntoConstraints = false
        banner.text = "✓  " + NameDictionary.congrats
        banner.numberOfLines = 3
        banner.font = Brand.sans(14.5, .medium)
        banner.textColor = .white
        banner.translatesAutoresizingMaskIntoConstraints = false
        bannerBox.addSubview(banner)
        view.addSubview(bannerBox)
        NSLayoutConstraint.activate([
            bannerBox.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            bannerBox.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),
            bannerBox.topAnchor.constraint(equalTo: view.topAnchor, constant: 10),
            banner.leadingAnchor.constraint(equalTo: bannerBox.leadingAnchor, constant: 16),
            banner.trailingAnchor.constraint(equalTo: bannerBox.trailingAnchor, constant: -16),
            banner.topAnchor.constraint(equalTo: bannerBox.topAnchor, constant: 12),
            banner.bottomAnchor.constraint(equalTo: bannerBox.bottomAnchor, constant: -12),
        ])
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        lastKey = ""
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
        let text = before
        let all = dict.findAll(text)
        let real = all.filter { !$0.fromMemory }.count
        setCTATitle((real == 0 && !all.isEmpty) ? "Fresh names" : "Make it Meta friendly")
        metaKey.alpha = all.isEmpty ? 0.6 : 1

        let m = dict.matchAtEnd(text)
        let key = m.map { "\($0.entity.id):\(text.distance(from: text.startIndex, to: $0.range.lowerBound)):\(text.count)" } ?? "none:\(all.count)"
        guard key != lastKey else { return }
        lastKey = key
        chipStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        guard let m = m else {
            titleLabel.text = all.isEmpty
                ? "Type a name on your usual keyboard, then tap 🌐 to come here"
                : "\(all.count) name(s) in your text"
            return
        }
        titleLabel.text = "“\(text[m.range])” · \(m.entity.label) · pick a code name"
        for (i, alt) in m.entity.alternates.shuffled().enumerated() {
            let chip = makeChip(alt)
            chipStack.addArrangedSubview(chip)
            if i < 6 {
                chip.alpha = 0
                chip.transform = CGAffineTransform(translationX: 0, y: 6)
                UIView.animate(withDuration: 0.28, delay: Double(i) * 0.035, options: .curveEaseOut) {
                    chip.alpha = 1; chip.transform = .identity
                }
            }
        }
        scroll.setContentOffset(.zero, animated: false)
    }

    private func swap(to alt: String) {
        let text = before
        guard let m = dict.matchAtEnd(text) else { return }
        let tail = String(text[m.range.upperBound...])
        dict.remember(alt, as: m.entity)
        deleteBackward(count: text[m.range.lowerBound...].count)
        textDocumentProxy.insertText(alt + tail)
        celebrate()
    }

    private func makeFriendly() {
        let text = before
        guard !dict.findAll(text).isEmpty else { return }
        let out = dict.replaceAll(text)
        deleteBackward(count: text.count)
        textDocumentProxy.insertText(out)
        celebrate()
    }

    private func celebrate() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        view.bringSubviewToFront(bannerBox)
        bannerBox.transform = CGAffineTransform(translationX: 0, y: -8)
        UIView.animate(withDuration: 0.28) {
            self.bannerBox.alpha = 1; self.bannerBox.transform = .identity
        }
        UIView.animate(withDuration: 0.25, delay: 2.6, options: []) {
            self.bannerBox.alpha = 0
        }
    }

    private func deleteBackward(count: Int) {
        for _ in 0..<count { textDocumentProxy.deleteBackward() }
    }

    // MARK: - Views

    private func makeChip(_ title: String) -> UIButton {
        var cfg = UIButton.Configuration.filled()
        cfg.baseBackgroundColor = .white
        cfg.baseForegroundColor = Brand.ink
        cfg.cornerStyle = .capsule
        cfg.contentInsets = NSDirectionalEdgeInsets(top: 7, leading: 16, bottom: 7, trailing: 16)
        var t = AttributedString(title)
        t.font = Brand.sans(15, .medium)
        cfg.attributedTitle = t
        let b = UIButton(configuration: cfg, primaryAction: UIAction { [weak self] _ in self?.swap(to: title) })
        b.layer.borderColor = Brand.line.cgColor
        b.layer.borderWidth = 1
        b.layer.cornerRadius = 19
        return b
    }

    /// Meta-blue pill with a round white sparkle on the right.
    private func makeCTA(_ title: String, action: @escaping () -> Void) -> UIButton {
        var cfg = UIButton.Configuration.filled()
        cfg.baseBackgroundColor = Brand.meta
        cfg.baseForegroundColor = .white
        cfg.cornerStyle = .capsule
        cfg.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 20, bottom: 6, trailing: 6)
        let dot = UIGraphicsImageRenderer(size: CGSize(width: 38, height: 38)).image { _ in
            UIColor.white.setFill()
            UIBezierPath(ovalIn: CGRect(x: 0, y: 0, width: 38, height: 38)).fill()
            if let s = UIImage(systemName: "sparkles")?.withTintColor(Brand.meta, renderingMode: .alwaysOriginal) {
                s.draw(in: CGRect(x: 10, y: 10, width: 18, height: 18))
            }
        }
        cfg.image = dot.withRenderingMode(.alwaysOriginal)
        cfg.imagePlacement = .trailing
        cfg.imagePadding = 12
        let b = UIButton(configuration: cfg)
        b.contentHorizontalAlignment = .fill
        b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        b.configurationUpdateHandler = nil
        var t = AttributedString(title)
        t.font = Brand.sans(16, .semibold)
        b.configuration?.attributedTitle = t
        return b
    }

    private func setCTATitle(_ title: String) {
        var t = AttributedString(title)
        t.font = Brand.sans(16, .semibold)
        metaKey.configuration?.attributedTitle = t
    }

    private func makeKey(_ title: String, action: (() -> Void)?) -> UIButton {
        var cfg = UIButton.Configuration.filled()
        cfg.baseBackgroundColor = .white
        cfg.baseForegroundColor = Brand.ink
        cfg.cornerStyle = .capsule
        var t = AttributedString(title)
        t.font = Brand.sans(16, .medium)
        cfg.attributedTitle = t
        let b = UIButton(configuration: cfg)
        if let action = action {
            b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        }
        return b
    }
}
