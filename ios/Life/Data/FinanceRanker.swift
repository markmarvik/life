import Foundation

struct ScoredPayee: Equatable {
    var name: String
    var score: Double
}

struct FinanceSuggestion: Equatable {
    var payees: [ScoredPayee]
    var suggestedCategory: String?
    var suggestedAmountMinor: Int64?
}

enum FinanceRanker {
    static func suggest(
        _ data: AppData,
        direction: MoneyDirection,
        query: String,
        nowMs now: Int64 = nowMs(),
        selectedPayee: String = ""
    ) -> FinanceSuggestion {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let txs = data.events.filter {
            $0.kind == .finance && $0.direction == direction && !$0.payee.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }

        var scored: [String: Double] = [:]
        func bump(_ name: String, _ amount: Double) {
            if let key = scored.keys.first(where: { $0.caseInsensitiveCompare(name) == .orderedSame }) {
                scored[key, default: 0] += amount
            } else {
                scored[name] = amount
            }
        }
        for e in txs {
            let hours = Double(max(now - e.createdAt, 0)) / 3_600_000.0
            let recency = exp(-hours / 72.0)
            bump(e.payee.trimmingCharacters(in: .whitespacesAndNewlines), 1.0 + recency)
        }
        for p in data.payees where !p.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            bump(p, 0.1)
        }

        var payees = scored.map { ScoredPayee(name: $0.key, score: $0.value) }
            .sorted { $0.score > $1.score }

        if !q.isEmpty {
            payees = payees.compactMap { p in
                let f = Fuzzy.score(q, p.name)
                if f < 0.45 { return nil }
                return ScoredPayee(name: p.name, score: f * 4.0 + p.score)
            }.sorted { $0.score > $1.score }
        }

        let payeeHint = selectedPayee.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? q
            : selectedPayee.trimmingCharacters(in: .whitespacesAndNewlines)
        let forPayee: [LifeEvent]
        if !payeeHint.isEmpty {
            forPayee = txs.filter { $0.payee.caseInsensitiveCompare(payeeHint) == .orderedSame }
                .sorted { $0.createdAt > $1.createdAt }
        } else {
            forPayee = []
        }

        let category: String?
        if let first = forPayee.first {
            let cat = first.category.trimmingCharacters(in: .whitespacesAndNewlines)
            category = cat.isEmpty ? nil : cat
        } else {
            category = mostUsedCategory(txs)
        }

        let amount: Int64?
        if !forPayee.isEmpty {
            amount = modeAmount(Array(forPayee.prefix(5))) ?? forPayee.first?.amountMinor
        } else {
            amount = nil
        }

        return FinanceSuggestion(
            payees: Array(payees.prefix(12)),
            suggestedCategory: category,
            suggestedAmountMinor: amount
        )
    }

    private static func mostUsedCategory(_ txs: [LifeEvent]) -> String? {
        var counts: [String: Int] = [:]
        for t in txs {
            let c = t.category.trimmingCharacters(in: .whitespacesAndNewlines)
            if !c.isEmpty { counts[c, default: 0] += 1 }
        }
        return counts.max { $0.value < $1.value }?.key
    }

    private static func modeAmount(_ txs: [LifeEvent]) -> Int64? {
        var counts: [Int64: Int] = [:]
        for t in txs {
            if let a = t.amountMinor { counts[a, default: 0] += 1 }
        }
        return counts.max { $0.value < $1.value }?.key
    }
}
