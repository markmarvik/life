import XCTest
@testable import Life

final class MoneyTests: XCTestCase {
    func testUsdFourFiftyToMinor() {
        XCTAssertEqual(Optional(Int64(450)), Money.parseToMinor("4.50", currencyCode: "USD"))
        XCTAssertEqual(Optional(Int64(450)), Money.parseToMinor("4,50", currencyCode: "USD"))
        XCTAssertEqual(Optional(Int64(1200)), Money.parseToMinor("12", currencyCode: "USD"))
    }

    func testJpyHasNoFraction() {
        XCTAssertEqual(Optional(Int64(1000)), Money.parseToMinor("1000", currencyCode: "JPY"))
        XCTAssertEqual("1000", Money.formatMajorPlain(Int64(1000), currencyCode: "JPY"))
    }

    func testRejectsNegativeAndGarbage() {
        XCTAssertNil(Money.parseToMinor("", currencyCode: "USD"))
        XCTAssertNil(Money.parseToMinor("-3", currencyCode: "USD"))
        XCTAssertNil(Money.parseToMinor("abc", currencyCode: "USD"))
    }

    func testBalanceIsOpeningPlusInMinusOut() {
        let data = AppData(
            events: [
                LifeEvent(id: "1", kind: .finance, createdAt: 1, amountMinor: 500, direction: .income),
                LifeEvent(id: "2", kind: .finance, createdAt: 2, amountMinor: 250, direction: .expense),
                LifeEvent(id: "3", kind: .note, createdAt: 3, body: "ignore me")
            ],
            openingBalanceMinor: 10_000
        )
        XCTAssertEqual(Int64(10_250), data.balanceMinor())
    }

    func testDefaultCurrencyDoesNotCrash() {
        let code = Money.defaultCurrencyCode(Locale(identifier: "en_US"))
        XCTAssertEqual("USD", code)
    }

    func testRoundTripFormatPlain() {
        let minor = Money.parseToMinor("4.50", currencyCode: "USD")!
        XCTAssertEqual("4.50", Money.formatMajorPlain(minor, currencyCode: "USD"))
    }
}
