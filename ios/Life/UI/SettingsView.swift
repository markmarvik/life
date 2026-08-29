import SwiftUI
import UniformTypeIdentifiers

private let commonCurrencies = ["USD", "EUR", "GBP", "NOK", "SEK", "DKK", "JPY", "CAD", "AUD"]

struct SettingsView: View {
    @EnvironmentObject private var vm: LifeViewModel
    @Environment(\.lifePalette) private var palette
    @State private var openingText: String = ""
    @State private var currency: String = ""
    @State private var status: String?
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var exportDocument = JSONFile(text: "{}")

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    section("Money")
                    TextField("Opening balance", text: $openingText)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(LifeFieldStyle())
                    TextField("Currency", text: $currency)
                        .textInputAutocapitalization(.characters)
                        .textFieldStyle(LifeFieldStyle())
                    FlowRow(spacing: 8) {
                        ForEach(commonCurrencies, id: \.self) { code in
                            TagChip(label: code, selected: currency.caseInsensitiveCompare(code) == .orderedSame) {
                                currency = code
                            }
                        }
                    }
                    Button {
                        let minor = Money.parseToMinor(openingText, currencyCode: currency) ?? 0
                        vm.updateSettings(
                            openingBalanceMinor: minor,
                            currencyCode: currency.isEmpty ? vm.data.currencyCode : currency
                        )
                        status = "Saved"
                    } label: {
                        Text("Save money settings")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderedProminent)
                    .padding(.top, 4)

                    section("Look").padding(.top, 16)
                    Text("Background")
                        .font(.system(size: 12))
                        .foregroundStyle(palette.onSurfaceVariant)
                    FlowRow(spacing: 8) {
                        ForEach(ThemeOptions.backgrounds) { bg in
                            TagChip(label: bg.label, selected: vm.data.backgroundMode == bg.id) {
                                vm.updateSettings(backgroundMode: bg.id)
                            }
                        }
                    }
                    Text("Accent")
                        .font(.system(size: 12))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .padding(.top, 4)
                    FlowRow(spacing: 10) {
                        ForEach(ThemeOptions.accents) { acc in
                            Circle()
                                .fill(Color(argb: acc.colorArgb))
                                .frame(width: 36, height: 36)
                                .overlay {
                                    if vm.data.colorScheme == acc.id {
                                        Circle().stroke(palette.onSurface, lineWidth: 3)
                                    }
                                }
                                .onTapGesture { vm.updateSettings(colorScheme: acc.id) }
                        }
                    }

                    section("On-device AI").padding(.top, 16)
                    Text("Speak or type to log. Pattern matching always works offline, no model pack. Hold the mic for on-device speech (English or Serbian if the language pack is on the phone). Forms stay as a fallback.")
                        .font(.system(size: 13))
                        .foregroundStyle(palette.onSurfaceVariant)

                    section("Reminders").padding(.top, 16)
                    Text("Day before at 20:00 and morning-of at 08:00 for Wants.")
                        .font(.system(size: 13))
                        .foregroundStyle(palette.onSurfaceVariant)
                    Toggle("Want reminders", isOn: Binding(
                        get: { vm.data.wantRemindersEnabled },
                        set: { vm.updateSettings(wantRemindersEnabled: $0) }
                    ))
                    .tint(palette.primary)

                    section("Backup").padding(.top, 16)
                    Text("Everything stays on this phone. Export a JSON file you control.")
                        .font(.system(size: 13))
                        .foregroundStyle(palette.onSurfaceVariant)
                    Button {
                        if let json = try? vm.exportJSON() {
                            exportDocument = JSONFile(text: json)
                            showExporter = true
                        }
                    } label: {
                        Text("Export JSON").frame(maxWidth: .infinity).padding(.vertical, 10)
                    }
                    .buttonStyle(.bordered)
                    Button { showImporter = true } label: {
                        Text("Import JSON").frame(maxWidth: .infinity).padding(.vertical, 10)
                    }
                    .buttonStyle(.bordered)

                    section("About").padding(.top, 16)
                    Text("Life is local-only. No account, no cloud, no ads. Habits, notes, and money on one timeline.")
                        .font(.system(size: 13))
                        .foregroundStyle(palette.onSurfaceVariant)
                    if let status {
                        Text(status)
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(palette.primary)
                            .padding(.top, 8)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        vm.showSettings = false
                    } label: {
                        Image(systemName: "chevron.left")
                    }
                }
            }
            .toolbarBackground(palette.background, for: .navigationBar)
            .fileExporter(
                isPresented: $showExporter,
                document: exportDocument,
                contentType: .json,
                defaultFilename: "life-backup-\(WantDomain.iso(WantDomain.todayComponents()))"
            ) { result in
                switch result {
                case .success: status = "Exported"
                case .failure(let err): status = "Export failed: \(err.localizedDescription)"
                }
            }
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .plainText, .data]) { result in
                switch result {
                case .success(let url):
                    let got = url.startAccessingSecurityScopedResource()
                    defer { if got { url.stopAccessingSecurityScopedResource() } }
                    do {
                        let text = try String(contentsOf: url, encoding: .utf8)
                        try vm.importJSON(text)
                        status = "Imported"
                    } catch {
                        status = "Import failed: \(error.localizedDescription)"
                    }
                case .failure(let err):
                    status = "Import failed: \(err.localizedDescription)"
                }
            }
            .onAppear {
                openingText = Money.formatMajorPlain(vm.data.openingBalanceMinor, currencyCode: vm.data.currencyCode)
                currency = vm.data.currencyCode
            }
        }
    }

    private func section(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(palette.onBackground)
            .padding(.bottom, 4)
    }
}

struct JSONFile: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var text: String

    init(text: String) { self.text = text }

    init(configuration: ReadConfiguration) throws {
        if let data = configuration.file.regularFileContents,
           let s = String(data: data, encoding: .utf8) {
            text = s
        } else {
            text = "{}"
        }
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
