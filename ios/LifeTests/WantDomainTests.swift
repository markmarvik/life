import XCTest
@testable import Life

final class WantDomainTests: XCTestCase {
    private let today = DateComponents(year: 2026, month: 8, day: 19)

    private func want(
        id: String,
        due: String,
        createdAt: Int64 = 1,
        status: WantStatus = .open
    ) -> LifeEvent {
        LifeEvent(
            id: id,
            kind: .note,
            createdAt: createdAt,
            body: "do \(id)",
            dueDate: due,
            isWant: true,
            wantStatus: status
        )
    }

    func testWordCountIgnoresExtraSpace() {
        XCTAssertEqual(10, WantDomain.wordCount("  one two  three four five six seven eight nine ten  "))
        XCTAssertEqual(9, WantDomain.wordCount("one two three four five six seven eight nine"))
    }

    func testFloorScalesAndCaps() {
        XCTAssertEqual(10, WantDomain.wordFloor(0))
        XCTAssertEqual(15, WantDomain.wordFloor(1))
        XCTAssertEqual(40, WantDomain.wordFloor(100))
    }

    func testCompleteResetsStreak() {
        let data = AppData(events: [want(id: "a", due: "2026-08-19")], wantStrikeStreak: 3)
        let next = WantDomain.withCompletedWant(data, id: "a", nowMs: 99)
        XCTAssertEqual(0, next.wantStrikeStreak)
        XCTAssertEqual(Optional(WantStatus.done), next.events.first?.wantStatus)
        XCTAssertEqual(Optional(Int64(99)), next.events.first?.resolvedAt)
    }

    func testFailRequiresFloorAndIncrementsStreak() {
        let data = AppData(events: [want(id: "a", due: "2026-08-19")], wantStrikeStreak: 0)
        let tooShort = WantDomain.withFailedWant(data, id: "a", reflection: "too short", nowMs: 1, today: today)
        XCTAssertEqual(Optional(WantStatus.open), tooShort.events.first?.wantStatus)
        XCTAssertEqual(0, tooShort.wantStrikeStreak)

        let ten = "one two three four five six seven eight nine ten"
        let canceled = WantDomain.withFailedWant(data, id: "a", reflection: ten, nowMs: 2, today: today)
        XCTAssertEqual(Optional(WantStatus.canceled), canceled.events.first?.wantStatus)
        XCTAssertEqual(1, canceled.wantStrikeStreak)

        let overdue = AppData(events: [want(id: "b", due: "2026-08-10")], wantStrikeStreak: 1)
        let fifteen = "\(ten) eleven twelve thirteen fourteen fifteen"
        let missed = WantDomain.withFailedWant(overdue, id: "b", reflection: fifteen, nowMs: 3, today: today)
        XCTAssertEqual(Optional(WantStatus.missed), missed.events.first?.wantStatus)
        XCTAssertEqual(2, missed.wantStrikeStreak)
    }

    func testUpcomingOrdersOverdueThenTodayThenLater() {
        let data = AppData(events: [
            want(id: "later", due: "2026-08-22", createdAt: 3),
            want(id: "today", due: "2026-08-19", createdAt: 2),
            want(id: "old", due: "2026-08-10", createdAt: 1),
            LifeEvent(id: "note", kind: .note, createdAt: 4, body: "just a thought", dueDate: "2026-08-19")
        ])
        let ids = WantDomain.upcoming(data, today: today).map(\.id)
        XCTAssertEqual(["old", "today", "later"], ids)
    }

    func testDateOnlyNoteIsNotUpcoming() {
        let data = AppData(events: [
            LifeEvent(id: "n", kind: .note, createdAt: 1, body: "idea", dueDate: "2026-08-20")
        ])
        XCTAssertTrue(WantDomain.upcoming(data, today: today).isEmpty)
    }

    func testGroupEventsOmitsOpenWantsAndUsesResolvedDay() {
        let open = want(id: "open", due: "2026-08-20", createdAt: 1_000)
        var done = want(id: "done", due: "2026-08-18", createdAt: 1_000, status: .done)
        done.resolvedAt = 86_400_000 * 2
        let note = LifeEvent(id: "n", kind: .note, createdAt: 2_000, body: "hi")
        let groups = groupEvents([open, done, note], nowMs: 2_000)
        let ids = groups.flatMap(\.events).map(\.id)
        XCTAssertFalse(ids.contains("open"))
        XCTAssertTrue(ids.contains("n"))
        XCTAssertTrue(ids.contains("done"))
    }

    func testWantWithoutDateDefaultsToToday() {
        XCTAssertEqual(Optional("2026-08-19"), WantDomain.resolveDueOnSave(isWant: true, dueDate: nil, today: today))
        XCTAssertNil(WantDomain.resolveDueOnSave(isWant: false, dueDate: nil, today: today))
        XCTAssertEqual(Optional("2026-08-21"), WantDomain.resolveDueOnSave(isWant: false, dueDate: "2026-08-21", today: today))
    }
}
