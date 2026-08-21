package com.life.app.data

data class ScoredPayee(
    val name: String,
    val score: Double
)

data class FinanceSuggestion(
    val payees: List<ScoredPayee>,
    val suggestedCategory: String?,
    val suggestedAmountMinor: Long?
)

object FinanceRanker {

    fun suggest(
        data: AppData,
        direction: MoneyDirection,
        query: String,
        nowMs: Long = System.currentTimeMillis(),
        selectedPayee: String = ""
    ): FinanceSuggestion {
        val q = query.trim()
        val txs = data.events.filter {
            it.kind == EventKind.FINANCE && it.direction == direction && it.payee.isNotBlank()
        }

        val scored = LinkedHashMap<String, Double>()
        fun bump(name: String, amount: Double) {
            val key = scored.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
            scored[key] = (scored[key] ?: 0.0) + amount
        }
        for (e in txs) {
            val hours = ((nowMs - e.createdAt).coerceAtLeast(0)).toDouble() / 3_600_000.0
            val recency = kotlin.math.exp(-hours / 72.0)
            bump(e.payee.trim(), 1.0 + recency)
        }
        for (p in data.payees) {
            if (p.isNotBlank()) bump(p, 0.1)
        }

        var payees = scored.entries
            .map { ScoredPayee(it.key, it.value) }
            .sortedByDescending { it.score }

        if (q.isNotEmpty()) {
            payees = payees.mapNotNull { p ->
                val f = Fuzzy.score(q, p.name)
                if (f < 0.45) null else p.copy(score = f * 4.0 + p.score)
            }.sortedByDescending { it.score }
        }

        val payeeHint = selectedPayee.trim().ifBlank { q }
        val forPayee = if (payeeHint.isNotEmpty()) {
            txs.filter { it.payee.equals(payeeHint, ignoreCase = true) }
                .sortedByDescending { it.createdAt }
        } else {
            emptyList()
        }

        val category = when {
            forPayee.isNotEmpty() -> forPayee.first().category.ifBlank { null }
            else -> mostUsedCategory(txs)
        }

        val amount = when {
            forPayee.isNotEmpty() -> modeAmount(forPayee.take(5)) ?: forPayee.first().amountMinor
            else -> null
        }

        return FinanceSuggestion(
            payees = payees.take(12),
            suggestedCategory = category,
            suggestedAmountMinor = amount
        )
    }

    private fun mostUsedCategory(txs: List<LifeEvent>): String? {
        val counts = txs.map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
        return counts.maxByOrNull { it.value }?.key
    }

    private fun modeAmount(txs: List<LifeEvent>): Long? {
        val counts = txs.mapNotNull { it.amountMinor }
            .groupingBy { it }
            .eachCount()
        return counts.maxByOrNull { it.value }?.key
    }
}
