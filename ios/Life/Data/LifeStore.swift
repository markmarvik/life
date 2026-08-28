import Foundation

final class LifeStore {
    static let shared = LifeStore()

    private let url: URL
    private let encoder: JSONEncoder
    private let prettyEncoder: JSONEncoder
    private let decoder: JSONDecoder

    private(set) var lastErrorMessage: String?
    var userDismissed = false

    var shouldShowBanner: Bool {
        !userDismissed && !(lastErrorMessage ?? "").isEmpty
    }

    init(fileManager: FileManager = .default) {
        let dir = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        url = dir.appendingPathComponent("app_data.json")
        encoder = JSONEncoder()
        encoder.outputFormatting = []
        prettyEncoder = JSONEncoder()
        prettyEncoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        decoder = JSONDecoder()
    }

    static func defaultData(locale: Locale = .current) -> AppData {
        AppData(currencyCode: Money.defaultCurrencyCode(locale), categories: Seed.financeCategories)
    }

    func load() -> AppData {
        guard FileManager.default.fileExists(atPath: url.path) else {
            lastErrorMessage = nil
            return Self.defaultData()
        }
        do {
            let data = try Data(contentsOf: url)
            if data.isEmpty {
                lastErrorMessage = nil
                return Self.defaultData()
            }
            let parsed = try decoder.decode(AppData.self, from: data)
            lastErrorMessage = nil
            return migrateIfNeeded(parsed)
        } catch {
            lastErrorMessage = error.localizedDescription
            userDismissed = false
            if let raw = try? Data(contentsOf: url) {
                let backup = url.deletingLastPathComponent().appendingPathComponent("corrupt_app_data_backup.json")
                try? raw.write(to: backup, options: .atomic)
            }
            return Self.defaultData()
        }
    }

    func save(_ data: AppData) {
        do {
            let encoded = try encoder.encode(data)
            try encoded.write(to: url, options: .atomic)
            lastErrorMessage = nil
        } catch {
            lastErrorMessage = error.localizedDescription
        }
    }

    func encodeBackup(_ data: AppData, pretty: Bool = true) throws -> String {
        let enc = pretty ? prettyEncoder : encoder
        let bytes = try enc.encode(data)
        return String(data: bytes, encoding: .utf8) ?? "{}"
    }

    func decodeBackup(_ jsonString: String) throws -> AppData {
        guard let bytes = jsonString.data(using: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        let parsed = try decoder.decode(AppData.self, from: bytes)
        return migrateIfNeeded(parsed)
    }

    private func migrateIfNeeded(_ data: AppData) -> AppData {
        var next = data
        if next.categories.isEmpty {
            next.categories = Seed.financeCategories
        }
        if next.schemaVersion < AppData.schemaVersionCurrent {
            next.schemaVersion = AppData.schemaVersionCurrent
        }
        return next
    }

    func dismissBanner() {
        userDismissed = true
    }
}
