import XCTest
@testable import Life

final class ModelsTests: XCTestCase {
    private func utcMs(year: Int, month: Int, day: Int, hour: Int) -> Int64 {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0)!
        let date = cal.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    func testGroupEventsLabelsTodayAndYesterday() {
        let utc = TimeZone(secondsFromGMT: 0)!
        let now = utcMs(year: 2026, month: 8, day: 19, hour: 15)
        let today = utcMs(year: 2026, month: 8, day: 19, hour: 9)
        let yest = utcMs(year: 2026, month: 8, day: 18, hour: 9)
        let older = utcMs(year: 2026, month: 8, day: 10, hour: 9)
        let groups = groupEvents(
            [
                LifeEvent(id: "1", kind: .note, createdAt: today, body: "today"),
                LifeEvent(id: "2", kind: .note, createdAt: yest, body: "yest"),
                LifeEvent(id: "3", kind: .note, createdAt: older, body: "old")
            ],
            nowMs: now,
            timeZone: utc
        )
        XCTAssertEqual(["Today", "Yesterday"], groups.prefix(2).map(\.label))
        XCTAssertEqual("today", groups[0].events[0].body)
    }

    func testWithEventReplacesByIdAndPrepends() {
        let a = LifeEvent(id: "a", kind: .note, createdAt: 1, body: "one")
        let b = LifeEvent(id: "b", kind: .note, createdAt: 2, body: "two")
        let data = AppData().withEvent(a).withEvent(b)
        XCTAssertEqual(["b", "a"], data.events.map(\.id))
        var edited = a
        edited.body = "edited"
        let updated = data.withEvent(edited)
        XCTAssertEqual(Optional("edited"), updated.events.first { $0.id == "a" }?.body)
        XCTAssertEqual(2, updated.events.count)
    }
}
