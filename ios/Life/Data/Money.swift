import Foundation

enum Money {
    static func defaultCurrencyCode(_ locale: Locale = .current) -> String {
        if let code = locale.currency?.identifier { return code }
        return "USD"
    }

    static func fractionDigits(_ currencyCode: String) -> Int {
        let nf = NumberFormatter()
        nf.numberStyle = .currency
        nf.currencyCode = currencyCode
        let d = nf.maximumFractionDigits
        return d >= 0 ? d : 2
    }

    static func toMinor(_ major: Double, currencyCode: String) -> Int64 {
        let scale = pow(10.0, Double(fractionDigits(currencyCode)))
        return Int64((major * scale).rounded())
    }

    static func toMajor(_ minor: Int64, currencyCode: String) -> Double {
        let scale = pow(10.0, Double(fractionDigits(currencyCode)))
        return Double(minor) / scale
    }

    /// Parse a user-typed amount ("4.50", "4,50", "12") into minor units.
    static func parseToMinor(_ input: String, currencyCode: String) -> Int64? {
        let t = input.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: ",", with: ".")
        if t.isEmpty { return nil }
        guard let d = Double(t), d >= 0, d.isFinite else { return nil }
        return toMinor(d, currencyCode: currencyCode)
    }

    static func formatMajorPlain(_ minor: Int64, currencyCode: String) -> String {
        let digits = fractionDigits(currencyCode)
        let major = toMajor(minor, currencyCode: currencyCode)
        if digits == 0 {
            return String(Int64(major.rounded()))
        }
        return String(format: "%.\(digits)f", locale: Locale(identifier: "en_US_POSIX"), major)
    }

    static func format(_ minor: Int64, currencyCode: String, locale: Locale = .current) -> String {
        let nf = NumberFormatter()
        nf.numberStyle = .currency
        nf.locale = locale
        nf.currencyCode = currencyCode
        return nf.string(from: NSNumber(value: toMajor(minor, currencyCode: currencyCode)))
            ?? formatMajorPlain(minor, currencyCode: currencyCode)
    }

    static func formatSigned(
        _ minor: Int64,
        direction: MoneyDirection,
        currencyCode: String,
        locale: Locale = .current
    ) -> String {
        let abs = format(Swift.abs(minor), currencyCode: currencyCode, locale: locale)
        return direction == .income ? "+\(abs)" : "−\(abs)"
    }
}
