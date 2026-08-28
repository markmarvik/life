import XCTest
@testable import Life

final class FuzzyTests: XCTestCase {
    func testExactAndPrefixBeatTypo() {
        let exact = Fuzzy.score("meditate", "Meditate")
        let prefix = Fuzzy.score("med", "Meditate")
        let typo = Fuzzy.score("meditete", "Meditate")
        let miss = Fuzzy.score("zzzz", "Meditate")
        XCTAssertTrue(exact > prefix)
        XCTAssertTrue(prefix > typo)
        XCTAssertTrue(typo > 0.45)
        XCTAssertTrue(miss < 0.45)
    }

    func testTokenStartMatches() {
        XCTAssertTrue(Fuzzy.matches("sun", "Morning sunlight"))
        XCTAssertTrue(Fuzzy.matches("walk", "Lunch walk"))
    }

    func testSubsequenceMatches() {
        XCTAssertTrue(Fuzzy.matches("mngsun", "Morning sunlight") || Fuzzy.score("msun", "Morning sunlight") > 0.45)
        XCTAssertTrue(Fuzzy.matches("brush", "Brush + floss"))
    }
}
