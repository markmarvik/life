import Foundation

enum Fuzzy {
    /// 0.0 = no match, 1.0 = exact. Tuned for short habit/payee names.
    static func score(_ query: String, _ candidate: String) -> Double {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let c = candidate.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if q.isEmpty { return 1.0 }
        if c.isEmpty { return 0.0 }
        if c == q { return 1.0 }
        if c.hasPrefix(q) { return 0.92 }
        let tokens = c.split { ch in
            ch == " " || ch == "/" || ch == "_" || ch == "-"
        }
        if tokens.contains(where: { $0.hasPrefix(q) }) { return 0.85 }
        if c.contains(q) { return 0.75 }
        if isSubsequence(q, c) {
            let density = Double(q.count) / Double(max(c.count, 1))
            return 0.50 + 0.20 * density
        }
        if q.count <= 16 && c.count <= 40 {
            let d = levenshtein(q, c)
            let maxLen = max(q.count, c.count, 1)
            let sim = 1.0 - Double(d) / Double(maxLen)
            if sim >= 0.62 { return sim * 0.72 }
        }
        return 0.0
    }

    static func matches(_ query: String, _ candidate: String, minScore: Double = 0.45) -> Bool {
        score(query, candidate) >= minScore
    }

    private static func isSubsequence(_ q: String, _ c: String) -> Bool {
        var i = q.startIndex
        for ch in c {
            if i < q.endIndex && ch == q[i] {
                i = q.index(after: i)
            }
        }
        return i == q.endIndex
    }

    private static func levenshtein(_ a: String, _ b: String) -> Int {
        let aChars = Array(a)
        let bChars = Array(b)
        let n = aChars.count
        let m = bChars.count
        if n == 0 { return m }
        if m == 0 { return n }
        var prev = Array(0...m)
        var curr = Array(repeating: 0, count: m + 1)
        for i in 1...n {
            curr[0] = i
            for j in 1...m {
                let cost = aChars[i - 1] == bChars[j - 1] ? 0 : 1
                curr[j] = min(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            swap(&prev, &curr)
        }
        return prev[m]
    }
}
