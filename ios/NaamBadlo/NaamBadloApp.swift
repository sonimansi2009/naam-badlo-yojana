import SwiftUI

@main
struct NaamBadloApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

private let ambedkarBlue = Color(red: 0x14 / 255, green: 0x46 / 255, blue: 0xA0 / 255)
private let blueDeep = Color(red: 0x0B / 255, green: 0x2E / 255, blue: 0x6B / 255)
private let blueTint = Color(red: 0xE8 / 255, green: 0xEE / 255, blue: 0xFA / 255)

struct ContentView: View {
    @State private var sample = ""
    @State private var appeared = false
    private let dict = NameDictionary.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Header band
                VStack(alignment: .leading, spacing: 6) {
                    Text(NameDictionary.appName)
                        .font(.system(size: 30, weight: .bold, design: .serif))
                    Text("नाम छुपाओ योजना")
                        .font(.system(size: 17, design: .serif)).opacity(0.75)
                    Text("Swap ministers' and party names with code names before you post. \(dict.entities.reduce(0) { $0 + $1.alternates.count }) code names for \(dict.entities.count) names. Works offline.")
                        .font(.system(size: 14, design: .serif)).opacity(0.75)
                        .padding(.top, 6)
                }
                .foregroundColor(.white)
                .padding(.horizontal, 22).padding(.vertical, 28)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(blueDeep)

                VStack(spacing: 14) {
                    card {
                        Text("Turn it on").font(.system(size: 19, weight: .bold, design: .serif))
                        step(1, "Open Settings → General → Keyboard → Keyboards → Add New Keyboard.")
                        step(2, "Pick “Naam Chupao Yojana”. Full Access is not needed.")
                        Button {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        } label: {
                            Text("Open Settings")
                                .font(.system(size: 16, weight: .semibold, design: .serif))
                                .frame(maxWidth: .infinity).padding(.vertical, 13)
                                .background(ambedkarBlue).foregroundColor(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        .padding(.top, 4)
                    }
                    .opacity(appeared ? 1 : 0).offset(y: appeared ? 0 : 10)

                    card {
                        Text("Use it").font(.system(size: 19, weight: .bold, design: .serif))
                        step(1, "Type on your usual keyboard.")
                        step(2, "Tap 🌐 to switch to Naam Chupao.")
                        step(3, "Tap “Make it Meta friendly”. Tap again for fresh names. Tap 🌐 to go back.")
                    }
                    .opacity(appeared ? 1 : 0).offset(y: appeared ? 0 : 10)

                    card {
                        Text("Try it here").font(.system(size: 19, weight: .bold, design: .serif))
                        TextEditor(text: $sample)
                            .font(.system(size: 16, design: .serif))
                            .frame(minHeight: 120)
                            .padding(8)
                            .background(blueTint)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .opacity(appeared ? 1 : 0).offset(y: appeared ? 0 : 10)

                    Text("No network access. Nothing you type is stored or sent anywhere.")
                        .font(.system(size: 12.5, design: .serif)).foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.vertical, 8)
                }
                .padding(16)
            }
        }
        .background(Color(red: 0xF5 / 255, green: 0xF7 / 255, blue: 0xFB / 255).ignoresSafeArea())
        .tint(ambedkarBlue)
        .onAppear { withAnimation(.easeOut(duration: 0.35).delay(0.05)) { appeared = true } }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) { content() }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(red: 0xD9 / 255, green: 0xE1 / 255, blue: 0xEF / 255)))
    }

    private func step(_ n: Int, _ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text("\(n)").font(.system(size: 13, weight: .bold, design: .serif))
                .foregroundColor(ambedkarBlue)
                .frame(width: 22, height: 22).background(blueTint).clipShape(Circle())
            Text(text).font(.system(size: 15, design: .serif)).foregroundColor(.primary.opacity(0.85))
        }
    }
}
