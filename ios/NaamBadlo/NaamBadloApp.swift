import SwiftUI

@main
struct NaamBadloApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

private let ambedkarBlue = Color(Brand.blue)
private let navy = Color(Brand.navy)
private let azure = Color(Brand.azure)
private let blueTint = Color(Brand.tint)
private let brandGradient = LinearGradient(colors: [navy, ambedkarBlue, azure], startPoint: .topLeading, endPoint: .bottomTrailing)
private let buttonGradient = LinearGradient(colors: [ambedkarBlue, azure], startPoint: .topLeading, endPoint: .bottomTrailing)

private func display(_ size: CGFloat) -> Font { Brand.registerFonts(); return .custom("PlayfairDisplay-Bold", size: size) }
private func lora(_ size: CGFloat, bold: Bool = false) -> Font { Brand.registerFonts(); return .custom(bold ? "Lora-SemiBold" : "Lora-Regular", size: size) }

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
                        .font(display(34))
                    Text("नाम छुपाओ योजना")
                        .font(lora(17)).opacity(0.75)
                    Text("Swap ministers' and party names with code names before you post. \(dict.entities.reduce(0) { $0 + $1.alternates.count }) code names for \(dict.entities.count) names. Works offline.")
                        .font(lora(14)).opacity(0.75)
                        .padding(.top, 6)
                }
                .foregroundColor(.white)
                .padding(.horizontal, 22).padding(.vertical, 28)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(brandGradient)

                VStack(spacing: 14) {
                    card {
                        Text("Turn it on").font(display(22))
                        step(1, "Open Settings → General → Keyboard → Keyboards → Add New Keyboard.")
                        step(2, "Pick “Naam Chupao Yojana”. Full Access is not needed.")
                        Button {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        } label: {
                            Text("Open Settings")
                                .font(lora(16, bold: true))
                                .frame(maxWidth: .infinity).padding(.vertical, 13)
                                .background(buttonGradient).foregroundColor(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        .padding(.top, 4)
                    }
                    .opacity(appeared ? 1 : 0).offset(y: appeared ? 0 : 10)

                    card {
                        Text("Use it").font(display(22))
                        step(1, "Type on your usual keyboard.")
                        step(2, "Tap 🌐 to switch to Naam Chupao.")
                        step(3, "Tap “Make it Meta friendly”. Tap again for fresh names. Tap 🌐 to go back.")
                    }
                    .opacity(appeared ? 1 : 0).offset(y: appeared ? 0 : 10)

                    card {
                        Text("Try it here").font(display(22))
                        TextEditor(text: $sample)
                            .font(lora(16))
                            .frame(minHeight: 120)
                            .padding(8)
                            .background(blueTint)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .opacity(appeared ? 1 : 0).offset(y: appeared ? 0 : 10)

                    Text("No network access. Nothing you type is stored or sent anywhere.")
                        .font(lora(12.5)).foregroundColor(.secondary)
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
            Text("\(n)").font(lora(13, bold: true))
                .foregroundColor(ambedkarBlue)
                .frame(width: 22, height: 22).background(blueTint).clipShape(Circle())
            Text(text).font(lora(15)).foregroundColor(.primary.opacity(0.85))
        }
    }
}
