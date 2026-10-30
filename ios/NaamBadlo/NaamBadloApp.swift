import SwiftUI

@main
struct NaamBadloApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

struct ContentView: View {
    @State private var sample = ""
    private let dict = NameDictionary.shared

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("\(dict.entities.count) names · \(dict.entities.reduce(0) { $0 + $1.alternates.count }) alternates · works offline")
                        .font(.footnote).foregroundColor(.secondary)

                    section("Turn it on", [
                        "Open Settings → General → Keyboard → Keyboards → Add New Keyboard.",
                        "Pick “Naam Badlo”. No “Full Access” is needed.",
                    ])
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                    .buttonStyle(.borderedProminent)

                    section("Use it", [
                        "Type on your usual keyboard.",
                        "After a name, tap 🌐 to switch to Naam Badlo.",
                        "Tap a swap, or “Sab badlo” to swap every name. Tap 🌐 to go back.",
                    ])

                    Text("Try it here").font(.headline)
                    TextEditor(text: $sample)
                        .frame(minHeight: 120)
                        .padding(8)
                        .background(Color(.secondarySystemBackground))
                        .cornerRadius(10)

                    Text("This keyboard has no network access. It cannot see, store or send what you type anywhere.")
                        .font(.footnote).foregroundColor(.secondary)
                }
                .padding()
            }
            .navigationTitle("Naam Badlo")
        }
    }

    private func section(_ title: String, _ steps: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.headline)
            ForEach(Array(steps.enumerated()), id: \.offset) { i, s in
                Text("\(i + 1). \(s)")
            }
        }
    }
}
