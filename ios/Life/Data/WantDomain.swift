import Foundation

enum WantDomain {
    static let baseWords = 10
    static let step = 5
    static let cap = 40

    private static let wordRe = try! NSRegularExpression(pattern: "\\w+")

    static func wordCount(_ text: String) -> Int {
        let range = NSRange(text.startIndex..., in: text)
        return wordRe.numberOfMatches(in: text, range: range)
    }

    static func wordFloor(_ streak: Int) -> Int {
        min(cap, baseWords + step * max(streak, 0))
    }

    static func canSubmitReflection(_ text: String, streak: Int) -> Bool {
        wordCount(text) >= wordFloor(streak)
    }

    static func parseDue(_ dueDate: String?) -> DateComponents? {
        let s = dueDate?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if s.isEmpty { return nil }
        let parts = s.split(separator: "-")
        guard parts.count == 3,
              let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]) else { return nil }
        return DateComponents(year: y, month: m, day: d)
    }

    static func todayComponents(_ date: Date = Date(), timeZone: TimeZone = .current) -> DateComponents {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        return cal.dateComponents([.year, .month, .day], from: date)
    }

    static func iso(_ c: DateComponents) -> String {
        String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    static func addDays(_ c: DateComponents, _ days: Int, timeZone: TimeZone = TimeZone(secondsFromGMT: 0)!) -> DateComponents {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let date = cal.date(from: DateComponents(year: c.year, month: c.month, day: c.day)) ?? Date()
        let next = cal.date(byAdding: .day, value: days, to: date) ?? date
        return cal.dateComponents([.year, .month, .day], from: next)
    }

    static func compare(_ a: DateComponents, _ b: DateComponents) -> ComparisonResult {
        let av = (a.year ?? 0) * 10_000 + (a.month ?? 0) * 100 + (a.day ?? 0)
        let bv = (b.year ?? 0) * 10_000 + (b.month ?? 0) * 100 + (b.day ?? 0)
        if av < bv { return .orderedAscending }
        if av > bv { return .orderedDescending }
        return .orderedSame
    }

    static func isOverdue(_ event: LifeEvent, today: DateComponents) -> Bool {
        guard event.isOpenWant, let due = parseDue(event.dueDate) else { return false }
        return compare(due, today) == .orderedAscending
    }

    static func upcoming(_ data: AppData, today: DateComponents) -> [LifeEvent] {
        data.events.filter(\.isOpenWant).sorted { a, b in
            let da = parseDue(a.dueDate)
            let db = parseDue(b.dueDate)
            switch (da, db) {
            case (nil, nil):
                return a.createdAt < b.createdAt
            case (nil, _):
                return false
            case (_, nil):
                return true
            case let (da?, db?):
                let c = compare(da, db)
                if c != .orderedSame { return c == .orderedAscending }
                return a.createdAt < b.createdAt
            }
        }
    }

    static func resolveDueOnSave(isWant: Bool, dueDate: String?, today: DateComponents) -> String? {
        if !isWant { return dueDate }
        return dueDate ?? iso(today)
    }

    static func upcomingLabel(_ dueDate: String?, today: DateComponents, locale: Locale = .current) -> String {
        guard let due = parseDue(dueDate) else { return "Someday" }
        if compare(due, today) == .orderedAscending { return "Overdue" }
        if compare(due, today) == .orderedSame { return "Today" }
        if compare(due, addDays(today, 1)) == .orderedSame { return "Tomorrow" }
        return formatDue(due, locale: locale)
    }

    static func historyLabel(_ dueDate: String?, today: DateComponents, locale: Locale = .current) -> String {
        guard let due = parseDue(dueDate) else { return "" }
        if compare(due, today) == .orderedSame { return "Today" }
        if compare(due, addDays(today, 1)) == .orderedSame { return "Tomorrow" }
        if compare(due, addDays(today, -1)) == .orderedSame { return "Yesterday" }
        return formatDue(due, locale: locale)
    }

    private static func formatDue(_ due: DateComponents, locale: Locale) -> String {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        guard let date = cal.date(from: DateComponents(year: due.year, month: due.month, day: due.day)) else {
            return iso(due)
        }
        let fmt = DateFormatter()
        fmt.locale = locale
        fmt.timeZone = cal.timeZone
        fmt.dateFormat = "EEE d MMM"
        return fmt.string(from: date)
    }

    static func withCompletedWant(_ data: AppData, id: String, nowMs now: Int64) -> AppData {
        guard var event = data.events.first(where: { $0.id == id }), event.isOpenWant else { return data }
        event.wantStatus = .done
        event.resolvedAt = now
        var next = data.withEvent(event)
        next.wantStrikeStreak = 0
        return next
    }

    static func withFailedWant(
        _ data: AppData,
        id: String,
        reflection: String,
        nowMs now: Int64,
        today: DateComponents
    ) -> AppData {
        guard var event = data.events.first(where: { $0.id == id }), event.isOpenWant else { return data }
        if !canSubmitReflection(reflection, streak: data.wantStrikeStreak) { return data }
        let missed = isOverdue(event, today: today)
        event.wantStatus = missed ? .missed : .canceled
        event.reflection = reflection.trimmingCharacters(in: .whitespacesAndNewlines)
        event.resolvedAt = now
        var next = data.withEvent(event)
        next.wantStrikeStreak = data.wantStrikeStreak + 1
        return next
    }

    static func applyWantFields(
        _ event: LifeEvent,
        isWant: Bool,
        dueDate: String?,
        today: DateComponents
    ) -> LifeEvent {
        let due = resolveDueOnSave(isWant: isWant, dueDate: dueDate, today: today)
        var event = event
        if !isWant {
            let wasOpen = event.wantStatus == .open
            event.isWant = false
            event.dueDate = dueDate
            event.wantStatus = .none
            if wasOpen { event.reflection = "" }
            return event
        }
        let status: WantStatus
        switch event.wantStatus {
        case .done, .canceled, .missed:
            status = event.wantStatus
        default:
            status = .open
        }
        event.isWant = true
        event.dueDate = due
        event.wantStatus = status
        return event
    }
}
