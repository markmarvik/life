import Foundation

enum HabitDomain {
    static func newId() -> String { newHabitId() }

    static func logCount(_ data: AppData, habitId: String) -> Int {
        data.events.filter { $0.kind == .habit && $0.habitId == habitId }.count
    }

    /// Existing def, or a catalog template so premade rows can be edited before first log.
    static func defForEdit(_ data: AppData, habitId: String) -> HabitDef? {
        let existing = data.habitById(habitId)
        let cat = HabitCatalog.byId(habitId)
        if let existing {
            if existing.why.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, let cat {
                var copy = existing
                copy.why = cat.why
                return copy
            }
            return existing
        }
        return cat?.toDef(createdAt: 0)
    }

    /// Rename / re-icon an existing habit, or claim a premade catalog slot.
    /// Claiming uses the catalog id so the default row does not come back
    /// beside the edited one. Logs keep the same habitId. If the new name
    /// already belongs to a different habit, logs are merged onto that habit
    /// and this definition is dropped.
    static func edit(
        _ data: AppData,
        habitId: String,
        name: String,
        icon: String,
        why: String? = nil,
        nowMs now: Int64 = nowMs()
    ) -> AppData {
        let catalog = HabitCatalog.byId(habitId)
        guard var habit = data.habitById(habitId) ?? catalog?.toDef(createdAt: now) else { return data }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return data }
        let nextIcon = icon.trimmingCharacters(in: .whitespacesAndNewlines)
        habit.icon = nextIcon.isEmpty ? habit.icon : nextIcon
        habit.why = why?.trimmingCharacters(in: .whitespacesAndNewlines) ?? habit.why
        habit.name = trimmed

        if let other = data.findHabitByName(trimmed), other.id != habitId {
            var next = data
            next.habits = data.habits.filter { $0.id != habitId }
            next.events = data.events.map { e in
                var e = e
                if e.kind == .habit && e.habitId == habitId {
                    e.habitId = other.id
                }
                return e
            }
            return next
        }

        var next = data
        if data.habitById(habitId) == nil {
            next.habits = data.habits + [habit]
        } else {
            next.habits = data.habits.map { $0.id == habitId ? habit : $0 }
        }
        return next
    }

    /// Insert a new habit. Existing logs stay. If reassignEventId is set,
    /// that one log moves onto the new habit. A same-name habit is reused.
    static func saveAsNew(
        _ data: AppData,
        name: String,
        icon: String,
        why: String = "",
        reassignEventId: String? = nil,
        nowMs now: Int64 = nowMs()
    ) -> AppData {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return data }
        let existing = data.findHabitByName(trimmed)
        let habit: HabitDef
        if let existing {
            habit = existing
        } else {
            let catalog = HabitCatalog.byName(trimmed)
            let iconTrim = icon.trimmingCharacters(in: .whitespacesAndNewlines)
            let whyTrim = why.trimmingCharacters(in: .whitespacesAndNewlines)
            habit = HabitDef(
                id: newId(),
                name: trimmed,
                icon: iconTrim.isEmpty ? (catalog?.icon ?? "•") : iconTrim,
                why: whyTrim.isEmpty ? (catalog?.why ?? "") : whyTrim,
                createdAt: now
            )
        }
        var next = existing == nil ? {
            var d = data
            d.habits = data.habits + [habit]
            return d
        }() : data
        if let reassignEventId {
            next.events = next.events.map { e in
                var e = e
                if e.id == reassignEventId && e.kind == .habit {
                    e.habitId = habit.id
                }
                return e
            }
        }
        return next
    }
}
