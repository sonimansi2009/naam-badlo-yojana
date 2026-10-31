import Foundation

/// A real name the keyboard watches for, and its 50 code names.
struct NameEntity: Decodable {
    let id: String
    let label: String
    let triggers: [String]
    let alternates: [String]
    /// The first `popular` alternates are hand-picked, recognisable code names.
    let popular: Int?
    /// Bare first names / common surnames: ask before swapping.
    let ask: [String]?

    var recognisable: [String] { Array(alternates.prefix(max(1, min(popular ?? alternates.count, alternates.count)))) }
}

private struct NameFile: Decodable {
    let entities: [NameEntity]
}

struct NameMatch {
    let entity: NameEntity
    let range: Range<String.Index>
    /// True when this is a code name the app put in earlier, not the real name.
    let fromMemory: Bool
    /// True for a bare first name or common surname: confirm before swapping.
    var ask: Bool = false
}

/// Loads names.json from the bundle and finds names in text. Works offline.
final class NameDictionary {
    static let shared = NameDictionary()
    static let appName = "Naam Chupao Yojana"
    static let congrats = "Badhai ho! Aapka post ab Meta-proof hai."

    private(set) var entities: [NameEntity] = []
    private var map: [String: NameEntity] = [:]
    private var askSet: Set<String> = []
    private var maxWords = 1
    /// Code names the app inserted, so "Fresh names" can shuffle them again.
    private var memory: [String: NameEntity] = [:]
    private var memoryWords = 1

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
            for a in e.ask ?? [] { askSet.insert(Self.normalize(a)) }
        }
    }

    static func normalize(_ s: String) -> String {
        s.lowercased().split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }

    func remember(_ alt: String, as e: NameEntity) {
        if memory.count > 3000 { memory.removeAll(); memoryWords = 1 }
        let k = Self.normalize(alt)
        guard !k.isEmpty else { return }
        memory[k] = e
        memoryWords = max(memoryWords, k.split(separator: " ").count)
    }

    /// A random recognisable code name that differs from the one there now.
    static func freshPick(_ e: NameEntity, current: String) -> String {
        let cur = normalize(current)
        var options = e.recognisable.filter { normalize($0) != cur }
        if options.isEmpty { options = e.alternates.filter { normalize($0) != cur } }
        return (options.isEmpty ? e.alternates : options).randomElement() ?? current
    }

    private var words: Int { max(maxWords, memoryWords) }

    /// The name (or earlier code name) that ends right before the cursor.
    func matchAtEnd(_ text: String) -> NameMatch? {
        let toks = tokens(text)
        let n = toks.count
        guard n > 0 else { return nil }
        for k in stride(from: min(words, n), through: 1, by: -1) {
            let lo = trimLower(text, toks[n - k])
            let hi = trimUpper(text, toks[n - 1])
            if lo < hi, let m = lookup(text, lo, hi, toks[n - k].lowerBound, toks[n - 1].upperBound) { return m }
        }
        return nil
    }

    /// Every name (and earlier code name) in the text, left to right.
    func findAll(_ text: String) -> [NameMatch] {
        let toks = tokens(text)
        var out: [NameMatch] = []
        var i = 0
        while i < toks.count {
            var step = 1
            for k in stride(from: min(words, toks.count - i), through: 1, by: -1) {
                let lo = trimLower(text, toks[i])
                let hi = trimUpper(text, toks[i + k - 1])
                guard lo < hi else { continue }
                if let m = lookup(text, lo, hi, toks[i].lowerBound, toks[i + k - 1].upperBound) {
                    out.append(m); step = k; break
                }
            }
            i += step
        }
        return out
    }

    /// Replace every name with a fresh code name and remember the picks.
    /// Bare first names / surnames are skipped unless listed in `confirmed`.
    func replaceAll(_ text: String, confirmed: Set<String> = []) -> String {
        var out = ""
        var last = text.startIndex
        for m in findAll(text) {
            if m.ask && !m.fromMemory && !confirmed.contains(Self.normalize(String(text[m.range]))) { continue }
            let alt = Self.freshPick(m.entity, current: String(text[m.range]))
            remember(alt, as: m.entity)
            out += text[last..<m.range.lowerBound]
            out += alt
            last = m.range.upperBound
        }
        out += text[last...]
        return out
    }

    private func lookup(_ text: String, _ lo: String.Index, _ hi: String.Index,
                        _ rawLo: String.Index, _ rawHi: String.Index) -> NameMatch? {
        let cand = Self.normalize(String(text[lo..<hi]))
        if let e = map[cand] { return NameMatch(entity: e, range: lo..<hi, fromMemory: false, ask: askSet.contains(cand)) }
        if !memory.isEmpty {
            var a = rawLo
            while a <= lo {
                var b = hi
                while b <= rawHi {
                    if let e = memory[Self.normalize(String(text[a..<b]))] {
                        return NameMatch(entity: e, range: a..<b, fromMemory: true)
                    }
                    if b == rawHi { break }
                    b = text.index(after: b)
                }
                if a == lo { break }
                a = text.index(after: a)
            }
        }
        for suf in Self.possessives where cand.hasSuffix(suf) {
            if let e = map[String(cand.dropLast(suf.count))] {
                return NameMatch(entity: e, range: lo..<text.index(hi, offsetBy: -suf.count), fromMemory: false,
                                 ask: askSet.contains(String(cand.dropLast(suf.count))))
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
