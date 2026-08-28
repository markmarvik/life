package com.life.app.needle

import com.life.app.data.AppData
import com.life.app.data.Fuzzy
import com.life.app.data.Money
import com.life.app.data.findHabitByName
import java.time.LocalDate
import kotlinx.serialization.json.JsonPrimitive

/**
 * Keyword + fuzzy stand-in used when `liblife_needle.so` is not on the device
 * (unit tests, x86 emulator, first build before fetch-needle). Same tool
 * names as Needle so [NeedleDispatcher] is the only writer.
 */
object NeedleFallback {

    private val doneVerbs = listOf(
        "uradio", "uradila", "odradio", "odradila", "zavrsio", "zavrsila",
        "završio", "završila", "done", "completed", "finished", "logged",
        "popio", "popila"
    )
    private val incomeWords = listOf(
        "plata", "salary", "income", "paycheck", "primio", "primila", "earned", "bonus"
    )
    private val expenseWords = listOf(
        "platio", "platila", "kupio", "kupila", "racun", "račun", "paid", "bought",
        "spent", "bill", "trosak", "trošak", "expense"
    )
    private val moneyHints = listOf("rsd", "din", "dinara", "eur", "usd", "€", "$", "£")
    private val wantWords = listOf("want", "hocu", "hoću", "zelim", "želim", "sutra", "tomorrow")
    private val balanceWords = listOf(
        "balance", "koliko imam", "stanje", "cash", "how much", "budget"
    )

    fun parse(
        text: String,
        data: AppData,
        today: LocalDate = LocalDate.now()
    ): NeedleResponse {
        val raw = text.trim()
        if (raw.isEmpty()) return NeedleResponse(functionCalls = emptyList(), confidence = 1.0)
        val folded = NeedleTools.fold(raw)
        if (balanceWords.any { folded.contains(NeedleTools.fold(it)) }) {
            return NeedleResponse(
                functionCalls = listOf(NeedleCall("query_balance")),
                confidence = 0.95
            )
        }
        val amount = extractAmount(raw, data.currencyCode)
        val hasIncome = containsAny(folded, incomeWords) || raw.trim().startsWith("+")
        val hasExpense = containsAny(folded, expenseWords) || raw.trim().startsWith("-")
        val hasMoneyHint = containsAny(folded, moneyHints) || hasIncome || hasExpense
        if (amount != null && hasMoneyHint) {
            val sign = if (hasIncome && !hasExpense) "in" else "out"
            val payee = leftover(raw, incomeWords + expenseWords + moneyHints)
            val args = mutableMapOf(
                "sign" to JsonPrimitive(sign),
                "amount" to JsonPrimitive(Money.toMajor(amount, data.currencyCode))
            )
            if (payee.isNotEmpty()) args["payee"] = JsonPrimitive(payee)
            val cat = leftoverCategory(payee, folded)
            if (cat != null) args["category"] = JsonPrimitive(cat)
            return NeedleResponse(
                functionCalls = listOf(NeedleCall("log_finance", args)),
                confidence = 0.92
            )
        }

        val stripped = stripDoneVerbs(raw)
        val hadDone = NeedleTools.fold(stripped) != folded
        val wantHit = NeedleDispatcher.matchOpenWant(data, stripped)
        if (wantHit != null && hadDone) {
            return NeedleResponse(
                functionCalls = listOf(
                    NeedleCall("complete_want", mapOf("text" to JsonPrimitive(stripped.ifBlank { wantHit.body })))
                ),
                confidence = 0.9
            )
        }

        if (containsAny(folded, wantWords) && !hadDone) {
            val args = mutableMapOf("body" to JsonPrimitive(raw))
            val due = when {
                containsAny(folded, listOf("sutra", "tomorrow")) -> "tomorrow"
                containsAny(folded, listOf("danas", "today")) -> "today"
                else -> "today"
            }
            args["want_date"] = JsonPrimitive(due)
            return NeedleResponse(
                functionCalls = listOf(NeedleCall("add_note", args)),
                confidence = 0.9
            )
        }

        val habitName = habitGuess(stripped.ifBlank { raw }, data)
        if (habitName != null) {
            return NeedleResponse(
                functionCalls = listOf(
                    NeedleCall("log_habit", mapOf("name" to JsonPrimitive(habitName)))
                ),
                confidence = 0.88
            )
        }
        if (wantHit != null) {
            return NeedleResponse(
                functionCalls = listOf(
                    NeedleCall("complete_want", mapOf("text" to JsonPrimitive(wantHit.body)))
                ),
                confidence = 0.7
            )
        }

        return NeedleResponse(
            functionCalls = listOf(NeedleCall("add_note", mapOf("body" to JsonPrimitive(raw)))),
            confidence = 0.55
        )
    }

    private fun habitGuess(query: String, data: AppData): String? {
        val q = query.trim()
        if (q.isEmpty() || q.split(Regex("\\s+")).size > 6) return null
        val resolved = NeedleDispatcher.resolveHabitName(data, q)
        val aliasHit = NeedleTools.fold(q) in setOf(
            "kreatin", "creatine", "trening", "zube", "gym", "brush", "voda"
        )
        if (aliasHit) return resolved.name
        data.findHabitByName(resolved.name)?.let { return it.name }
        if (Fuzzy.score(q, resolved.name) >= 0.45 && resolved.catalogId != null) return resolved.name
        // Short novel name ("creatine") is a new habit, not a note.
        if (q.length in 3..24 && !q.contains('?') && q.split(Regex("\\s+")).size <= 3) {
            val looksLikeSentence = q.contains('.') || q.length > 28
            if (!looksLikeSentence && q.split(Regex("\\s+")).size <= 2) return resolved.name
        }
        return if (resolved.catalogId != null) resolved.name else null
    }

    internal fun extractAmount(text: String, currencyCode: String): Long? {
        val re = Regex("""(?<![\d])(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?\s*([kкK])?""")
        var best: Long? = null
        for (m in re.findAll(text)) {
            val whole = m.groupValues[1]
            val frac = m.groupValues[2]
            val k = m.groupValues[3]
            val asInt = whole.replace(".", "").replace(",", "")
            if (asInt.length == 4 && asInt.toIntOrNull() in 2000..2099 && k.isEmpty()) continue
            val major: Double = when {
                k.isNotEmpty() -> {
                    val n = whole.replace(".", "").replace(",", ".").toDoubleOrNull() ?: continue
                    n * 1000.0
                }
                frac.isNotEmpty() -> {
                    val head = whole.replace(".", "").replace(",", "")
                    "$head.$frac".toDoubleOrNull() ?: continue
                }
                else -> asInt.toDoubleOrNull() ?: continue
            }
            if (major < 0.0 || !major.isFinite()) continue
            best = Money.toMinor(major, currencyCode)
        }
        return best
    }

    private fun leftover(raw: String, strip: List<String>): String {
        var s = raw
        s = s.replace(Regex("""[+\-€$£]"""), " ")
        s = s.replace(Regex("""(?<!\d)(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?\s*([kкK])?"""), " ")
        val foldedStrip = strip.map { NeedleTools.fold(it) }.toSet()
        val cats = setOf("food", "hrana", "groceries", "transport", "rent", "health", "fun", "shopping", "bills")
        val kept = s.split(Regex("""\s+""")).filter { tok ->
            val f = NeedleTools.fold(tok).trim(',', '.', '!', '?')
            f.isNotEmpty() && f !in foldedStrip && f != "k" && f !in cats
        }
        return kept.joinToString(" ").trim()
    }

    private fun leftoverCategory(payee: String, folded: String): String? {
        val p = NeedleTools.fold(payee)
        return when {
            "lidl" in p || "maxi" in p || "idea" in p || "grocery" in folded || "hrana" in folded || "food" in folded -> "Food"
            "uber" in p || "bolt" in p -> "Transport"
            "rent" in folded || "stan" in folded -> "Rent"
            else -> null
        }
    }

    private fun stripDoneVerbs(text: String): String {
        var s = text.trim()
        val folded = NeedleTools.fold(s)
        for (v in doneVerbs.sortedByDescending { it.length }) {
            val fv = NeedleTools.fold(v)
            if (folded == fv) return ""
            if (folded.startsWith("$fv ")) {
                s = s.substring(v.length).trim()
                return s
            }
        }
        return s
    }

    private fun containsAny(foldedHaystack: String, words: List<String>): Boolean {
        val padded = " $foldedHaystack "
        return words.any { w ->
            val f = NeedleTools.fold(w)
            padded.contains(" $f ") || foldedHaystack.startsWith("$f ") ||
                foldedHaystack.endsWith(" $f") || foldedHaystack == f
        }
    }
}
