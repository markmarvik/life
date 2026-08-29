import Foundation

enum NeedleEngine: String { case pattern, cactus }

struct NeedleContext {
    var data: AppData
    var today: DateComponents
    var currencyCode: String
}

enum NeedleDecision {
    case habit(name: String, confidence: Double, engine: NeedleEngine)
    case note(body: String, tags: [String], wantDate: String?, confidence: Double, engine: NeedleEngine)
    case finance(direction: MoneyDirection, amountMinor: Int64?, payee: String, category: String, memo: String, confidence: Double, engine: NeedleEngine)
    case completeWant(text: String, confidence: Double, engine: NeedleEngine)
    case missWant(text: String, confidence: Double, engine: NeedleEngine)
    case balance(confidence: Double, engine: NeedleEngine)
    case unknown(text: String, confidence: Double, engine: NeedleEngine)

    var confidence: Double {
        switch self {
        case .habit(_, let c, _), .note(_, _, _, let c, _), .finance(_, _, _, _, _, let c, _),
             .completeWant(_, let c, _), .missWant(_, let c, _), .balance(let c, _), .unknown(_, let c, _):
            return c
        }
    }

    var label: String {
        switch self {
        case .habit(let n, _, _): return "Habit · \(n)"
        case .note(let b, _, let d, _, _): return d == nil ? "Note · \(b)" : "Want · \(b)"
        case .finance(let dir, let amt, let p, let c, _, _, _):
            let s = dir == .income ? "+" : "−"
            return "Finance · \(s)\(amt.map(String.init) ?? "?") \(p) \(c)".trimmingCharacters(in: .whitespaces)
        case .completeWant(let t, _, _): return "Done · \(t)"
        case .missWant(let t, _, _): return "Didn't · \(t)"
        case .balance: return "Balance"
        case .unknown: return "Not sure"
        }
    }

    var canAutoCommit: Bool {
        switch self {
        case .unknown, .missWant: return false
        case .finance(_, let amt, _, _, _, let c, _): return (amt ?? 0) > 0 && c >= 0.55
        default: return confidence >= 0.55
        }
    }
}

enum PatternNeedle {
    private static let spend = ["spent", "spend", "paid", "pay", "bought", "buy", "cost", "expense",
                                "potrosio", "potrosila", "platio", "platila", "kupio", "kupila"]
    private static let earn = ["earned", "income", "salary", "zaradio", "zaradila", "plata", "primio", "dobio"]
    private static let habitVerb = ["logged", "log", "did", "drank", "took", "popio", "popila", "uzeo", "uzela",
                                    "uradio", "uradila", "odradio", "odradila"]
    private static let noteVerb = ["note", "remember", "idea", "thought", "beleska", "zapamti"]
    private static let wantVerb = ["want", "commit", "zelim", "hocu"]
    private static let balanceW = ["balance", "how much", "stanje", "koliko imam"]
    private static let completeW = ["done with", "finished", "complete want", "zavrsio", "zavrsila"]
    private static let missW = ["didn't", "didnt", "skipped", "missed", "nisam", "propustio"]
    private static let currency = ["dinara", "dinar", "rsd", "eur", "euro", "usd", "dollars"]
    private static let categories: [(String, String)] = [
        ("food", "Food"), ("hrana", "Food"), ("grocer", "Groceries"),
        ("rent", "Rent"), ("transport", "Transport"), ("health", "Health"),
        ("fun", "Fun"), ("bill", "Bills"), ("salary", "Salary"), ("plata", "Salary")
    ]

    static func dispatch(_ text: String, ctx: NeedleContext) -> NeedleDecision {
        let raw = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if raw.isEmpty { return .unknown(text: "", confidence: 0, engine: .pattern) }
        let lower = fold(raw)
        if balanceW.contains(where: { lower.contains($0) }) {
            return .balance(confidence: 0.9, engine: .pattern)
        }
        if let m = miss(lower, raw: raw) { return m }
        if let m = complete(lower, raw: raw, data: ctx.data) { return m }
        if let m = finance(lower, raw: raw, ctx: ctx) { return m }
        if let m = note(lower, raw: raw, ctx: ctx) { return m }
        if let m = habit(lower, raw: raw, ctx: ctx) { return m }
        if raw.count <= 40 {
            return .habit(name: raw, confidence: 0.5, engine: .pattern)
        }
        return .note(body: raw, tags: [], wantDate: nil, confidence: 0.5, engine: .pattern)
    }

    private static func miss(_ lower: String, raw: String) -> NeedleDecision? {
        guard missW.contains(where: { lower.contains($0) }) else { return nil }
        let rest = strip(raw, missW)
        guard !rest.isEmpty else { return nil }
        return .missWant(text: rest, confidence: 0.7, engine: .pattern)
    }

    private static func complete(_ lower: String, raw: String, data: AppData) -> NeedleDecision? {
        guard completeW.contains(where: { lower.contains($0) }) || lower.hasPrefix("done ") else { return nil }
        let rest = strip(raw, completeW + ["done"])
        let open = data.events.filter { $0.isWant && $0.wantStatus == .open }
        guard !open.isEmpty else { return nil }
        let hit: LifeEvent
        if rest.isEmpty { hit = open[0] }
        else if let best = open.max(by: { Fuzzy.score(rest, $0.body) < Fuzzy.score(rest, $1.body) }),
                  Fuzzy.score(rest, best.body) >= 0.45 { hit = best }
        else { hit = open[0] }
        return .completeWant(text: hit.body, confidence: 0.75, engine: .pattern)
    }

    private static func finance(_ lower: String, raw: String, ctx: NeedleContext) -> NeedleDecision? {
        guard let amount = firstAmount(lower) else { return nil }
        let spent = spend.contains(where: { lower.contains($0) })
        let earned = earn.contains(where: { lower.contains($0) })
        if !spent && !earned && amount < 20 { return nil }
        let dir: MoneyDirection = (earned && !spent) ? .income : .expense
        let minor = Money.parseToMinor(String(amount), currencyCode: ctx.currencyCode)
        let cat = categories.first(where: { lower.contains($0.0) })?.1 ?? ""
        let payee = leftover(raw)
            .split(separator: " ")
            .map(String.init)
            .filter { tok in !categories.contains(where: { tok.contains($0.0) }) }
            .joined(separator: " ")
        return .finance(
            direction: dir,
            amountMinor: minor,
            payee: payee,
            category: cat,
            memo: raw,
            confidence: spent || earned ? 0.88 : 0.62,
            engine: .pattern
        )
    }

    private static func note(_ lower: String, raw: String, ctx: NeedleContext) -> NeedleDecision? {
        let isWant = wantVerb.contains(where: { lower.contains($0) })
        let isNote = noteVerb.contains(where: { lower.contains($0) })
        guard isWant || isNote else { return nil }
        let date = parseDate(lower, today: ctx.today)
        let body = strip(raw, wantVerb + noteVerb + ["today", "tomorrow", "danas", "sutra"])
        let wantDate = isWant ? (date ?? WantDomain.iso(ctx.today)) : date
        return .note(body: body.isEmpty ? raw : body, tags: [], wantDate: wantDate, confidence: 0.8, engine: .pattern)
    }

    private static func habit(_ lower: String, raw: String, ctx: NeedleContext) -> NeedleDecision? {
        let stripped = strip(raw, habitVerb)
        let name = stripped.isEmpty ? raw : stripped
        let verb = habitVerb.contains(where: { lower == $0 || lower.hasPrefix($0 + " ") })
        let known = bestHabit(name, data: ctx.data)
        if let known, verb || Fuzzy.score(name, known) >= 0.72 {
            return .habit(name: known, confidence: verb ? 0.9 : 0.78, engine: .pattern)
        }
        if verb && !name.isEmpty {
            return .habit(name: name, confidence: 0.82, engine: .pattern)
        }
        return nil
    }

    private static func bestHabit(_ query: String, data: AppData) -> String? {
        let names = data.habits.filter { !$0.archived }.map(\.name) + HabitCatalog.all.map(\.name)
        return names
            .map { ($0, Fuzzy.score(query, $0)) }
            .filter { $0.1 >= 0.62 }
            .max(by: { $0.1 < $1.1 })?
            .0
    }

    private static func firstAmount(_ lower: String) -> Double? {
        guard let r = try? NSRegularExpression(pattern: "(?<![a-z])(\\d+(?:[.,]\\d+)?)(?![a-z])") else { return nil }
        let ns = lower as NSString
        guard let m = r.firstMatch(in: lower, range: NSRange(location: 0, length: ns.length)) else { return nil }
        let s = ns.substring(with: m.range(at: 1)).replacingOccurrences(of: ",", with: ".")
        return Double(s)
    }

    private static func leftover(_ raw: String) -> String {
        let drop = Set(spend + earn + habitVerb + noteVerb + wantVerb + currency + ["a", "an", "the", "on", "for", "at", "to", "of", "and", "i", "my"])
        return fold(raw)
            .split(separator: " ")
            .map(String.init)
            .filter { tok in
                Double(tok) == nil && !drop.contains(tok)
            }
            .joined(separator: " ")
    }

    private static func strip(_ raw: String, _ verbs: [String]) -> String {
        var s = raw
        for v in verbs.sorted(by: { $0.count > $1.count }) {
            s = s.replacingOccurrences(of: v, with: " ", options: .caseInsensitive)
        }
        return s.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet.whitespacesAndNewlines.union(.punctuationCharacters))
    }

    private static func parseDate(_ lower: String, today: DateComponents) -> String? {
        if lower.contains("tomorrow") || lower.contains("sutra") {
            return WantDomain.iso(WantDomain.addDays(today, 1))
        }
        if lower.contains("today") || lower.contains("danas") {
            return WantDomain.iso(today)
        }
        return nil
    }

    private static func fold(_ s: String) -> String {
        s.lowercased()
            .replacingOccurrences(of: "š", with: "s")
            .replacingOccurrences(of: "č", with: "c")
            .replacingOccurrences(of: "ć", with: "c")
            .replacingOccurrences(of: "ž", with: "z")
            .replacingOccurrences(of: "đ", with: "d")
    }
}
