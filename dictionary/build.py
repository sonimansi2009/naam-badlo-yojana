#!/usr/bin/env python3
"""Builds names.json from source.json.

Code names are always readable words: nicknames, sound-alikes and emoji versions.
No letter tricks like B-J-P, M.o.d.i or R3ddy (they look like evasion and are hard to read).

"popular" = how many of the first alternates are hand-picked, recognisable code names.
"ask" = triggers that are only a first name or a common surname; the app asks before swapping them.

Every entity ends up with exactly 50 alternates:
  1. the hand-picked "curated" list first,
  2. emoji versions of the curated ones (Kamal Party 🪷), mixed in,
  3. then sound-alike jumbles of the name (spoonerisms, letter swaps, vowel swaps).
Acronyms (BJP, ECI, CJP) get no jumbles: only curated names and emoji versions.

Rules applied to every alternate:
  - never equal to, and never containing, any real trigger word
    (so the keyboard never suggests the real name back),
  - no duplicates (case-insensitive),
  - generated jumbles that accidentally form common Hindi swear words are dropped.

Output is copied into the Android and iOS projects.
Usage: python3 dictionary/build.py
"""
import json
import re
import shutil
from pathlib import Path

TARGET = 50
ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
OUTPUTS = [
    ROOT / "names.json",
    REPO / "android/app/src/main/assets/names.json",
    REPO / "ios/Shared/names.json",
]

VOWELS = set("aeiou")
SWAP_ONSETS = ["B", "G", "J", "K", "L", "N", "P", "R", "S", "T", "V", "H", "D", "F", "Y", "Bh", "Ch"]
# Generated jumbles containing these are dropped (accidental profanity).
BLOCK = ["chod", "chut", "lund", "lun ", "gand", "gaand", "bhos", "randi", "madar",
         "behen", "bsdk", "kutt", "suar", "harami", "lauda", "loda", "lodu", "jhaat", "tatt", "ganesh", "shiv"]
LETTER_NAMES = {
    "A": "Ay", "B": "Bee", "C": "See", "D": "Dee", "E": "Ee", "F": "Eff", "G": "Jee",
    "H": "Aitch", "I": "Eye", "J": "Jay", "K": "Kay", "L": "Ell", "M": "Em", "N": "En",
    "O": "Oh", "P": "Pee", "Q": "Kyu", "R": "Aar", "S": "Ess", "T": "Tee", "U": "You",
    "V": "Vee", "W": "Double-U", "X": "Ex", "Y": "Why", "Z": "Zed",
}


def norm(s: str) -> str:
    return re.sub(r"\s+", " ", s.lower()).strip()


def onset(w: str):
    """Split a word into (leading consonants, rest)."""
    i = 0
    while i < len(w) and w[i].lower() not in VOWELS:
        i += 1
    return w[:i], w[i:]


def cap(w: str) -> str:
    return w[:1].upper() + w[1:].lower() if w else w


def name_jumbles(first: str, last: str, key: str = None, with_first: bool = False):
    """first/last = real name; key = the word to jumble (defaults to surname)."""
    out = []
    f_on, f_rest = onset(first)
    s_on, s_rest = onset(last)
    has_vowel = any(c.lower() in VOWELS for c in first)

    # Spoonerism: Narendra Modi -> Marendra Nodi
    if has_vowel and (f_on or s_on) and f_on.lower() != s_on.lower():
        out.append(f"{cap(s_on + f_rest)} {cap(f_on + s_rest)}")

    last = key or last
    l_on, l_rest = onset(last)
    short = len(last) <= 4

    # Consonant swaps on the surname: Modi -> Bodi, Godi, Nodi ...
    # Short surnames (Shah, Puri) get the first name in front so they stay readable
    for o in SWAP_ONSETS:
        if o.lower() != l_on.lower():
            sw = cap(o + l_rest)
            out.append(f"{cap(first)} {sw}" if with_first else sw)

    # Vowel swaps: Modi -> Madi, Medi, Mudi; Moda, Mode, Modu
    idx = [i for i, c in enumerate(last) if c.lower() in VOWELS]
    if idx:
        a = idx[0]
        for v in "aeiu o".replace(" ", ""):
            if v != last[a].lower():
                out.append(cap(last[:a] + v + last[a + 1:]))
        b = idx[-1]
        if b != a:
            for v in "aeu":
                if v != last[b].lower():
                    out.append(cap(last[:b] + v + last[b + 1:]))

    L = cap(last)
    # Doubling and stretching
    if len(L) >= 3:
        out.append(L[:-1] + L[-2].lower() + L[-1].lower())   # Moddi
        out.append(L + L[-1].lower())                          # Modii
    if idx:
        a = idx[0]
        out.append(L[:a + 1] + L[a].lower() * 2 + L[a + 1:])  # Mooodi

    # First-name side
    if has_vowel:
        F = cap(first)
        out.append(F[:2] + L[:2].capitalize())               # NaMo-style blend

    # Honorific suffixes on the swaps, used as top-up
    base = [cap(o + l_rest) for o in SWAP_ONSETS[:8] if o.lower() != l_on.lower()]
    for b in base:
        for suf in [" ji", " Saheb", " Babu"]:
            out.append(b + suf)
    return out


def acronym_jumbles(ac: str):
    ac = ac.upper()
    letters = list(ac)
    names = [LETTER_NAMES[c] for c in letters]
    out = [
        ".".join(letters), ".".join(letters) + ".", " ".join(letters), "-".join(letters),
        "_".join(letters), "/".join(letters), "|".join(letters), "•".join(letters),
        "".join(f"({c})" for c in letters), ac[::-1], ".".join(ac[::-1]),
        " ".join(names), "-".join(names), "".join(names), " ".join(n.lower() for n in names),
        ac.lower().swapcase() if False else ac[0].lower() + ac[1:],
        ac[:-1].lower() + ac[-1], ac[0] + ac[1:].lower(),
        ".".join(c.lower() for c in letters) + ".",
        " . ".join(letters), "~".join(letters), "*".join(letters),
    ]
    for i in range(len(letters)):
        out.append(" ".join(names[:i] + [letters[i]] + names[i + 1:]))
    return out


def build():
    src = json.loads((ROOT / "source.json").read_text(encoding="utf-8"))
    ents = src["entities"]
    all_triggers = set()
    for e in ents:
        for t in e["triggers"]:
            all_triggers.add(norm(t))

    def contains_trigger(alt: str) -> bool:
        a = " " + norm(re.sub(r"[.\-_/|•()*~]", " ", alt)) + " "
        a2 = " " + norm(alt) + " "
        for t in all_triggers:
            if f" {t} " in a or f" {t} " in a2:
                return True
        return False

    out_entities = []
    report = []
    for e in ents:
        seen, alts = set(), []

        def add(a: str, generated: bool):
            a = a.strip()
            k = norm(a)
            if not a or k in seen or k in all_triggers or contains_trigger(a):
                return
            if generated and any(b in k for b in BLOCK):
                return
            seen.add(k)
            alts.append(a)

        curated = e.get("curated", [])
        emojis = e.get("emoji", [])
        latin = [c for c in curated if re.search(r"[A-Za-z]", c) and not re.search(r"[^\x00-\x7F]", c)]
        n_emoji = max(8, TARGET - len(curated) + 4) if "acronym" in e else 8
        emoji_alts = [f"{latin[i % len(latin)]} {emojis[i % len(emojis)]}" for i in range(min(n_emoji, len(latin)))] if emojis and latin else []
        # mix: after every third curated name, one emoji version
        merged, q = [], list(emoji_alts)
        for i, c in enumerate(curated):
            merged.append(c)
            if i % 3 == 2 and q:
                merged.append(q.pop(0))
        merged += q
        for a in merged:
            add(a, False)
        popular = min(len(alts), TARGET)   # hand-picked, recognisable code names come first
        gen = [] if "acronym" in e else name_jumbles(e["first"], e["last"], e.get("key"), e.get("with_first", False))
        for a in gen:
            if len(alts) >= TARGET:
                break
            add(a, True)
        if len(alts) < TARGET:
            raise SystemExit(f"{e['id']}: only {len(alts)} alternates, add more curated ones")
        alts = alts[:TARGET]
        ask = [t for t in e.get("ask", []) if norm(t) in {norm(x) for x in e["triggers"]}]
        out_entities.append({"id": e["id"], "label": e["label"],
                             "triggers": e["triggers"], "alternates": alts,
                             "popular": popular, "ask": ask})
        report.append((e["id"], len(e.get("curated", [])), len(alts)))

    data = {"version": src["version"], "updated": src["updated"], "entities": out_entities}
    text = json.dumps(data, ensure_ascii=False, indent=1)
    for p in OUTPUTS:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text + "\n", encoding="utf-8")
    for r in report:
        print(f"{r[0]:<18} curated {r[1]:>2}  total {r[2]}")
    print(f"{len(out_entities)} names, {sum(r[2] for r in report)} alternates")


if __name__ == "__main__":
    build()
