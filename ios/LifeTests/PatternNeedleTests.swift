import XCTest
@testable import Life

final class PatternNeedleTests: XCTestCase {
    private let today = DateComponents(year: 2026, month: 8, day: 29)
    private var ctx: NeedleContext {
        NeedleContext(
            data: AppData(currencyCode: "RSD"),
            today: today,
            currencyCode: "RSD"
        )
    }

    func testPopioKreatinLogsHabit() {
        let d = PatternNeedle.dispatch("popio kreatin", ctx: ctx)
        guard case .habit(let name, let confidence, _) = d else {
            return XCTFail("expected habit, got \(d)")
        }
        XCTAssertEqual(name.lowercased(), "kreatin")
        XCTAssertTrue(d.canAutoCommit)
        XCTAssertGreaterThanOrEqual(confidence, 0.55)
    }

    func testLoggedCreatineLogsHabit() {
        let d = PatternNeedle.dispatch("logged creatine", ctx: ctx)
        guard case .habit(let name, _, _) = d else {
            return XCTFail("expected habit, got \(d)")
        }
        XCTAssertEqual(name.lowercased(), "creatine")
    }

    func testSpentLidFoodIsFinanceOut() {
        let d = PatternNeedle.dispatch("spent 450 dinara lidl food", ctx: ctx)
        guard case .finance(let dir, let amt, let payee, let category, _, _, _) = d else {
            return XCTFail("expected finance, got \(d)")
        }
        XCTAssertEqual(dir, .expense)
        XCTAssertEqual(amt, 45000) // RSD has 2 fraction digits
        XCTAssertTrue(payee.lowercased().contains("lidl"))
        XCTAssertEqual(category, "Food")
        XCTAssertTrue(d.canAutoCommit)
    }

    func testNoteWithTomorrowWant() {
        let d = PatternNeedle.dispatch("want call dentist tomorrow", ctx: ctx)
        guard case .note(let body, _, let wantDate, _, _) = d else {
            return XCTFail("expected note, got \(d)")
        }
        XCTAssertEqual(wantDate, WantDomain.iso(WantDomain.addDays(today, 1)))
        XCTAssertTrue(body.lowercased().contains("dentist"))
    }

    func testBalanceQuery() {
        let d = PatternNeedle.dispatch("koliko imam", ctx: ctx)
        guard case .balance = d else {
            return XCTFail("expected balance, got \(d)")
        }
    }

    func testCompleteOpenWant() {
        let data = AppData(
            events: [
                LifeEvent(
                    id: "w1",
                    kind: .note,
                    createdAt: 1,
                    body: "Call dentist",
                    dueDate: "2026-08-29",
                    isWant: true,
                    wantStatus: .open
                )
            ],
            currencyCode: "RSD"
        )
        let d = PatternNeedle.dispatch(
            "done call dentist",
            ctx: NeedleContext(data: data, today: today, currencyCode: "RSD")
        )
        guard case .completeWant = d else {
            return XCTFail("expected completeWant, got \(d)")
        }
    }

    func testKnownCatalogHabitWithoutVerb() {
        let d = PatternNeedle.dispatch("Meditate", ctx: ctx)
        guard case .habit(let name, _, _) = d else {
            return XCTFail("expected habit, got \(d)")
        }
        XCTAssertEqual(name, "Meditate")
    }
}
