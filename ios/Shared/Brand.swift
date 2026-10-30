import UIKit
import CoreText

/// Brand: Ambedkar-blue gradients, Playfair Display for headings, Lora for text.
enum Brand {
    static let navy = UIColor(red: 0x08 / 255, green: 0x1A / 255, blue: 0x4A / 255, alpha: 1)
    static let blueDeep = UIColor(red: 0x0C / 255, green: 0x23 / 255, blue: 0x66 / 255, alpha: 1)
    static let blue = UIColor(red: 0x1A / 255, green: 0x4F / 255, blue: 0xD6 / 255, alpha: 1)
    static let azure = UIColor(red: 0x3D / 255, green: 0x8B / 255, blue: 0xFF / 255, alpha: 1)
    static let tint = UIColor(red: 0xEA / 255, green: 0xF0 / 255, blue: 0xFF / 255, alpha: 1)
    static let onBlueMuted = UIColor(red: 0xC3 / 255, green: 0xD3 / 255, blue: 0xFF / 255, alpha: 1)
    static let glass = UIColor.white.withAlphaComponent(0.2)

    private static var registered = false

    /// Register the bundled fonts once (works in the app and the keyboard).
    static func registerFonts() {
        guard !registered else { return }
        registered = true
        let bundle = Bundle(for: NameDictionary.self)
        for name in ["playfair_bold", "lora_regular", "lora_semibold"] {
            if let url = bundle.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }

    static func display(_ size: CGFloat) -> UIFont {
        registerFonts()
        return UIFont(name: "PlayfairDisplay-Bold", size: size) ?? serifFallback(size, bold: true)
    }

    static func text(_ size: CGFloat, bold: Bool = false) -> UIFont {
        registerFonts()
        return UIFont(name: bold ? "Lora-SemiBold" : "Lora-Regular", size: size) ?? serifFallback(size, bold: bold)
    }

    private static func serifFallback(_ size: CGFloat, bold: Bool) -> UIFont {
        let base = UIFont.systemFont(ofSize: size, weight: bold ? .semibold : .regular)
        guard let d = base.fontDescriptor.withDesign(.serif) else { return base }
        return UIFont(descriptor: d, size: size)
    }

    static func gradientLayer(_ colors: [UIColor], start: CGPoint = CGPoint(x: 0, y: 0),
                              end: CGPoint = CGPoint(x: 1, y: 1)) -> CAGradientLayer {
        let g = CAGradientLayer()
        g.colors = colors.map { $0.cgColor }
        g.startPoint = start
        g.endPoint = end
        return g
    }
}

/// A view whose whole background is a gradient (resizes with the view).
final class GradientView: UIView {
    override class var layerClass: AnyClass { CAGradientLayer.self }
    init(_ colors: [UIColor]) {
        super.init(frame: .zero)
        let g = layer as! CAGradientLayer
        g.colors = colors.map { $0.cgColor }
        g.startPoint = CGPoint(x: 0, y: 0)
        g.endPoint = CGPoint(x: 1, y: 1)
    }
    required init?(coder: NSCoder) { fatalError() }
}
