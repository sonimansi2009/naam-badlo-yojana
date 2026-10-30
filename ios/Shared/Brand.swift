import UIKit
import CoreText

/// Brand: soft periwinkle ground, white cards, black active pills, Meta-blue actions.
/// Type: Instrument Sans, with Instrument Serif Italic for one accent word.
enum Brand {
    static let bgTop = UIColor(red: 0xD8 / 255, green: 0xDF / 255, blue: 0xF1 / 255, alpha: 1)
    static let bg = UIColor(red: 0xCA / 255, green: 0xD3 / 255, blue: 0xEB / 255, alpha: 1)
    static let soft = UIColor(red: 0xE8 / 255, green: 0xEC / 255, blue: 0xF6 / 255, alpha: 1)
    static let line = UIColor(red: 0xDD / 255, green: 0xE3 / 255, blue: 0xF0 / 255, alpha: 1)
    static let ink = UIColor(red: 0x10 / 255, green: 0x12 / 255, blue: 0x17 / 255, alpha: 1)
    static let muted = UIColor(red: 0x6A / 255, green: 0x71 / 255, blue: 0x84 / 255, alpha: 1)
    static let black = UIColor(red: 0x12 / 255, green: 0x13 / 255, blue: 0x16 / 255, alpha: 1)
    static let meta = UIColor(red: 0x08 / 255, green: 0x66 / 255, blue: 0xFF / 255, alpha: 1)

    private static var registered = false

    /// Register the bundled fonts once (works in the app and the keyboard).
    static func registerFonts() {
        guard !registered else { return }
        registered = true
        let bundle = Bundle(for: NameDictionary.self)
        for name in ["isans_regular", "isans_medium", "isans_semibold", "iserif_italic"] {
            if let url = bundle.url(forResource: name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }

    enum Weight { case regular, medium, semibold }

    static func sans(_ size: CGFloat, _ w: Weight = .regular) -> UIFont {
        registerFonts()
        let name: String
        switch w {
        case .regular: name = "InstrumentSans-Regular"
        case .medium: name = "InstrumentSans-Medium"
        case .semibold: name = "InstrumentSans-SemiBold"
        }
        let sys: UIFont.Weight = w == .regular ? .regular : (w == .medium ? .medium : .semibold)
        return UIFont(name: name, size: size) ?? .systemFont(ofSize: size, weight: sys)
    }

    static func serifItalic(_ size: CGFloat) -> UIFont {
        registerFonts()
        return UIFont(name: "InstrumentSerif-Italic", size: size) ?? .italicSystemFont(ofSize: size)
    }
}

/// A view whose whole background is a vertical gradient.
final class GradientView: UIView {
    override class var layerClass: AnyClass { CAGradientLayer.self }
    init(_ colors: [UIColor]) {
        super.init(frame: .zero)
        let g = layer as! CAGradientLayer
        g.colors = colors.map { $0.cgColor }
        g.startPoint = CGPoint(x: 0.5, y: 0)
        g.endPoint = CGPoint(x: 0.5, y: 1)
    }
    required init?(coder: NSCoder) { fatalError() }
}
