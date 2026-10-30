# Naam Chupao Yojana — नाम छुपाओ योजना

An add-on for your phone keyboard. Type a minister's or party's name, and it offers **50 alternate names** for it: nicknames already used across Instagram, X, Reddit and news comments (Feku, Mota Bhai, Kadi Ninda ji, Reel Mantri), titles (Emperor, Saheb, Supreme Leader) and jumbles (Marendra Nodi, Bodi, Sh4h, B•J•P).

It covers **34 names, 1700 alternates in total**: Narendra Modi, BJP, Amit Shah, Gyanesh Kumar (CEC), the Election Commission, and every Union Cabinet minister. Hindi (Devanagari) and English both work.

- Suggests swaps only for the real names, never for words that are already swapped.
- No accounts, no server, no admins, **no internet permission**. The list lives inside the app.
- Open source (MIT). Anyone can add names and nicknames.

## How it works with your existing keyboard

You keep your normal keyboard (Gboard, SwiftKey, Samsung, Apple). Naam Chupao Yojana works on top of it.

| | Android | iPhone |
|---|---|---|
| **Floating button** (easiest) | Type a name in Instagram, WhatsApp or any app → a **✦ Make it Meta friendly** button appears → tap it, every name in the box is swapped. Tap again for fresh names. Hold to undo, drag to move. Can be limited to Meta apps only. | Not allowed by Apple |
| **Select text** | Select text → ⋮ → **Naam Chupao** → pick each swap → Done | — |
| **Add-on keyboard** | Switch to it → **Make it Meta friendly** or pick from 50 swaps → it jumps back to your keyboard | Tap 🌐 → **Make it Meta friendly** or pick a swap → 🌐 back |

The floating button uses Android's accessibility service, because that is the only way an app can edit the text box of another app. It reads only the box you are typing in, skips password fields, and the app has no internet permission, so nothing leaves the phone. Google Play reviews apps that use this closely, so for now the APK is shared directly.

## Install

### Android
1. Open the [latest release](../../releases/latest) and download `NaamChupaoYojana.apk` (or get it from the **Actions** tab → latest run → *NaamChupaoYojana-apk*).
   If you installed an older test build, **uninstall it first** (old builds used a different signing key).
2. Open the file on your phone. Allow "install from this source" when asked.
3. Open **Naam Chupao Yojana** and tap **Turn on floating button**. In Settings, open Installed apps → Naam Chupao button → On.

### iPhone
Apple only allows apps from the App Store or from a developer account. Until it is on the App Store:
1. On a Mac, install Xcode and run `brew install xcodegen`.
2. `cd ios && xcodegen generate && open NaamBadlo.xcodeproj`
3. Pick your Apple ID under *Signing & Capabilities* for both targets, plug in your phone, press ▶.
4. On the phone: Settings → General → Keyboard → Keyboards → Add New Keyboard → **Naam Chupao Yojana**. Full Access is **not** needed.

A free Apple ID works but the app expires after 7 days. A paid developer account ($99/year) gives a year, TestFlight and the App Store.

## Add or change names

Everything is in [`dictionary/source.json`](dictionary/source.json):

```json
{
  "id": "modi",
  "label": "Narendra Modi",
  "first": "Narendra", "last": "Modi",
  "triggers": ["modi", "narendra modi", "मोदी"],
  "curated": ["Odiji", "Emperor", "Feku", "Bodi"]
}
```

- **triggers** — the real names to watch for (all spellings, Hindi and English).
- **curated** — hand-picked nicknames. Put the best ones first.
- The script fills up to exactly 50 with jumbles (spoonerisms, letter swaps, look-alike digits, dots and dashes). Use `"key"` to choose which word gets jumbled, `"acronym"` for party/body names.

Then run:

```
python3 dictionary/build.py
```

It copies the new list into both apps. The script never suggests a real name back, removes duplicates, and drops jumbles that accidentally make Hindi swear words. Open a pull request and the GitHub Actions build makes a fresh APK.

## Current list (first 5 of 50 each)

| Name | Alternates |
|---|---|
| Narendra Modi | Odiji, Emperor, King, Chota Bandar, Marendra Nodi … |
| Amit Shah | Mota Bhai, Tadipaar, Chanakya, Chhote Saheb, Number Two … |
| BJP | Kamal Dal, Lotus Party, Kamal wale, Bhagwa Party, Saffron Party … |
| Gyanesh Kumar (CEC) | Gyani Baba, Gyan Babu, Chunav Babu, CEC Saheb, Mukhya Ayukt ji … |
| Election Commission of India | Ayog ji, Commission ji, Syahi Dept, Neeli Syahi wale, Ungli Daftar … |
| Rajnath Singh | Raksha Babu, Defence Dadu, Kadi Ninda ji, Ninda Mantri, RS ji … |
| Nitin Gadkari | Highway Man, Road wale Baba, Flyover Kaka, Toll Babu, Nagpur wale Bhau … |
| J. P. Nadda | Health Babu, Dawai Mantri, Khad Babu, J.P. ji, Hospital Babu … |
| Shivraj Singh Chouhan | Mama ji, Mamaji, Kisan Mama, Bhanje wale Mama, Khet Babu … |
| Nirmala Sitharaman | FM, FM Didi, Tax Didi, Budget Amma, Onion wali Madam … |
| S. Jaishankar | EAM, Videsh Babu, Diplomat ji, Passport Babu, Jai Sir … |
| Manohar Lal Khattar | Bijli Babu, Ghar Babu, Haryana wale Tau, Power Tau, Kaka ji … |
| H. D. Kumaraswamy | HDK, Kumaranna, Steel Babu, Bhari Udyog Babu, Anna of Steel … |
| Piyush Goyal | Vyapar Babu, Commerce Babu, Dukaan Babu, Trade Seth, Export Babu … |
| Dharmendra Pradhan | Pariksha Babu, NEET wale Babu, Paper Babu, Shiksha Babu, Exam Chief … |
| Jitan Ram Manjhi | MSME Baba, Laghu Udyog Babu, Gaya wale Baba, Mitan Janjhi, Banjhi … |
| Lalan Singh | Machhli Babu, Dairy Babu, Panchayat Babu, Munger wale Babu, Salan Lingh … |
| Sarbananda Sonowal | Jahaaz Babu, Bandargah Babu, Port Babu, Assam wale Dada, Bonowal … |
| Virendra Kumar | Nyay Babu, Samajik Nyay ji, Tikamgarh wale Babu, Kirendra Vumar, Birendra … |
| Ram Mohan Naidu | Udaan Babu, Aviation Babu, RMN, Hawai Babu, Flight Babu … |
| Pralhad Joshi | Ration Babu, Solar Babu, Urja Babu, Dharwad wale Babu, Jalhad Proshi … |
| Jual Oram | Janjati Babu, Tribal Babu, Sundargarh wale Babu, Ual Joram, Boram … |
| Giriraj Singh | Kapda Babu, Textile Babu, Pakistan Visa wale, Visa Babu, Begusarai wale Babu … |
| Ashwini Vaishnaw | Reel Mantri, Rail Babu, Reel Babu, Train Babu, IT Babu … |
| Jyotiraditya Scindia | Maharaj, Shrimant, Gwalior Maharaj, Telecom Raja, Northeast Babu … |
| Bhupender Yadav | Jungle Babu, Paryavaran Babu, Climate Babu, Alwar wale Babu, Yupender Bhadav … |
| Gajendra Singh Shekhawat | Sanskriti Babu, Paryatan Babu, Tourism Babu, Jodhpur wale Babu, Shajendra Gekhawat … |
| Annpurna Devi | Mahila Babu, Bal Vikas Didi, Koderma wali Didi, Dannpurna Evi, Bannpurna … |
| Kiren Rijiju | Sansad Babu, Arunachal wale Bhai, Minority Babu, Parliament Babu, Riren Kijiju … |
| Hardeep Singh Puri | Petrol Babu, Tel Mantri, Pump Babu, Gas Babu, Diesel Babu … |
| Mansukh Mandaviya | Cycle Mantri, Khel Babu, Shram Babu, Naukri Babu, Sports Babu … |
| G. Kishan Reddy | Koyla Babu, Khadan Babu, Coal Babu, Hyderabad wale Anna, Rishan Keddy … |
| Chirag Paswan | Saheb ka Hanuman, Khana Babu, Food Processing Babu, Bihari Hanuman, Pirag Chaswan … |
| C. R. Patil | Jal Babu, Paani Babu, Nal Babu, Navsari wale Bhau, Pandrakant Chatil … |

Cabinet as of October 2026 (Third Modi ministry). Dharmendra Pradhan is kept in the list even though reports say he resigned in July 2026; remove him or add new ministers in `source.json` after any reshuffle.

## Project layout

```
dictionary/   source.json (edit this), build.py, names.json (generated)
android/      Kotlin app: add-on keyboard + "select text" action, no internet permission
ios/          Swift keyboard extension + setup app (XcodeGen project.yml)
.github/      CI: builds the APK on every push, attaches it to releases (tag v0.1.0 etc.)
```

## Signing

Every build is signed with the same key (`android/keystore/naam-chupao.jks`), so new versions install over old ones. That key is in the repo, which means anyone could sign an app that installs over this one. Before sharing widely, make a private key and add it as GitHub secrets (`NC_KEYSTORE_B64`, `NC_KEYSTORE_PASSWORD`, `NC_KEY_ALIAS`, `NC_KEY_PASSWORD`); the build uses it automatically. Users then reinstall once.

## A note on use

Swapped names make posts harder for simple keyword filters to catch. They do not change what a post means, and the law treats a decoded post the same as the original.

## Licence

MIT
