import XCTest
@testable import Life

final class HabitCatalogRegressionTests: XCTestCase {
    private let utc = TimeZone(secondsFromGMT: 0)!

    func testCatalogIdsAndNamesAreUnique() {
        let ids = HabitCatalog.all.map(\.id)
        let names = HabitCatalog.all.map { $0.name.lowercased() }
        XCTAssertEqual(Array(Set(ids)).sorted(), ids.sorted())
        XCTAssertEqual(ids.count, Set(ids).count)
        XCTAssertEqual(names.count, Set(names).count)
        XCTAssertTrue(HabitCatalog.all.allSatisfy { !$0.why.trimmingCharacters(in: .whitespaces).isEmpty })
    }

    func testEveningHygieneIsNotASeparateHabit() {
        XCTAssertFalse(HabitCatalog.all.contains { $0.id.lowercased().contains("evening") })
        XCTAssertFalse(HabitCatalog.all.contains { $0.name.lowercased().contains("hygiene") })
        XCTAssertFalse(HabitCatalog.all.contains { $0.why.lowercased().contains("at night") })
    }

    func testEveryPremadeRowIsEditableBeforeFirstLog() {
        let empty = AppData()
        let ranked = HabitRanker.suggest(empty, nowMs: 1, query: "", timeZone: utc)
        for cat in HabitCatalog.all {
            let def = HabitDomain.defForEdit(empty, habitId: cat.id)
            XCTAssertEqual(Optional(cat.id), def?.id, cat.name)
            XCTAssertEqual(Optional(cat.name), def?.name)
            XCTAssertEqual(Optional(cat.why), def?.why)
            let row = ranked.first { $0.habitId == cat.id }
            XCTAssertNotNil(row, cat.name)
            XCTAssertTrue(row?.canEdit == true, cat.name)
            XCTAssertEqual(Optional(cat.why), row?.why)
        }
        XCTAssertTrue(ranked.allSatisfy { !$0.isCreate })
        XCTAssertEqual(HabitCatalog.all.count, ranked.filter(\.canEdit).count)
    }

    func testClaimingPremadeDoesNotLeaveDefaultBesideEdit() {
        for cat in HabitCatalog.all {
            let next = HabitDomain.edit(
                AppData(),
                habitId: cat.id,
                name: "My \(cat.name)",
                icon: cat.icon,
                why: cat.why,
                nowMs: 9
            )
            XCTAssertEqual(1, next.habits.filter { $0.id == cat.id }.count)
            let ranked = HabitRanker.suggest(next, nowMs: 9, query: "", timeZone: utc)
            let ids = ranked.compactMap(\.habitId)
            XCTAssertEqual(ids.count, Set(ids).count, cat.id)
            XCTAssertEqual(1, ranked.filter { $0.habitId == cat.id }.count, cat.id)
            XCTAssertTrue(ranked.allSatisfy { $0.name != cat.name }, cat.name)
            XCTAssertEqual(Optional(cat.why), ranked.first { $0.habitId == cat.id }?.why)
        }
    }

    func testLoggedCatalogWithoutStoredWhyStillShowsGrayText() {
        let data = AppData(
            habits: [HabitDef(id: "cat_brush", name: "Brush + floss", icon: "🪥", why: "", createdAt: 1)]
        )
        let ranked = HabitRanker.suggest(data, nowMs: 1, query: "", timeZone: utc)
        let row = ranked.first { $0.habitId == "cat_brush" }
        XCTAssertEqual(Optional("Dental foundation."), row?.why)
        XCTAssertTrue(row?.canEdit == true)
        XCTAssertFalse(HabitDomain.defForEdit(data, habitId: "cat_brush")?.why.trimmingCharacters(in: .whitespaces).isEmpty ?? true)
        XCTAssertTrue(ranked.allSatisfy { !($0.name == "Brush + floss" && $0.habitId != "cat_brush") })
    }

    func testCreateRowIsNotEditable() {
        let ranked = HabitRanker.suggest(AppData(), nowMs: 1, query: "Cold plunge", timeZone: utc)
        XCTAssertTrue(ranked.first?.isCreate == true)
        XCTAssertFalse(ranked.first?.canEdit == true)
    }
}
