import Foundation

enum EventKind: String, Codable, Equatable, CaseIterable {
    case habit = "HABIT"
    case note = "NOTE"
    case finance = "FINANCE"
}

enum MoneyDirection: String, Codable, Equatable {
    case income = "IN"
    case expense = "OUT"
}

enum WantStatus: String, Codable, Equatable {
    case none = "NONE"
    case open = "OPEN"
    case done = "DONE"
    case canceled = "CANCELED"
    case missed = "MISSED"
}

struct HabitDef: Codable, Equatable, Identifiable {
    var id: String
    var name: String
    var icon: String = ""
    var why: String = ""
    var archived: Bool = false
    var createdAt: Int64 = 0

    init(
        id: String,
        name: String,
        icon: String = "",
        why: String = "",
        archived: Bool = false,
        createdAt: Int64 = 0
    ) {
        self.id = id
        self.name = name
        self.icon = icon
        self.why = why
        self.archived = archived
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        icon = try c.decodeIfPresent(String.self, forKey: .icon) ?? ""
        why = try c.decodeIfPresent(String.self, forKey: .why) ?? ""
        archived = try c.decodeIfPresent(Bool.self, forKey: .archived) ?? false
        createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
    }
}

struct LifeEvent: Codable, Equatable, Identifiable {
    var id: String
    var kind: EventKind
    var createdAt: Int64
    var habitId: String? = nil
    var body: String = ""
    var tags: [String] = []
    var amountMinor: Int64? = nil
    var direction: MoneyDirection? = nil
    var payee: String = ""
    var category: String = ""
    var dueDate: String? = nil
    var isWant: Bool = false
    var wantStatus: WantStatus = .none
    var reflection: String = ""
    var resolvedAt: Int64? = nil

    init(
        id: String,
        kind: EventKind,
        createdAt: Int64,
        habitId: String? = nil,
        body: String = "",
        tags: [String] = [],
        amountMinor: Int64? = nil,
        direction: MoneyDirection? = nil,
        payee: String = "",
        category: String = "",
        dueDate: String? = nil,
        isWant: Bool = false,
        wantStatus: WantStatus = .none,
        reflection: String = "",
        resolvedAt: Int64? = nil
    ) {
        self.id = id
        self.kind = kind
        self.createdAt = createdAt
        self.habitId = habitId
        self.body = body
        self.tags = tags
        self.amountMinor = amountMinor
        self.direction = direction
        self.payee = payee
        self.category = category
        self.dueDate = dueDate
        self.isWant = isWant
        self.wantStatus = wantStatus
        self.reflection = reflection
        self.resolvedAt = resolvedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        kind = try c.decode(EventKind.self, forKey: .kind)
        createdAt = try c.decode(Int64.self, forKey: .createdAt)
        habitId = try c.decodeIfPresent(String.self, forKey: .habitId)
        body = try c.decodeIfPresent(String.self, forKey: .body) ?? ""
        tags = try c.decodeIfPresent([String].self, forKey: .tags) ?? []
        amountMinor = try c.decodeIfPresent(Int64.self, forKey: .amountMinor)
        direction = try c.decodeIfPresent(MoneyDirection.self, forKey: .direction)
        payee = try c.decodeIfPresent(String.self, forKey: .payee) ?? ""
        category = try c.decodeIfPresent(String.self, forKey: .category) ?? ""
        dueDate = try c.decodeIfPresent(String.self, forKey: .dueDate)
        isWant = try c.decodeIfPresent(Bool.self, forKey: .isWant) ?? false
        wantStatus = try c.decodeIfPresent(WantStatus.self, forKey: .wantStatus) ?? .none
        reflection = try c.decodeIfPresent(String.self, forKey: .reflection) ?? ""
        resolvedAt = try c.decodeIfPresent(Int64.self, forKey: .resolvedAt)
    }

    var isOpenWant: Bool { isWant && wantStatus == .open }

    var timelineAt: Int64 {
        if isWant && wantStatus != .none && wantStatus != .open {
            return resolvedAt ?? createdAt
        }
        return createdAt
    }
}

enum Seed {
    static let noteTags = ["Ideas", "Thoughts", "Work", "Health", "Gratitude"]
    static let financeCategories = [
        "Food", "Transport", "Rent", "Groceries", "Health",
        "Fun", "Shopping", "Bills", "Salary", "Other"
    ]
}

struct AppData: Codable, Equatable {
    static let schemaVersionCurrent = 2

    var schemaVersion: Int = schemaVersionCurrent
    var habits: [HabitDef] = []
    var events: [LifeEvent] = []
    var customTags: [String] = []
    var payees: [String] = []
    var categories: [String] = Seed.financeCategories
    var openingBalanceMinor: Int64 = 0
    var currencyCode: String = "USD"
    var colorScheme: String = "default"
    var backgroundMode: String = "dark"
    var wantStrikeStreak: Int = 0
    var wantRemindersEnabled: Bool = true

    init(
        schemaVersion: Int = schemaVersionCurrent,
        habits: [HabitDef] = [],
        events: [LifeEvent] = [],
        customTags: [String] = [],
        payees: [String] = [],
        categories: [String] = Seed.financeCategories,
        openingBalanceMinor: Int64 = 0,
        currencyCode: String = "USD",
        colorScheme: String = "default",
        backgroundMode: String = "dark",
        wantStrikeStreak: Int = 0,
        wantRemindersEnabled: Bool = true
    ) {
        self.schemaVersion = schemaVersion
        self.habits = habits
        self.events = events
        self.customTags = customTags
        self.payees = payees
        self.categories = categories
        self.openingBalanceMinor = openingBalanceMinor
        self.currencyCode = currencyCode
        self.colorScheme = colorScheme
        self.backgroundMode = backgroundMode
        self.wantStrikeStreak = wantStrikeStreak
        self.wantRemindersEnabled = wantRemindersEnabled
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try c.decodeIfPresent(Int.self, forKey: .schemaVersion) ?? AppData.schemaVersionCurrent
        habits = try c.decodeIfPresent([HabitDef].self, forKey: .habits) ?? []
        events = try c.decodeIfPresent([LifeEvent].self, forKey: .events) ?? []
        customTags = try c.decodeIfPresent([String].self, forKey: .customTags) ?? []
        payees = try c.decodeIfPresent([String].self, forKey: .payees) ?? []
        categories = try c.decodeIfPresent([String].self, forKey: .categories) ?? Seed.financeCategories
        openingBalanceMinor = try c.decodeIfPresent(Int64.self, forKey: .openingBalanceMinor) ?? 0
        currencyCode = try c.decodeIfPresent(String.self, forKey: .currencyCode) ?? "USD"
        colorScheme = try c.decodeIfPresent(String.self, forKey: .colorScheme) ?? "default"
        backgroundMode = try c.decodeIfPresent(String.self, forKey: .backgroundMode) ?? "dark"
        wantStrikeStreak = try c.decodeIfPresent(Int.self, forKey: .wantStrikeStreak) ?? 0
        wantRemindersEnabled = try c.decodeIfPresent(Bool.self, forKey: .wantRemindersEnabled) ?? true
    }

    func balanceMinor() -> Int64 {
        var total = openingBalanceMinor
        for e in events {
            guard e.kind == .finance else { continue }
            let a = e.amountMinor ?? 0
            total += e.direction == .income ? a : -a
        }
        return total
    }

    func habitById(_ id: String) -> HabitDef? {
        habits.first { $0.id == id }
    }

    func findHabitByName(_ name: String) -> HabitDef? {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if n.isEmpty { return nil }
        return habits.first { !$0.archived && $0.name.caseInsensitiveCompare(n) == .orderedSame }
    }

    func withEvent(_ event: LifeEvent) -> AppData {
        var next = self
        next.events = [event] + events.filter { $0.id != event.id }
        return next
    }

    func withoutEvent(_ id: String) -> AppData {
        var next = self
        next.events = events.filter { $0.id != id }
        return next
    }

    func withHabit(_ habit: HabitDef) -> AppData {
        var next = self
        next.habits = habits.filter { $0.id != habit.id } + [habit]
        return next
    }

    func withPayee(_ name: String) -> AppData {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if n.isEmpty { return self }
        if payees.contains(where: { $0.caseInsensitiveCompare(n) == .orderedSame }) { return self }
        var next = self
        next.payees = [n] + payees
        return next
    }

    func withCategory(_ name: String) -> AppData {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if n.isEmpty { return self }
        if categories.contains(where: { $0.caseInsensitiveCompare(n) == .orderedSame }) { return self }
        var next = self
        next.categories = categories + [n]
        return next
    }

    func withTag(_ name: String) -> AppData {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if n.isEmpty { return self }
        if Seed.noteTags.contains(where: { $0.caseInsensitiveCompare(n) == .orderedSame }) { return self }
        if customTags.contains(where: { $0.caseInsensitiveCompare(n) == .orderedSame }) { return self }
        var next = self
        next.customTags = customTags + [n]
        return next
    }

    func availableTags() -> [String] {
        var used = Set<String>()
        for e in events where e.kind == .note {
            for t in e.tags {
                let trimmed = t.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { used.insert(trimmed) }
            }
        }
        var all: [String] = []
        var seen = Set<String>()
        func add(_ s: String) {
            let key = s.lowercased()
            if seen.contains(key) { return }
            seen.insert(key)
            all.append(s)
        }
        Seed.noteTags.forEach(add)
        customTags.forEach(add)
        used.sorted().forEach(add)
        return all
    }
}

struct DayGroup: Equatable {
    var date: String
    var label: String
    var events: [LifeEvent]
}

func nowMs() -> Int64 {
    Int64(Date().timeIntervalSince1970 * 1000)
}

func dateFromMs(_ ms: Int64) -> Date {
    Date(timeIntervalSince1970: TimeInterval(ms) / 1000.0)
}

func startOfDay(_ date: Date, calendar: Calendar) -> Date {
    calendar.startOfDay(for: date)
}

func isoDateString(_ date: Date, timeZone: TimeZone = .current) -> String {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = timeZone
    let c = cal.dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
}

func parseIsoDate(_ s: String, timeZone: TimeZone = TimeZone(secondsFromGMT: 0)!) -> Date? {
    let parts = s.split(separator: "-")
    guard parts.count == 3,
          let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]) else { return nil }
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = timeZone
    return cal.date(from: DateComponents(year: y, month: m, day: d))
}

func groupEvents(
    _ events: [LifeEvent],
    nowMs now: Int64 = nowMs(),
    timeZone: TimeZone = .current
) -> [DayGroup] {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = timeZone
    let today = cal.startOfDay(for: dateFromMs(now))
    let yesterday = cal.date(byAdding: .day, value: -1, to: today)!
    let fmt = DateFormatter()
    fmt.locale = .current
    fmt.timeZone = timeZone
    fmt.dateFormat = "EEE d MMM"

    let visible = events.filter { !$0.isOpenWant }.sorted { $0.timelineAt > $1.timelineAt }
    var buckets: [(Date, [LifeEvent])] = []
    var index: [Date: Int] = [:]
    for e in visible {
        let day = cal.startOfDay(for: dateFromMs(e.timelineAt))
        if let i = index[day] {
            buckets[i].1.append(e)
        } else {
            index[day] = buckets.count
            buckets.append((day, [e]))
        }
    }
    buckets.sort { $0.0 > $1.0 }
    return buckets.map { day, evs in
        let label: String
        if day == today {
            label = "Today"
        } else if day == yesterday {
            label = "Yesterday"
        } else {
            label = fmt.string(from: day)
        }
        return DayGroup(
            date: isoDateString(day, timeZone: timeZone),
            label: label,
            events: evs.sorted { $0.timelineAt > $1.timelineAt }
        )
    }
}

func newEventId() -> String {
    UUID().uuidString.lowercased()
}

func newHabitId() -> String {
    "h_" + String(UUID().uuidString.lowercased().prefix(8))
}

func uniqueKeepOrder(_ items: [String]) -> [String] {
    var seen = Set<String>()
    var out: [String] = []
    for s in items {
        if seen.contains(s) { continue }
        seen.insert(s)
        out.append(s)
    }
    return out
}
