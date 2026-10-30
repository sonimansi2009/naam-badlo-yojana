import SwiftUI

@main
struct NaamBadloApp: App {
    init() { Brand.registerFonts() }
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

private let ink = Color(Brand.ink)
private let muted = Color(Brand.muted)
private let soft = Color(Brand.soft)
private let line = Color(Brand.line)
private let black = Color(Brand.black)
private let metaBlue = Color(Brand.meta)

private func sans(_ size: CGFloat, _ w: Brand.Weight = .regular) -> Font {
    switch w {
    case .regular: return .custom("InstrumentSans-Regular", size: size)
    case .medium: return .custom("InstrumentSans-Medium", size: size)
    case .semibold: return .custom("InstrumentSans-SemiBold", size: size)
    }
}
private func serifItalic(_ size: CGFloat) -> Font { .custom("InstrumentSerif-Italic", size: size) }

struct ContentView: View {
    enum Tab: String, CaseIterable { case setup = "Set up", use = "How to use", privacy = "Privacy" }
    @State private var tab: Tab = .setup
    @State private var sample = ""
    @State private var appeared = false
    private let dict = NameDictionary.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Headline with one italic word
                (Text("Naam ").font(sans(48, .medium))
                 + Text("Chupao").font(serifItalic(52))
                 + Text("\nYojana").font(sans(48, .medium)))
                    .foregroundColor(ink)
                    .tracking(-1.2)
                    .lineSpacing(-6)
                    .padding(.top, 24)
                Text("नाम छुपाओ योजना · \(dict.entities.reduce(0) { $0 + $1.alternates.count }) code names for \(dict.entities.count) names")
                    .font(sans(14.5)).foregroundColor(muted)

                // Pill tabs
                HStack(spacing: 8) {
                    ForEach(Tab.allCases, id: \.self) { t in
                        Button { withAnimation(.easeOut(duration: 0.25)) { tab = t } } label: {
                            Text(t.rawValue).font(sans(14, .medium))
                                .padding(.horizontal, 18).padding(.vertical, 10)
                                .background(tab == t ? black : soft)
                                .foregroundColor(tab == t ? .white : ink)
                                .clipShape(Capsule())
                        }
                    }
                }

                card {
                    switch tab {
                    case .setup:
                        header("keyboard", "Turn it on", "Takes 20 seconds")
                        tags(["No Full Access", "Offline", "Hindi + English"])
                        panel {
                            step(1, "Settings → General → Keyboard → Keyboards.")
                            step(2, "Add New Keyboard → Naam Chupao Yojana.")
                            HStack { Spacer(); cta("Open Settings") {
                                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                            } }.padding(.top, 8)
                        }
                    case .use:
                        header("sparkles", "Make it Meta friendly", "One tap swaps every name")
                        tags(["One tap", "Fresh names", "50 per name"])
                        panel {
                            step(1, "Type on your usual keyboard.")
                            step(2, "Tap 🌐 to switch to Naam Chupao.")
                            step(3, "Tap the blue button. Tap again for fresh names.")
                        }
                    case .privacy:
                        header("lock.shield", "Your privacy", "Kept simple")
                        panel {
                            step(1, "No network access. Nothing leaves your phone.")
                            step(2, "Your text is not stored.")
                            step(3, "Full Access is never needed.")
                        }
                    }
                }
                .id(tab)
                .transition(.opacity.combined(with: .offset(y: 8)))

                card {
                    Text("Try it here").font(sans(17, .semibold)).foregroundColor(ink)
                    TextEditor(text: $sample)
                        .font(sans(16))
                        .frame(minHeight: 120)
                        .padding(10)
                        .background(soft)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 40)
            .opacity(appeared ? 1 : 0).offset(y: appeared ? 0 : 12)
        }
        .background(LinearGradient(colors: [Color(Brand.bgTop), Color(Brand.bg)], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        .onAppear { withAnimation(.easeOut(duration: 0.4)) { appeared = true } }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) { content() }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
    }

    private func panel<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) { content() }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(soft)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private func header(_ icon: String, _ title: String, _ sub: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 17, weight: .medium)).foregroundColor(ink)
                .frame(width: 44, height: 44).background(soft).clipShape(Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(sans(16.5, .semibold)).foregroundColor(ink)
                Text(sub).font(sans(13)).foregroundColor(muted)
            }
        }
        .padding(.horizontal, 4).padding(.top, 4)
    }

    private func tags(_ items: [String]) -> some View {
        HStack(spacing: 6) {
            ForEach(items, id: \.self) { t in
                Text(t).font(sans(12.5)).foregroundColor(ink)
                    .padding(.horizontal, 12).padding(.vertical, 5)
                    .overlay(Capsule().stroke(line))
            }
        }
        .padding(.horizontal, 2)
    }

    private func step(_ n: Int, _ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text("\(n)").font(sans(12, .semibold)).foregroundColor(ink)
                .frame(width: 24, height: 24).background(Color.white).clipShape(Circle())
            Text(text).font(sans(15)).foregroundColor(ink)
        }
    }

    private func cta(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(title).font(sans(15.5, .semibold)).foregroundColor(.white)
                Image(systemName: "arrow.right").font(.system(size: 14, weight: .bold)).foregroundColor(metaBlue)
                    .frame(width: 38, height: 38).background(Color.white).clipShape(Circle())
            }
            .padding(.leading, 20).padding(.trailing, 6).padding(.vertical, 6)
            .background(metaBlue).clipShape(Capsule())
        }
    }
}
