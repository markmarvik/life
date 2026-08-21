package com.life.app.data

import kotlin.math.max
import kotlin.math.min

object Fuzzy {

    /**
     * 0.0 = no match, 1.0 = exact. Tuned for short habit/payee names.
     */
    fun score(query: String, candidate: String): Double {
        val q = query.trim().lowercase()
        val c = candidate.trim().lowercase()
        if (q.isEmpty()) return 1.0
        if (c.isEmpty()) return 0.0
        if (c == q) return 1.0
        if (c.startsWith(q)) return 0.92
        val tokens = c.split(Regex("[\\s/_\\-]+"))
        if (tokens.any { it.startsWith(q) }) return 0.85
        if (c.contains(q)) return 0.75
        if (isSubsequence(q, c)) {
            val density = q.length.toDouble() / c.length.coerceAtLeast(1)
            return 0.50 + 0.20 * density
        }
        if (q.length <= 16 && c.length <= 40) {
            val d = levenshtein(q, c)
            val maxLen = max(q.length, c.length).coerceAtLeast(1)
            val sim = 1.0 - d.toDouble() / maxLen
            if (sim >= 0.62) return sim * 0.72
        }
        return 0.0
    }

    fun matches(query: String, candidate: String, minScore: Double = 0.45): Boolean =
        score(query, candidate) >= minScore

    private fun isSubsequence(q: String, c: String): Boolean {
        var i = 0
        for (ch in c) {
            if (i < q.length && ch == q[i]) i++
        }
        return i == q.length
    }

    private fun levenshtein(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }
}
