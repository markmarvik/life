import Foundation
import Combine

@MainActor
final class LifeViewModel: ObservableObject {
    @Published var data: AppData
    @Published var showCorruptBanner: Bool
    @Published var showSettings = false

    private let store: LifeStore

    init(store: LifeStore = .shared) {
        self.store = store
        self.data = store.load()
        self.showCorruptBanner = store.shouldShowBanner
        WantScheduler.scheduleAll(self.data)
    }

    func dismissCorrupt() {
        store.dismissBanner()
        showCorruptBanner = false
    }

    func logHabit(_ suggestion: HabitSuggestion) {
        mutate { data in
            let now = nowMs()
            let (next, habit) = resolveHabit(data, suggestion: suggestion, now: now)
            return next.withEvent(
                LifeEvent(id: newEventId(), kind: .habit, createdAt: now, habitId: habit.id)
            )
        }
    }

    func logHabitNamed(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return }
        let suggestions = HabitRanker.suggest(data, nowMs: nowMs(), query: trimmed)
        if let known = suggestions.first(where: { !$0.isCreate && Fuzzy.score(trimmed, $0.name) >= 0.62 }) {
            logHabit(known)
            return
        }
        if var create = suggestions.first(where: { $0.isCreate }) {
            create.name = trimmed
            logHabit(create)
            return
        }
        logHabit(HabitSuggestion(name: trimmed, icon: "•", habitId: nil, score: 0, isCreate: true))
    }

    func editHabit(habitId: String, name: String, icon: String, why: String = "") {
        mutate { HabitDomain.edit($0, habitId: habitId, name: name, icon: icon, why: why) }
    }

    func saveHabitAsNew(name: String, icon: String, why: String = "", reassignEventId: String? = nil) {
        mutate { HabitDomain.saveAsNew($0, name: name, icon: icon, why: why, reassignEventId: reassignEventId) }
    }

    func logNote(body: String, tags: [String], isWant: Bool = false, dueDate: String? = nil) {
        let text = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return }
        mutate { data in
            let today = WantDomain.todayComponents()
            var next = data
            for t in tags { next = next.withTag(t) }
            let due = WantDomain.resolveDueOnSave(isWant: isWant, dueDate: dueDate, today: today)
            if isWant { WantScheduler.requestPermission() }
            return next.withEvent(
                LifeEvent(
                    id: newEventId(),
                    kind: .note,
                    createdAt: nowMs(),
                    body: text,
                    tags: uniqueKeepOrder(tags.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }),
                    dueDate: due,
                    isWant: isWant,
                    wantStatus: isWant ? .open : .none
                )
            )
        }
    }

    func completeWant(_ id: String) {
        mutate { WantDomain.withCompletedWant($0, id: id, nowMs: nowMs()) }
    }

    func failWant(id: String, reflection: String) {
        mutate { WantDomain.withFailedWant($0, id: id, reflection: reflection, nowMs: nowMs(), today: WantDomain.todayComponents()) }
    }

    func logFinance(direction: MoneyDirection, amountMinor: Int64, payee: String, category: String, memo: String) {
        if amountMinor <= 0 { return }
        mutate { data in
            data.withPayee(payee)
                .withCategory(category)
                .withEvent(
                    LifeEvent(
                        id: newEventId(),
                        kind: .finance,
                        createdAt: nowMs(),
                        body: memo.trimmingCharacters(in: .whitespacesAndNewlines),
                        amountMinor: amountMinor,
                        direction: direction,
                        payee: payee.trimmingCharacters(in: .whitespacesAndNewlines),
                        category: category.trimmingCharacters(in: .whitespacesAndNewlines)
                    )
                )
        }
    }

    func updateEvent(_ event: LifeEvent) {
        mutate { data in
            var next = data.withEvent(event)
            if event.kind == .note {
                for t in event.tags { next = next.withTag(t) }
            }
            if event.kind == .finance {
                next = next.withPayee(event.payee).withCategory(event.category)
            }
            return next
        }
    }

    func updateNote(existing: LifeEvent, body: String, tags: [String], isWant: Bool, dueDate: String?) {
        let today = WantDomain.todayComponents()
        var copy = existing
        copy.body = body.trimmingCharacters(in: .whitespacesAndNewlines)
        copy.tags = uniqueKeepOrder(
            tags.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        )
        let updated = WantDomain.applyWantFields(copy, isWant: isWant, dueDate: dueDate, today: today)
        if isWant { WantScheduler.requestPermission() }
        updateEvent(updated)
    }

    func deleteEvent(_ id: String) {
        mutate { $0.withoutEvent(id) }
    }

    func updateSettings(
        openingBalanceMinor: Int64? = nil,
        currencyCode: String? = nil,
        colorScheme: String? = nil,
        backgroundMode: String? = nil,
        wantRemindersEnabled: Bool? = nil
    ) {
        mutate { data in
            var next = data
            if let openingBalanceMinor { next.openingBalanceMinor = openingBalanceMinor }
            if let currencyCode {
                next.currencyCode = currencyCode.isEmpty ? data.currencyCode : currencyCode
            }
            if let colorScheme { next.colorScheme = colorScheme }
            if let backgroundMode { next.backgroundMode = backgroundMode }
            if let wantRemindersEnabled { next.wantRemindersEnabled = wantRemindersEnabled }
            return next
        }
    }

    func exportJSON() throws -> String {
        try store.encodeBackup(data, pretty: true)
    }

    func importJSON(_ text: String) throws {
        let parsed = try store.decodeBackup(text)
        data = parsed
        store.save(parsed)
        WantScheduler.scheduleAll(parsed)
    }

    private func mutate(_ block: (AppData) -> AppData) {
        let next = block(data)
        data = next
        store.save(next)
        WantScheduler.scheduleAll(next)
    }

    private func resolveHabit(
        _ data: AppData,
        suggestion: HabitSuggestion,
        now: Int64
    ) -> (AppData, HabitDef) {
        if !suggestion.isCreate, let id = suggestion.habitId {
            if let existing = data.habitById(id) { return (data, existing) }
            if let cat = HabitCatalog.byId(id) {
                if let named = data.findHabitByName(cat.name) { return (data, named) }
                let created = cat.toDef(createdAt: now)
                return (data.withHabit(created), created)
            }
        }
        if let named = data.findHabitByName(suggestion.name) { return (data, named) }
        let catalog = HabitCatalog.byName(suggestion.name)
        let habit: HabitDef
        if let catalog {
            habit = catalog.toDef(createdAt: now)
        } else {
            let icon = (suggestion.icon == "＋" || suggestion.icon.isEmpty) ? "•" : suggestion.icon
            let why = suggestion.why == "Create and log" ? "" : suggestion.why
            habit = HabitDef(
                id: newHabitId(),
                name: suggestion.name.trimmingCharacters(in: .whitespacesAndNewlines),
                icon: icon,
                why: why,
                createdAt: now
            )
        }
        return (data.withHabit(habit), habit)
    }
}
