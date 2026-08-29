package com.life.app.needle

import com.life.app.data.AppData
import com.life.app.data.Fuzzy
import com.life.app.data.HabitCatalog
import com.life.app.data.Money
import com.life.app.data.MoneyDirection
import com.life.app.data.WantStatus
import java.time.LocalDate
import java.util.Locale

/**
 * Always-on offline dispatcher. No model, no network.
 * Same tool set as Cactus Needle; used until (and if) the on-device pack loads,
 * and as fallback when Cactus is unsure.
 *
 * Keep this file boring and tested — Serbian + English log phrases.
 */
object PatternNeedle {

    private val SPEND = listOf(
        "spent", "spend", "paid", "pay", "bought", "buy", "cost", "expense",
        "potrosio", "potrosila", "potrošio", "potrošila", "platio", "platila",
        "kupio", "kupila", "isplatio", "isplatila"
    )
    private val EARN = listOf(
        "earned", "earnt", "income", "salary", "received", "got paid",
        "zaradio", "zaradila", "plata", "primio", "primila", "dobio", "dobila"
    )
    private val HABIT_VERB = listOf(
        "logged", "log", "did", "drank", "took", "done",
        "popio", "popila", "uzeo", "uzela", "uradio", "uradila",
        "odradio", "odradila", "obavio", "obavila"
    )
    private val NOTE_VERB = listOf(
        "note", "remember", "idea", "thought", "beleska", "beleška",
        "zapamti", "zabelezi", "zabeleži", "misao"
    )
    private val WANT_VERB = listOf(
        "want", "commit", "remind me", "zelim", "želim", "hocu", "hoću"
    )
    private val BALANCE = listOf(
        "balance", "how much", "stanje", "koliko imam", "koliko ima"
    )
    private val COMPLETE = listOf(
        "done with", "finished", "complete want", "mark done",
        "zavrsio", "završio", "zavrsila", "završila", "obavio want"
    )
    private val MISS = listOf(
        "didn't", "didnt", "skipped", "missed", "nisam", "propustio", "propustila"
    )
    private val CURRENCY = listOf(
        "dinara", "dinar", "rsd", "eur", "euro", "euros", "usd", "dollars", "dollar",
        "gbp", "pounds", "nok", "sek", "dkk"
    )
    private val STOP = (
        SPEND + EARN + HABIT_VERB + NOTE_VERB + WANT_VERB + CURRENCY +
            listOf("a", "an", "the", "on", "for", "at", "to", "of", "and", "i", "my")
        ).toSet()

    private val CATEGORY_HINTS = mapOf(
        "food" to "Food", "hrana" to "Food", "eat" to "Food", "lunch" to "Food",
        "grocer" to "Groceries",
        "shop" to "Shopping", "rent" to "Rent", "kirija" to "Rent",
        "transport" to "Transport", "bus" to "Transport", "fuel" to "Transport",
        "health" to "Health", "fun" to "Fun", "bill" to "Bills", "racun" to "Bills",
        "račun" to "Bills", "salary" to "Salary", "plata" to "Salary"
    )

    fun dispatch(text: String, ctx: NeedleContext): NeedleDecision {
        val raw = text.trim()
        if (raw.isEmpty()) return NeedleDecision.Unknown("", engine = NeedleEngine.PATTERN)
        val lower = fold(raw)

        if (BALANCE.any { lower.contains(it) }) {
            return NeedleDecision.Balance(confidence = 0.9, engine = NeedleEngine.PATTERN)
        }
        missWant(lower, raw)?.let { return it }
        completeWant(lower, raw, ctx.data)?.let { return it }
        finance(lower, raw, ctx)?.let { return it }
        noteOrWant(lower, raw, ctx)?.let { return it }
        habit(lower, raw, ctx)?.let { return it }

        return if (raw.length <= 40) {
            NeedleDecision.Habit(name = raw.trim(), confidence = 0.5, engine = NeedleEngine.PATTERN)
        } else {
            NeedleDecision.Note(body = raw.trim(), confidence = 0.5, engine = NeedleEngine.PATTERN)
        }
    }

    private fun missWant(lower: String, raw: String): NeedleDecision? {
        if (MISS.none { lower.contains(it) }) return null
        val rest = stripVerbs(raw, MISS)
        if (rest.isBlank()) return null
        return NeedleDecision.MissWant(rest, confidence = 0.7, engine = NeedleEngine.PATTERN)
    }

    private fun completeWant(lower: String, raw: String, data: AppData): NeedleDecision? {
        if (COMPLETE.none { lower.contains(it) } && !lower.startsWith("done ")) return null
        val rest = stripVerbs(raw, COMPLETE + listOf("done"))
        val open = data.events.filter { it.isWant && it.wantStatus == WantStatus.OPEN }
        if (open.isEmpty()) return null
        val match = if (rest.isBlank()) {
            open.first()
        } else {
            open.maxBy { Fuzzy.score(rest, it.body) }.takeIf { Fuzzy.score(rest, it.body) >= 0.45 }
                ?: open.firstOrNull()
        } ?: return null
        return NeedleDecision.CompleteWant(match.body, confidence = 0.75, engine = NeedleEngine.PATTERN)
    }

    private fun finance(lower: String, raw: String, ctx: NeedleContext): NeedleDecision? {
        val amount = firstAmount(lower) ?: return null
        val spent = SPEND.any { lower.contains(it) }
        val earned = EARN.any { lower.contains(it) }
        val direction = when {
            earned && !spent -> MoneyDirection.IN
            else -> MoneyDirection.OUT
        }
        if (!spent && !earned && amount < 1.0) return null
        val confidence = when {
            spent || earned -> 0.88
            amount >= 20.0 -> 0.62
            else -> return null
        }
        val minor = Money.parseToMinor(amount.toString(), ctx.currencyCode)
        val category = CATEGORY_HINTS.entries.firstOrNull { lower.contains(it.key) }?.value.orEmpty()
        val payee = leftoverName(raw)
            .split(" ")
            .filter { tok -> CATEGORY_HINTS.keys.none { key -> tok.contains(key) } }
            .joinToString(" ")
            .trim()
        return NeedleDecision.Finance(
            direction = direction,
            amountMinor = minor,
            payee = payee,
            category = category,
            memo = raw.trim(),
            confidence = confidence,
            engine = NeedleEngine.PATTERN
        )
    }

    private fun noteOrWant(lower: String, raw: String, ctx: NeedleContext): NeedleDecision? {
        val isWant = WANT_VERB.any { lower.contains(it) }
        val isNote = NOTE_VERB.any { lower.contains(it) }
        if (!isWant && !isNote) return null
        val date = parseDate(lower, ctx.today)
        val body = stripVerbs(
            raw,
            WANT_VERB + NOTE_VERB + listOf("today", "tomorrow", "danas", "sutra")
        ).ifBlank { raw.trim() }
        return NeedleDecision.Note(
            body = body,
            wantDate = if (isWant) (date ?: ctx.today.toString()) else date,
            confidence = 0.8,
            engine = NeedleEngine.PATTERN
        )
    }

    private fun habit(lower: String, raw: String, ctx: NeedleContext): NeedleDecision? {
        val stripped = stripVerbs(raw, HABIT_VERB)
        val name = stripped.ifBlank { raw.trim() }
        val verb = HABIT_VERB.any { lower == it || lower.startsWith("$it ") || lower.contains(" $it ") }
        val known = bestHabitName(name, ctx.data)
        return when {
            known != null && (verb || Fuzzy.score(name, known) >= 0.72) ->
                NeedleDecision.Habit(known, confidence = if (verb) 0.9 else 0.78, engine = NeedleEngine.PATTERN)
            verb && name.isNotBlank() ->
                NeedleDecision.Habit(name, confidence = 0.82, engine = NeedleEngine.PATTERN)
            else -> null
        }
    }

    private fun bestHabitName(query: String, data: AppData): String? {
        val live = data.habits.filter { !it.archived }.map { it.name }
        val catalog = HabitCatalog.ALL.map { it.name }
        return (live + catalog)
            .map { it to Fuzzy.score(query, it) }
            .filter { it.second >= 0.62 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun firstAmount(lower: String): Double? {
        val m = Regex("""(?<![a-z])(\d+(?:[.,]\d+)?)(?![a-z])""").find(lower) ?: return null
        return m.groupValues[1].replace(',', '.').toDoubleOrNull()
    }

    private fun leftoverName(raw: String, extra: Set<String> = emptySet()): String {
        val drop = STOP + extra + CURRENCY
        return fold(raw)
            .split(Regex("""\s+"""))
            .filter { tok ->
                tok.isNotBlank() &&
                    tok.toDoubleOrNull() == null &&
                    drop.none { it == tok || (it.length > 3 && tok.contains(it)) }
            }
            .joinToString(" ")
            .trim()
    }

    private fun stripVerbs(raw: String, verbs: List<String>): String {
        var s = raw.trim()
        for (v in verbs.sortedByDescending { it.length }) {
            s = Regex("""(?i)\b${Regex.escape(v)}\b""").replace(s, " ")
        }
        return s.replace(Regex("""\s+"""), " ").trim(' ', ',', '.', '!', ':')
    }

    private fun parseDate(lower: String, today: LocalDate): String? = when {
        "tomorrow" in lower || "sutra" in lower -> today.plusDays(1).toString()
        "today" in lower || "danas" in lower -> today.toString()
        else -> Regex("""\d{4}-\d{2}-\d{2}""").find(lower)?.value
    }

    private fun fold(s: String): String =
        s.lowercase(Locale.ROOT)
            .replace('š', 's').replace('č', 'c').replace('ć', 'c')
            .replace('ž', 'z').replace('đ', 'd')
}
