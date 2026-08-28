import Foundation

struct CatalogHabit: Equatable {
    var id: String
    var name: String
    var icon: String
    var why: String
}

enum HabitCatalog {
    static let all: [CatalogHabit] = [
        CatalogHabit(id: "cat_morning_sun", name: "Morning sunlight", icon: "☀️", why: "Aligns circadian rhythm."),
        CatalogHabit(id: "cat_meditate", name: "Meditate", icon: "🪷", why: "Start calm and present."),
        CatalogHabit(id: "cat_brush", name: "Brush + floss", icon: "🪥", why: "Dental foundation."),
        CatalogHabit(id: "cat_make_bed", name: "Make the bed", icon: "🛏️", why: "Small win, tidy space."),
        CatalogHabit(id: "cat_hydrate", name: "Morning hydrate", icon: "💧", why: "Glass of water on wake."),
        CatalogHabit(id: "cat_cold_shower", name: "Cold shower / shower", icon: "🚿", why: "Wake-up and hygiene."),
        CatalogHabit(id: "cat_protein", name: "Protein first", icon: "🥩", why: "Muscle, satiety, metabolism."),
        CatalogHabit(id: "cat_lunch_walk", name: "Lunch walk", icon: "🚶", why: "Midday movement reset."),
        CatalogHabit(id: "cat_stretch", name: "Stand / stretch break", icon: "🧍", why: "Undo sitting."),
        CatalogHabit(id: "cat_move", name: "Move your body", icon: "🚶", why: "20+ min walk or exercise."),
        CatalogHabit(id: "cat_strength", name: "Strength training", icon: "🏋️", why: "Weights, bodyweight, or gym."),
        CatalogHabit(id: "cat_read", name: "Read before bed", icon: "📖", why: "Calm input, not scroll."),
        CatalogHabit(id: "cat_gratitude", name: "Gratitude / 3 things", icon: "🙏", why: "Evening review."),
        CatalogHabit(id: "cat_no_screens", name: "No screens wind-down", icon: "📵", why: "Protect melatonin.")
    ]

    static func byId(_ id: String) -> CatalogHabit? {
        all.first { $0.id == id }
    }

    static func byName(_ name: String) -> CatalogHabit? {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return all.first { $0.name.caseInsensitiveCompare(n) == .orderedSame }
    }

    static func stillSuggested(_ data: AppData) -> [CatalogHabit] {
        let live = data.habits.filter { !$0.archived }
        let claimedIds = Set(live.map(\.id))
        let claimedNames = Set(live.map { $0.name.lowercased() })
        return all.filter { !claimedIds.contains($0.id) && !claimedNames.contains($0.name.lowercased()) }
    }
}

extension CatalogHabit {
    func toDef(createdAt: Int64 = 0) -> HabitDef {
        HabitDef(id: id, name: name, icon: icon, why: why, createdAt: createdAt)
    }
}

extension HabitDef {
    func displayWhy() -> String {
        if !why.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return why }
        return HabitCatalog.byId(id)?.why ?? ""
    }
}
