import Foundation

/// A real name the keyboard watches for, and its 50 alternates.
struct NameEntity: Decodable {
    let id: String
    let label: String
    let triggers: [String]
    let alternates: [String]
}

private struct NameFile: Decodable {
    let entities: [NameEntity]
}

struct NameMatch {
    let entity: NameEntity
    let range: Range<String.Index>
}

/// Loads names.json from the bundle and finds names in text. Works offline.
final class NameDictionary {
    static let shared = NameDictionary()

    private(set) var entities: [NameEntity] = []
    private var map: [String: NameEntity] = [:]
    private var maxWords = 1
    private static let punct: Set<Character> = Set(".,!?;:\"'()[]{}#@*_~“”‘’…-|।")
    private static let possessives = ["'s", "’s"]

    init(bundle: Bundle = Bundle(for: NameDictionary.self)) {
        guard let url = bundle.url(forResource: "names", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(NameFile.self, from: data) else { return }
        entities = file.entities
        for e in entities {
            for t in e.triggers {
                let k = Self.normalize(t)
                map[k] = e
                maxWords = max(maxWords, k.split(separator: " ").count)
            }
        }
    }

    static func normalize(_ s: String) -> String {
        s.lowercased().split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }

    /// The name that ends right before the cursor, if any.
    func matchAtEnd(_ text: String) -> NameMatch? {
        let toks = tokens(text)
        let n = toks.count
        guard n > 0 else { return nil }
        for k in stride(from: min(maxWords, n), through: 1, by: -1) {
            let lo = trimLower(text, toks[n - k])
            let hi = trimUpper(text, toks[n - 1])
            if lo < hi, let m = lookup(text, lo, hi) { return m }
        }
        return nil
    }

    /// Every name in the text, left to right.
    func findAll(_ text: String) -> [NameMatch] {
        let toks = tokens(text)
        var out: [NameMatch] = []
        var i = 0
        while i < toks.count {
            var step = 1
            for k in stride(from: min(maxWords, toks.count - i), through: 1, by: -1) {
                let lo = trimLower(text, toks[i])
                let hi = trimUpper(text, toks[i + k - 1])
                guard lo < hi else { continue }
                if let m = lookup(text, lo, hi) { out.append(m); step = k; break }
            }
            i += step
        }
        return out
    }

    /// Replace every name with a random alternate.
    func replaceAll(_ text: String) -> String {
        var out = ""
        var last = text.startIndex
        for m in findAll(text) {
            out += text[last..<m.range.lowerBound]
            out += m.entity.alternates.randomElement() ?? ""
            last = m.range.upperBound
        }
        out += text[last...]
        return out
    }

    private func lookup(_ text: String, _ lo: String.Index, _ hi: String.Index) -> NameMatch? {
        let cand = Self.normalize(String(text[lo..<hi]))
        if let e = map[cand] { return NameMatch(entity: e, range: lo..<hi) }
        for suf in Self.possessives where cand.hasSuffix(suf) {
            if let e = map[String(cand.dropLast(suf.count))] {
                return NameMatch(entity: e, range: lo..<text.index(hi, offsetBy: -suf.count))
            }
        }
        return nil
    }

    private func tokens(_ text: String) -> [Range<String.Index>] {
        var out: [Range<String.Index>] = []
        var i = text.startIndex
        while i < text.endIndex {
            if text[i].isWhitespace { i = text.index(after: i); continue }
            let s = i
            while i < text.endIndex && !text[i].isWhitespace { i = text.index(after: i) }
            out.append(s..<i)
        }
        return out
    }

    private func trimLower(_ text: String, _ r: Range<String.Index>) -> String.Index {
        var lo = r.lowerBound
        while lo < r.upperBound && Self.punct.contains(text[lo]) { lo = text.index(after: lo) }
        return lo
    }

    private func trimUpper(_ text: String, _ r: Range<String.Index>) -> String.Index {
        var hi = r.upperBound
        while hi > r.lowerBound && Self.punct.contains(text[text.index(before: hi)]) {
            hi = text.index(before: hi)
        }
        return hi
    }
}
