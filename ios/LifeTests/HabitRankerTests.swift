import XCTest
@testable import Life

final class HabitRankerTests: XCTestCase {
    private let utc = TimeZone(secondsFromGMT: 0)!

    private func utcMs(year: Int, month: Int, day: Int, hour: Int) -> Int64 {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = utc
        let date = cal.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    func testUnknownQueryOffersCreateFirst() {
        let data = AppData(habits: [HabitDef(id: "h1", name: "Meditate", createdAt: 1)])
        let ranked = HabitRanker.suggest(data, nowMs: 1, query: "Cold plunge", timeZone: utc)
        XCTAssertTrue(ranked.first?.isCreate == true)
        XCTAssertEqual(Optional("Cold plunge"), ranked.first?.name)
    }

    func testFuzzyHitsExistingHabitAndDoesNotCreateWhenExact() {
        let data = AppData(habits: [HabitDef(id: "h1", name: "Meditate", createdAt: 1)])
        let ranked = HabitRanker.suggest(data, nowMs: 1, query: "meditate", timeZone: utc)
        XCTAssertTrue(ranked.allSatisfy { !$0.isCreate })
        XCTAssertEqual(Optional("Meditate"), ranked.first?.name)
    }

    func testEmptyQueryRanksMorningHabitHigherAtEight() {
        let now = utcMs(year: 2026, month: 8, day: 19, hour: 8)
        let data = AppData(
            habits: [
                HabitDef(id: "h_sun", name: "Morning sunlight", icon: "•", createdAt: 1),
                HabitDef(id: "h_read", name: "Read before bed", icon: "•", createdAt: 1)
            ],
            events: [
                LifeEvent(id: "1", kind: .habit, createdAt: utcMs(year: 2026, month: 8, day: 18, hour: 8), habitId: "h_sun"),
                LifeEvent(id: "2", kind: .habit, createdAt: utcMs(year: 2026, month: 8, day: 17, hour: 8), habitId: "h_sun"),
                LifeEvent(id: "3", kind: .habit, createdAt: utcMs(year: 2026, month: 8, day: 16, hour: 7), habitId: "h_sun"),
                LifeEvent(id: "4", kind: .habit, createdAt: utcMs(year: 2026, month: 8, day: 18, hour: 22), habitId: "h_read"),
                LifeEvent(id: "5", kind: .habit, createdAt: utcMs(year: 2026, month: 8, day: 17, hour: 22), habitId: "h_read")
            ]
        )
        let ranked = HabitRanker.suggest(data, nowMs: now, query: "", timeZone: utc)
        XCTAssertEqual(Optional("Morning sunlight"), ranked.first?.name)
    }
}
