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

    // Ambedkar blue
    private let blue = UIColor(red: 0x14 / 255, green: 0x46 / 255, blue: 0xA0 / 255, alpha: 1)
    private let blueDeep = UIColor(red: 0x0B / 255, green: 0x2E / 255, blue: 0x6B / 255, alpha: 1)
    private let blueSoft = UIColor(red: 0x2C / 255, green: 0x5D / 255, blue: 0xBF / 255, alpha: 1)
    private let mutedOnBlue = UIColor(red: 0xB9 / 255, green: 0xC8 / 255, blue: 0xEA / 255, alpha: 1)

    private func serif(_ size: CGFloat, bold: Bool = false) -> UIFont {
        let base = UIFont.systemFont(ofSize: size, weight: bold ? .semibold : .regular)
        guard let d = base.fontDescriptor.withDesign(.serif) else { return base }
        return UIFont(descriptor: d, size: size)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = blueDeep

        let root = UIStackView()
        root.axis = .vertical
        root.spacing = 8
        root.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(root)
        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 8),
            root.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -8),
            root.topAnchor.constraint(equalTo: view.topAnchor, constant: 10),
            root.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -8),
        ])
        let height = view.heightAnchor.constraint(equalToConstant: 210)
        height.priority = .defaultHigh
        height.isActive = true

        titleLabel.font = serif(13.5)
        titleLabel.textColor = mutedOnBlue
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
            scroll.heightAnchor.constraint(equalToConstant: 42),
        ])
        root.addArrangedSubview(scroll)

        metaKey = makeKey("✦  Make it Meta friendly", primary: true) { [weak self] in self?.makeFriendly() }
        metaKey.heightAnchor.constraint(equalToConstant: 46).isActive = true
        root.addArrangedSubview(metaKey)

        let bar = UIStackView()
        bar.axis = .horizontal
        bar.spacing = 6
        bar.distribution = .fill
        let globe = makeKey("🌐", primary: false, action: nil)
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        globe.isHidden = !needsInputModeSwitchKey
        let space = makeKey("space", primary: false) { [weak self] in self?.textDocumentProxy.insertText(" ") }
        let del = makeKey("⌫", primary: false) { [weak self] in self?.textDocumentProxy.deleteBackward() }
        let ret = makeKey("↵", primary: false) { [weak self] in self?.textDocumentProxy.insertText("\n") }
        [globe, space, del, ret].forEach { bar.addArrangedSubview($0) }
        globe.widthAnchor.constraint(equalToConstant: 56).isActive = true
        del.widthAnchor.constraint(equalToConstant: 56).isActive = true
        ret.widthAnchor.constraint(equalToConstant: 56).isActive = true
        bar.heightAnchor.constraint(equalToConstant: 44).isActive = true
        root.addArrangedSubview(bar)

        // Congratulations banner, floats over the top of the keyboard
        bannerBox.backgroundColor = .white
        bannerBox.layer.cornerRadius = 12
        bannerBox.alpha = 0
        bannerBox.translatesAutoresizingMaskIntoConstraints = false
        banner.text = "✓  " + NameDictionary.congrats
        banner.numberOfLines = 3
        banner.font = serif(14, bold: true)
        banner.textColor = blueDeep
        banner.translatesAutoresizingMaskIntoConstraints = false
        bannerBox.addSubview(banner)
        view.addSubview(bannerBox)
        NSLayoutConstraint.activate([
            bannerBox.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            bannerBox.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),
            bannerBox.topAnchor.constraint(equalTo: view.topAnchor, constant: 10),
            banner.leadingAnchor.constraint(equalTo: bannerBox.leadingAnchor, constant: 14),
            banner.trailingAnchor.constraint(equalTo: bannerBox.trailingAnchor, constant: -14),
            banner.topAnchor.constraint(equalTo: bannerBox.topAnchor, constant: 10),
            banner.bottomAnchor.constraint(equalTo: bannerBox.bottomAnchor, constant: -10),
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
        metaKey.configuration?.title = (real == 0 && !all.isEmpty) ? "↻  Fresh names" : "✦  Make it Meta friendly"
        metaKey.alpha = all.isEmpty ? 0.55 : 1

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
        titleLabel.text = "“\(text[m.range])”  ·  \(m.entity.label)  ·  pick a code name"
        for (i, alt) in m.entity.alternates.shuffled().enumerated() {
            let chip = makeChip(alt)
            chipStack.addArrangedSubview(chip)
            if i < 6 {
                chip.alpha = 0
                chip.transform = CGAffineTransform(translationX: 0, y: 6)
                UIView.animate(withDuration: 0.25, delay: Double(i) * 0.035, options: .curveEaseOut) {
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
        UIView.animate(withDuration: 0.25) {
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
        cfg.baseBackgroundColor = blueSoft
        cfg.baseForegroundColor = .white
        cfg.cornerStyle = .capsule
        cfg.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 14, bottom: 6, trailing: 14)
        var t = AttributedString(title)
        t.font = serif(15)
        cfg.attributedTitle = t
        return UIButton(configuration: cfg, primaryAction: UIAction { [weak self] _ in self?.swap(to: title) })
    }

    private func makeKey(_ title: String, primary: Bool, action: (() -> Void)?) -> UIButton {
        var cfg = UIButton.Configuration.filled()
        cfg.baseBackgroundColor = primary ? .white : blueSoft
        cfg.baseForegroundColor = primary ? blueDeep : .white
        cfg.cornerStyle = .medium
        cfg.title = title
        let font = serif(16, bold: primary)
        cfg.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { a in
            var a = a; a.font = font; return a
        }
        let b = UIButton(configuration: cfg)
        if let action = action {
            b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        }
        return b
    }
}
