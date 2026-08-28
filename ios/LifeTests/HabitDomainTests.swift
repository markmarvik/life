import XCTest
@testable import Life

final class HabitDomainTests: XCTestCase {
    private let utc = TimeZone(secondsFromGMT: 0)!

    private func habit(_ id: String, _ name: String, icon: String = "•") -> HabitDef {
        HabitDef(id: id, name: name, icon: icon, createdAt: 1)
    }

    private func log(_ id: String, _ habitId: String, at: Int64 = 1) -> LifeEvent {
        LifeEvent(id: id, kind: .habit, createdAt: at, habitId: habitId)
    }

    func testEditRenamesHabitAndKeepsEveryLogOnSameId() {
        let data = AppData(
            habits: [habit("h1", "Meditate", icon: "🪷"), habit("h2", "Run")],
            events: [
                log("e1", "h1"),
                log("e2", "h1", at: 2),
                log("e3", "h2", at: 3),
                LifeEvent(id: "n1", kind: .note, createdAt: 4, body: "thought")
            ]
        )
        let next = HabitDomain.edit(data, habitId: "h1", name: "  Sit quietly  ", icon: "🙏")
        XCTAssertEqual(Optional("Sit quietly"), next.habitById("h1")?.name)
        XCTAssertEqual(Optional("🙏"), next.habitById("h1")?.icon)
        XCTAssertEqual(["h1", "h1", "h2"], next.events.filter { $0.kind == .habit }.map { $0.habitId ?? "" })
        XCTAssertEqual(Optional("thought"), next.events.first { $0.id == "n1" }?.body)
        XCTAssertEqual(Optional("Run"), next.habitById("h2")?.name)
    }

    func testEditMergesLogsWhenNameAlreadyExists() {
        let data = AppData(
            habits: [habit("h1", "Meditate"), habit("h2", "Run")],
            events: [log("e1", "h1"), log("e2", "h1", at: 2), log("e3", "h2", at: 3)]
        )
        let next = HabitDomain.edit(data, habitId: "h1", name: "run", icon: "•")
        XCTAssertNil(next.habitById("h1"))
        XCTAssertEqual(1, next.habits.count)
        XCTAssertEqual(["h2", "h2", "h2"], next.events.map { $0.habitId ?? "" })
    }

    func testEditNoopsWhenHabitMissingOrNameBlank() {
        let data = AppData(habits: [habit("h1", "Meditate")])
        XCTAssertEqual(data, HabitDomain.edit(data, habitId: "nope", name: "Sit", icon: "•"))
        XCTAssertEqual(Optional("Meditate"), HabitDomain.edit(data, habitId: "h1", name: "   ", icon: "•").habitById("h1")?.name)
    }

    func testSaveAsNewCreatesSeparateHabitAndLeavesLogs() {
        let data = AppData(
            habits: [habit("h1", "Meditate", icon: "🪷")],
            events: [log("e1", "h1"), log("e2", "h1", at: 2)]
        )
        let next = HabitDomain.saveAsNew(data, name: "Sit", icon: "🙏", nowMs: 9)
        XCTAssertEqual(Optional("Meditate"), next.habitById("h1")?.name)
        let created = next.findHabitByName("Sit")
        XCTAssertEqual(Optional("Sit"), created?.name)
        XCTAssertEqual(Optional("🙏"), created?.icon)
        XCTAssertNotEqual("h1", created?.id)
        XCTAssertEqual(["h1", "h1"], next.events.map { $0.habitId ?? "" })
    }

    func testSaveAsNewCanReassignOneLog() {
        let data = AppData(
            habits: [habit("h1", "Meditate")],
            events: [log("e1", "h1"), log("e2", "h1", at: 2)]
        )
        let next = HabitDomain.saveAsNew(data, name: "Sit", icon: "🙏", reassignEventId: "e1", nowMs: 9)
        let sit = next.findHabitByName("Sit")!
        XCTAssertEqual(Optional(sit.id), next.events.first { $0.id == "e1" }?.habitId)
        XCTAssertEqual(Optional("h1"), next.events.first { $0.id == "e2" }?.habitId)
    }

    func testSaveAsNewReusesExistingNameInsteadOfDuplicating() {
        let data = AppData(
            habits: [habit("h1", "Meditate"), habit("h2", "Run")],
            events: [log("e1", "h1")]
        )
        let next = HabitDomain.saveAsNew(data, name: "Run", icon: "•", reassignEventId: "e1")
        XCTAssertEqual(2, next.habits.count)
        XCTAssertEqual(Optional("h2"), next.events[0].habitId)
    }

    func testEditClaimsCatalogSlotWithoutLeavingDefault() {
        let data = AppData()
        let next = HabitDomain.edit(data, habitId: "cat_brush", name: "Brush teeth", icon: "🪥", why: "Dental foundation.", nowMs: 9)
        let claimed = next.habitById("cat_brush")
        XCTAssertEqual(Optional("Brush teeth"), claimed?.name)
        XCTAssertEqual(Optional("Dental foundation."), claimed?.why)
        XCTAssertEqual(1, next.habits.count)
        let ranked = HabitRanker.suggest(next, nowMs: 9, query: "", timeZone: utc)
        XCTAssertEqual(1, ranked.filter { $0.habitId == "cat_brush" }.count)
        XCTAssertTrue(ranked.allSatisfy { $0.name.caseInsensitiveCompare("Brush + floss") != .orderedSame })
        XCTAssertEqual(Optional("Dental foundation."), ranked.first { $0.habitId == "cat_brush" }?.why)
    }

    func testDefForEditUsesCatalogBeforeFirstLog() {
        let def = HabitDomain.defForEdit(AppData(), habitId: "cat_brush")
        XCTAssertEqual(Optional("Brush + floss"), def?.name)
        XCTAssertEqual(Optional("Dental foundation."), def?.why)
        XCTAssertEqual(Optional("cat_brush"), def?.id)
    }

    func testLogCountCountsOnlyThatHabit() {
        let data = AppData(events: [
            log("e1", "h1"),
            log("e2", "h1", at: 2),
            log("e3", "h2", at: 3),
            LifeEvent(id: "n1", kind: .note, createdAt: 4, body: "x")
        ])
        XCTAssertEqual(2, HabitDomain.logCount(data, habitId: "h1"))
        XCTAssertEqual(0, HabitDomain.logCount(data, habitId: "missing"))
        XCTAssertEqual(data, HabitDomain.saveAsNew(data, name: "  ", icon: "•"))
    }
}
