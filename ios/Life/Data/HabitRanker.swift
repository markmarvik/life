import Foundation

struct HabitSuggestion: Equatable, Identifiable {
    var name: String
    var icon: String
    var why: String = ""
    var habitId: String?
    var score: Double
    var isCreate: Bool = false

    var id: String { "\(isCreate):\(habitId ?? "nil"):\(name)" }

    /// Premade catalog rows and saved habits are long-press editable.
    var canEdit: Bool { !isCreate && !(habitId ?? "").isEmpty }
}

enum HabitRanker {
    struct Usage {
        var count30d: Int
        var lastAt: Int64?
        var hourHits: Int
        var hourTotal: Int
    }

    static func suggest(
        _ data: AppData,
        nowMs now: Int64,
        query: String,
        timeZone: TimeZone = .current
    ) -> [HabitSuggestion] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let usage = usageByHabit(data, nowMs: now, timeZone: timeZone)

        let created: [HabitSuggestion] = data.habits.filter { !$0.archived }.map { habit in
            let u = usage[habit.id]
            return HabitSuggestion(
                name: habit.name,
                icon: habit.icon.isEmpty ? "•" : habit.icon,
                why: habit.displayWhy(),
                habitId: habit.id,
                score: usageScore(u, nowMs: now) + 0.5
            )
        }

        let catalog: [HabitSuggestion] = HabitCatalog.stillSuggested(data).map { cat in
            HabitSuggestion(
                name: cat.name,
                icon: cat.icon,
                why: cat.why,
                habitId: cat.id,
                score: 0.05
            )
        }

        var seen = Set<String>()
        var all: [HabitSuggestion] = []
        for s in created + catalog {
            let key = s.habitId ?? "create:\(s.name.lowercased())"
            if seen.contains(key) { continue }
            seen.insert(key)
            all.append(s)
        }

        let filtered: [HabitSuggestion]
        if q.isEmpty {
            filtered = all.sorted {
                let aUsed = $0.habitId.map { usage[$0] != nil } ?? false
                let bUsed = $1.habitId.map { usage[$0] != nil } ?? false
                if aUsed != bUsed { return aUsed && !bUsed }
                if $0.score != $1.score { return $0.score > $1.score }
                return $0.name.lowercased() < $1.name.lowercased()
            }
        } else {
            filtered = all.compactMap { s -> HabitSuggestion? in
                let f = Fuzzy.score(q, s.name)
                if f < 0.45 { return nil }
                var copy = s
                copy.score = f * 4.0 + s.score
                return copy
            }.sorted { $0.score > $1.score }
        }

        let exact = !q.isEmpty && all.contains { $0.name.caseInsensitiveCompare(q) == .orderedSame }
        if !q.isEmpty && !exact {
            let create = HabitSuggestion(
                name: q,
                icon: "＋",
                why: "Create and log",
                habitId: nil,
                score: 100.0,
                isCreate: true
            )
            return [create] + filtered
        }
        return filtered
    }

    private static func usageByHabit(
        _ data: AppData,
        nowMs now: Int64,
        timeZone: TimeZone
    ) -> [String: Usage] {
        let windowStart = now - 30 * 24 * 60 * 60 * 1000
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let currentHour = cal.component(.hour, from: dateFromMs(now))
        let hours = Set([(currentHour + 23) % 24, currentHour, (currentHour + 1) % 24])
        var acc: [String: [LifeEvent]] = [:]
        for e in data.events {
            guard e.kind == .habit, let id = e.habitId else { continue }
            acc[id, default: []].append(e)
        }
        var result: [String: Usage] = [:]
        for (id, evs) in acc {
            let last = evs.map(\.createdAt).max()
            let count30 = evs.filter { $0.createdAt >= windowStart }.count
            var hits = 0
            var total = 0
            for e in evs {
                total += 1
                let h = cal.component(.hour, from: dateFromMs(e.createdAt))
                if hours.contains(h) { hits += 1 }
            }
            result[id] = Usage(count30d: count30, lastAt: last, hourHits: hits, hourTotal: total)
        }
        return result
    }

    static func usageScore(_ u: Usage?, nowMs now: Int64) -> Double {
        guard let u else { return 0 }
        let freq = 3.0 * log(1.0 + Double(u.count30d))
        let recency: Double
        if let last = u.lastAt {
            let hours = Double(max(now - last, 0)) / 3_600_000.0
            recency = 2.0 * exp(-hours / 36.0)
        } else {
            recency = 0
        }
        let affinity = u.hourTotal > 0 ? 2.5 * (Double(u.hourHits) / Double(u.hourTotal)) : 0
        return freq + recency + affinity
    }
}
