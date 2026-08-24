package com.life.app.data

import java.time.LocalDate
import java.util.UUID

enum class CaptureConfidence { HIGH, MID, LOW }

enum class ParsedKind {
    HABIT, FINANCE, WANT_DONE, NOTE, RECURRING, PLANNED, SAVINGS
}

data class CaptureDraft(
    val raw: String,
    val kind: ParsedKind,
    val confidence: CaptureConfidence,
    val label: String,
    val habitName: String = "",
    val habitId: String? = null,
    val habitIcon: String = "",
    val habitWhy: String = "",
    val habitIsCreate: Boolean = false,
    val wantEventId: String? = null,
    val direction: MoneyDirection? = null,
    val amountMinor: Long? = null,
    val payee: String = "",
    val category: String = "",
    val noteBody: String = "",
    val tags: List<String> = emptyList(),
    val isWant: Boolean = false,
    val dueDate: String? = null,
    val cadence: RecurringCadence = RecurringCadence.MONTHLY,
    val until: String? = null,
    val plannedName: String = ""
) {
    fun toHabitSuggestion(): HabitSuggestion = HabitSuggestion(
        name = habitName,
        icon = habitIcon.ifBlank { "•" },
        why = habitWhy,
        habitId = habitId,
        score = when (confidence) {
            CaptureConfidence.HIGH -> 4.0
            CaptureConfidence.MID -> 2.0
            CaptureConfidence.LOW -> 0.5
        },
        isCreate = habitIsCreate
    )
}

/**
 * Fast on-device capture. No ML. Splits on `;` / newlines, then classifies
 * each clause with Fuzzy, the habit catalog, and keyword rules.
 *
 * High → log immediately. Mid → preview chips. Low → Note.
 */
object CaptureParser {

    const val HABIT_MIN = 0.45
    const val HIGH_MIN = 0.75

    private val doneVerbs = listOf(
        "uradio", "uradila", "odradio", "odradila", "zavrsio", "zavrsila",
        "završio", "završila", "done", "completed", "finished", "logged"
    )

    private val incomeWords = listOf(
        "plata", "salary", "income", "paycheck", "primio", "primila", "earned", "bonus"
    )
    private val expenseWords = listOf(
        "platio", "platila", "kupio", "kupila", "racun", "račun", "paid", "bought",
        "spent", "bill", "trosak", "trošak", "expense"
    )
    private val moneyHints = listOf(
        "rsd", "din", "dinara", "eur", "usd", "€", "$", "£"
    )
    private val recurringWords = listOf(
        "rata", "installment", "recurring", "pretplata", "subscription",
        "mesecno", "mesečno", "monthly", "weekly", "nedeljno", "yearly", "godisnje", "godišnje"
    )
    private val plannedWords = listOf(
        "hocu", "hoću", "zelim", "želim", "want", "planned", "planiram", "wishlist"
    )
    private val savingsWords = listOf(
        "stedim", "štedim", "stednja", "štednja", "savings", "saving"
    )
    private val wantNoteWords = listOf(
        "want", "hocu", "hoću", "zelim", "želim", "sutra", "tomorrow", "commitment"
    )

    /** Folded alias → catalog id. Exact alias hits are high-confidence habits. */
    private val aliases: Map<String, String> = mapOf(
        "trening" to "cat_strength",
        "vezba" to "cat_strength",
        "gym" to "cat_strength",
        "teretana" to "cat_strength",
        "strength" to "cat_strength",
        "zube" to "cat_brush",
        "zub" to "cat_brush",
        "cetkica" to "cat_brush",
        "floss" to "cat_brush",
        "brush" to "cat_brush",
        "pranje" to "cat_brush",
        "meditacija" to "cat_meditate",
        "meditate" to "cat_meditate",
        "voda" to "cat_hydrate",
        "hidracija" to "cat_hydrate",
        "hydrate" to "cat_hydrate",
        "setnja" to "cat_move",
        "walk" to "cat_move",
        "citanje" to "cat_read",
        "read" to "cat_read",
        "tus" to "cat_cold_shower",
        "shower" to "cat_cold_shower",
        "krevet" to "cat_make_bed",
        "bed" to "cat_make_bed",
        "sunce" to "cat_morning_sun",
        "sunlight" to "cat_morning_sun",
        "protein" to "cat_protein",
        "istezanje" to "cat_stretch",
        "stretch" to "cat_stretch",
        "zahvalnost" to "cat_gratitude",
        "gratitude" to "cat_gratitude"
    )

    private val payeeCategories = listOf(
        "lidl" to "Groceries",
        "maxi" to "Groceries",
        "idea" to "Groceries",
        "tempo" to "Groceries",
        "shop" to "Shopping",
        "uber" to "Transport",
        "bolt" to "Transport",
        "rent" to "Rent",
        "stan" to "Rent"
    )

    fun parse(
        text: String,
        data: AppData,
        nowMs: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): List<CaptureDraft> =
        text.split(Regex("[;\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { classify(it, data, nowMs, today) }

    private fun classify(
        raw: String,
        data: AppData,
        nowMs: Long,
        today: LocalDate
    ): CaptureDraft {
        val amount = extractAmount(raw, data.currencyCode)
        val folded = fold(raw)
        val hasRecurring = containsAny(folded, recurringWords)
        val hasPlanned = containsAny(folded, plannedWords)
        val hasSavings = containsAny(folded, savingsWords)
        val hasIncome = containsAny(folded, incomeWords) || raw.trim().startsWith("+")
        val hasExpense = containsAny(folded, expenseWords) ||
            (raw.trim().startsWith("-") && !raw.trim().startsWith("-+"))
        val hasMoneyHint = containsAny(folded, moneyHints) ||
            hasIncome || hasExpense || hasRecurring || hasSavings ||
            (hasPlanned && amount != null)

        if (amount != null && hasRecurring) {
            return recurringDraft(raw, amount, data.currencyCode, today)
        }
        if (amount != null && hasSavings) {
            return CaptureDraft(
                raw = raw,
                kind = ParsedKind.SAVINGS,
                confidence = CaptureConfidence.HIGH,
                label = "Savings ${Money.format(amount, data.currencyCode)}",
                amountMinor = amount
            )
        }
        if (amount != null && hasPlanned) {
            val name = leftoverName(
                raw,
                strip = plannedWords + moneyHints + listOf("do", "until", "k")
            ).ifBlank { "Planned" }
            return CaptureDraft(
                raw = raw,
                kind = ParsedKind.PLANNED,
                confidence = CaptureConfidence.HIGH,
                label = "$name ${Money.format(amount, data.currencyCode)}",
                amountMinor = amount,
                plannedName = name
            )
        }
        if (amount != null && hasMoneyHint) {
            return financeDraft(raw, amount, data, hasIncome, hasExpense)
        }

        val stripped = stripDoneVerbs(raw)
        val hadDoneVerb = fold(stripped) != fold(raw)
        val wantHit = matchOpenWant(stripped, data)
        if (wantHit != null && (hadDoneVerb || wantHit.second >= HIGH_MIN)) {
            val conf = if (wantHit.second >= HIGH_MIN || hadDoneVerb) {
                CaptureConfidence.HIGH
            } else {
                CaptureConfidence.MID
            }
            return CaptureDraft(
                raw = raw,
                kind = ParsedKind.WANT_DONE,
                confidence = conf,
                label = "Done · ${wantHit.first.body}",
                wantEventId = wantHit.first.id
            )
        }

        val habit = matchHabit(stripped, data, nowMs)
        if (habit != null) {
            if (wantHit != null && wantHit.second >= habit.score && hadDoneVerb) {
                return CaptureDraft(
                    raw = raw,
                    kind = ParsedKind.WANT_DONE,
                    confidence = CaptureConfidence.MID,
                    label = "Done · ${wantHit.first.body}",
                    wantEventId = wantHit.first.id
                )
            }
            val conf = when {
                habit.aliasExact -> CaptureConfidence.HIGH
                habit.score >= HIGH_MIN -> CaptureConfidence.HIGH
                else -> CaptureConfidence.MID
            }
            return CaptureDraft(
                raw = raw,
                kind = ParsedKind.HABIT,
                confidence = conf,
                label = habit.suggestion.name,
                habitName = habit.suggestion.name,
                habitId = habit.suggestion.habitId,
                habitIcon = habit.suggestion.icon,
                habitWhy = habit.suggestion.why,
                habitIsCreate = habit.suggestion.isCreate
            )
        }
        if (wantHit != null) {
            return CaptureDraft(
                raw = raw,
                kind = ParsedKind.WANT_DONE,
                confidence = CaptureConfidence.MID,
                label = "Done · ${wantHit.first.body}",
                wantEventId = wantHit.first.id
            )
        }

        return noteDraft(raw, data, today)
    }

    private data class HabitHit(
        val suggestion: HabitSuggestion,
        val score: Double,
        val aliasExact: Boolean
    )

    private fun matchHabit(query: String, data: AppData, nowMs: Long): HabitHit? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val foldedQ = fold(q)
        aliases[foldedQ]?.let { id ->
            val cat = HabitCatalog.byId(id)
            if (cat != null) {
                val live = data.habitById(id) ?: data.findHabitByName(cat.name)
                return HabitHit(
                    suggestion = HabitSuggestion(
                        name = live?.name ?: cat.name,
                        icon = live?.icon?.ifBlank { cat.icon } ?: cat.icon,
                        why = live?.displayWhy()?.ifBlank { cat.why } ?: cat.why,
                        habitId = live?.id ?: cat.id,
                        score = 1.0
                    ),
                    score = 1.0,
                    aliasExact = true
                )
            }
        }

        val ranked = HabitRanker.suggest(data, nowMs, q)
            .filter { !it.isCreate }
        val best = ranked.maxByOrNull { Fuzzy.score(q, it.name) } ?: return null
        val score = Fuzzy.score(q, best.name)
        if (score < HABIT_MIN) {
            val aliasBest = aliases.entries
                .map { it.key to Fuzzy.score(foldedQ, it.key) }
                .filter { it.second >= HABIT_MIN }
                .maxByOrNull { it.second }
            if (aliasBest != null) {
                val cat = HabitCatalog.byId(aliases.getValue(aliasBest.first)) ?: return null
                val live = data.habitById(cat.id) ?: data.findHabitByName(cat.name)
                return HabitHit(
                    suggestion = HabitSuggestion(
                        name = live?.name ?: cat.name,
                        icon = live?.icon?.ifBlank { cat.icon } ?: cat.icon,
                        why = live?.displayWhy()?.ifBlank { cat.why } ?: cat.why,
                        habitId = live?.id ?: cat.id,
                        score = aliasBest.second
                    ),
                    score = aliasBest.second,
                    aliasExact = aliasBest.second >= HIGH_MIN
                )
            }
            return null
        }
        return HabitHit(best, score, aliasExact = false)
    }

    private fun matchOpenWant(query: String, data: AppData): Pair<LifeEvent, Double>? {
        val q = query.trim()
        if (q.isEmpty()) return null
        return data.events
            .filter { it.isOpenWant() }
            .map { it to maxOf(Fuzzy.score(q, it.body), Fuzzy.score(fold(q), fold(it.body))) }
            .filter { it.second >= HABIT_MIN }
            .maxByOrNull { it.second }
    }

    private fun financeDraft(
        raw: String,
        amount: Long,
        data: AppData,
        hasIncome: Boolean,
        hasExpense: Boolean
    ): CaptureDraft {
        val direction = when {
            hasIncome && !hasExpense -> MoneyDirection.IN
            hasExpense && !hasIncome -> MoneyDirection.OUT
            raw.trim().startsWith("+") -> MoneyDirection.IN
            else -> MoneyDirection.OUT
        }
        val payee = leftoverName(
            raw,
            strip = incomeWords + expenseWords + moneyHints + listOf("k")
        )
        val ranked = FinanceRanker.suggest(data, direction, payee, selectedPayee = payee)
        val category = ranked.suggestedCategory
            ?: payeeCategories.firstOrNull { payee.lowercase().contains(it.first) }?.second
            ?: if (direction == MoneyDirection.IN) "Salary" else ""
        val sign = if (direction == MoneyDirection.IN) "+" else "−"
        val who = payee.ifBlank { if (direction == MoneyDirection.IN) "Income" else "Expense" }
        return CaptureDraft(
            raw = raw,
            kind = ParsedKind.FINANCE,
            confidence = CaptureConfidence.HIGH,
            label = "$sign${Money.format(amount, data.currencyCode)} $who",
            direction = direction,
            amountMinor = amount,
            payee = payee,
            category = category
        )
    }

    private fun recurringDraft(
        raw: String,
        amount: Long,
        currency: String,
        today: LocalDate
    ): CaptureDraft {
        val folded = fold(raw)
        val cadence = when {
            containsAny(folded, listOf("weekly", "nedeljno")) -> RecurringCadence.WEEKLY
            containsAny(folded, listOf("yearly", "godisnje", "godišnje")) -> RecurringCadence.YEARLY
            else -> RecurringCadence.MONTHLY
        }
        val until = parseUntil(raw, today)
        val name = leftoverName(
            raw,
            strip = recurringWords + moneyHints + listOf("do", "until", "k")
        ).ifBlank { "Rata" }
        return CaptureDraft(
            raw = raw,
            kind = ParsedKind.RECURRING,
            confidence = CaptureConfidence.HIGH,
            label = "$name ${Money.format(amount, currency)}/${cadence.name.lowercase()}",
            amountMinor = amount,
            direction = MoneyDirection.OUT,
            cadence = cadence,
            until = until,
            plannedName = name,
            payee = name
        )
    }

    private fun noteDraft(raw: String, data: AppData, today: LocalDate): CaptureDraft {
        val folded = fold(raw)
        val tags = extractTags(raw, data)
        val tomorrow = containsAny(folded, listOf("sutra", "tomorrow"))
        val want = tomorrow || containsAny(folded, wantNoteWords)
        val due = when {
            tomorrow -> today.plusDays(1).toString()
            want && containsAny(folded, listOf("danas", "today")) -> today.toString()
            want -> today.toString()
            else -> null
        }
        val body = raw.trim()
        val conf = if (want) CaptureConfidence.HIGH else CaptureConfidence.LOW
        val label = if (want) "Want · $body" else body
        return CaptureDraft(
            raw = raw,
            kind = ParsedKind.NOTE,
            confidence = conf,
            label = label.take(42),
            noteBody = body,
            tags = tags,
            isWant = want,
            dueDate = due
        )
    }

    internal fun extractAmount(text: String, currencyCode: String): Long? {
        val skippedYears = yearContexts(text)
        val re = Regex(
            """(?<!\d)(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?\s*([kкK])?"""
        )
        var best: Long? = null
        for (m in re.findAll(text)) {
            val whole = m.groupValues[1]
            val frac = m.groupValues[2]
            val k = m.groupValues[3]
            val asInt = whole.replace(".", "").replace(",", "")
            if (asInt.length == 4 && asInt.toIntOrNull() in 2000..2099 && k.isEmpty()) {
                if (skippedYears.any { it == asInt.toInt() }) continue
                if (best != null) continue
            }
            val major: Double
            val compactThousands = frac.isEmpty() &&
                whole.contains(Regex("[.,]")) &&
                whole.substringAfterLast('.', "").substringAfterLast(',', "").let {
                    // 2.300 / 80.000 → thousands; 4.50 handled via frac group
                    val lastSep = maxOf(whole.lastIndexOf('.'), whole.lastIndexOf(','))
                    lastSep >= 0 && whole.length - lastSep - 1 == 3
                }
            major = when {
                k.isNotEmpty() -> {
                    val n = whole.replace(".", "").replace(",", ".").toDoubleOrNull() ?: continue
                    n * 1000.0
                }
                compactThousands -> asInt.toDoubleOrNull() ?: continue
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

    private fun yearContexts(text: String): Set<Int> {
        val years = mutableSetOf<Int>()
        val re = Regex("""(?:do|until|till)\s+(\d{4})""", RegexOption.IGNORE_CASE)
        for (m in re.findAll(text)) {
            m.groupValues[1].toIntOrNull()?.let { years += it }
        }
        return years
    }

    internal fun parseUntil(text: String, today: LocalDate): String? {
        val year = Regex("""(?:do|until|till)\s+(\d{4})(?:\s|$|[^\d])""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues?.get(1)
            ?.toIntOrNull()
        if (year != null) return LocalDate.of(year, 12, 31).toString()
        val iso = Regex("""(?:do|until|till)\s+(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues?.get(1)
        if (iso != null) return iso
        val dotted = Regex("""(?:do|until|till)\s+(\d{1,2})[./](\d{4})""", RegexOption.IGNORE_CASE)
            .find(text)
        if (dotted != null) {
            val month = dotted.groupValues[1].toInt()
            val y = dotted.groupValues[2].toInt()
            if (month in 1..12) {
                return LocalDate.of(y, month, 1).lengthOfMonth().let {
                    LocalDate.of(y, month, it).toString()
                }
            }
        }
        return null
    }

    private fun leftoverName(raw: String, strip: List<String>): String {
        var s = raw
        s = s.replace(Regex("""[+\-€$£]"""), " ")
        s = s.replace(
            Regex("""(?<!\d)(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?\s*([kкK])?"""),
            " "
        )
        s = s.replace(Regex("""(?:do|until|till)\s+\d{4}(?:-\d{2}-\d{2})?""", RegexOption.IGNORE_CASE), " ")
        val foldedStrip = strip.map { fold(it) }.toSet()
        val kept = s.split(Regex("""\s+""")).filter { tok ->
            val f = fold(tok).trim(',', '.', '!', '?')
            f.isNotEmpty() && f !in foldedStrip && f != "k"
        }
        return kept.joinToString(" ").trim()
    }

    private fun extractTags(raw: String, data: AppData): List<String> {
        val hash = Regex("""#(\S+)""").findAll(raw).map { it.groupValues[1] }.toList()
        val available = data.availableTags()
        val words = raw.split(Regex("""\s+""")).map { it.trim('#', ',', '.', '!') }.filter { it.length >= 3 }
        val named = words.mapNotNull { w ->
            available.firstOrNull { it.equals(w, ignoreCase = true) }
        }
        return (hash + named).distinctBy { it.lowercase() }
    }

    private fun stripDoneVerbs(text: String): String {
        var s = text.trim()
        val folded = fold(s)
        for (v in doneVerbs.sortedByDescending { it.length }) {
            val fv = fold(v)
            if (folded == fv) return ""
            if (folded.startsWith("$fv ")) {
                s = s.substring(v.length).trim()
                return s
            }
        }
        return s
    }

    internal fun fold(s: String): String =
        s.lowercase()
            .replace('č', 'c')
            .replace('ć', 'c')
            .replace('š', 's')
            .replace('ž', 'z')
            .replace("đ", "dj")
            .replace("đ".uppercase(), "dj")

    private fun containsAny(foldedHaystack: String, words: List<String>): Boolean {
        val padded = " $foldedHaystack "
        return words.any { w ->
            val f = fold(w)
            if (f.length == 1) padded.contains(f) else
                padded.contains(" $f ") || foldedHaystack.startsWith("$f ") ||
                    foldedHaystack.endsWith(" $f") || foldedHaystack == f
        }
    }
}

object CaptureApply {

    fun apply(
        data: AppData,
        draft: CaptureDraft,
        nowMs: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): AppData = when (draft.kind) {
        ParsedKind.HABIT -> applyHabit(data, draft, nowMs)
        ParsedKind.FINANCE -> applyFinance(data, draft, nowMs)
        ParsedKind.WANT_DONE -> {
            val id = draft.wantEventId ?: return data
            WantDomain.withCompletedWant(data, id, nowMs)
        }
        ParsedKind.NOTE -> applyNote(data, draft, nowMs, today)
        ParsedKind.RECURRING -> applyRecurring(data, draft, nowMs)
        ParsedKind.PLANNED -> applyPlanned(data, draft, nowMs)
        ParsedKind.SAVINGS -> data.copy(monthlySavingsMinor = draft.amountMinor ?: data.monthlySavingsMinor)
    }

    fun applyAll(
        data: AppData,
        drafts: List<CaptureDraft>,
        nowMs: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): AppData = drafts.fold(data) { acc, d -> apply(acc, d, nowMs, today) }

    private fun applyHabit(data: AppData, draft: CaptureDraft, nowMs: Long): AppData {
        val suggestion = draft.toHabitSuggestion()
        val (next, habit) = resolveHabit(data, suggestion, nowMs)
        return next.withEvent(
            LifeEvent(
                id = UUID.randomUUID().toString(),
                kind = EventKind.HABIT,
                createdAt = nowMs,
                habitId = habit.id
            )
        )
    }

    private fun applyFinance(data: AppData, draft: CaptureDraft, nowMs: Long): AppData {
        val amount = draft.amountMinor ?: return data
        if (amount <= 0L) return data
        val dir = draft.direction ?: MoneyDirection.OUT
        return data.withPayee(draft.payee)
            .withCategory(draft.category)
            .withEvent(
                LifeEvent(
                    id = UUID.randomUUID().toString(),
                    kind = EventKind.FINANCE,
                    createdAt = nowMs,
                    amountMinor = amount,
                    direction = dir,
                    payee = draft.payee.trim(),
                    category = draft.category.trim()
                )
            )
    }

    private fun applyNote(
        data: AppData,
        draft: CaptureDraft,
        nowMs: Long,
        today: LocalDate
    ): AppData {
        val text = draft.noteBody.trim().ifBlank { draft.raw.trim() }
        if (text.isEmpty()) return data
        var next = data
        for (t in draft.tags) next = next.withTag(t)
        val want = draft.isWant
        val due = WantDomain.resolveDueOnSave(want, draft.dueDate, today)
        return next.withEvent(
            LifeEvent(
                id = UUID.randomUUID().toString(),
                kind = EventKind.NOTE,
                createdAt = nowMs,
                body = text,
                tags = draft.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                dueDate = due,
                isWant = want,
                wantStatus = if (want) WantStatus.OPEN else WantStatus.NONE
            )
        )
    }

    private fun applyRecurring(data: AppData, draft: CaptureDraft, nowMs: Long): AppData {
        val amount = draft.amountMinor ?: return data
        if (amount <= 0L) return data
        val def = RecurringDef(
            id = "r_${UUID.randomUUID().toString().take(8)}",
            name = draft.plannedName.ifBlank { draft.payee }.ifBlank { "Rata" },
            amountMinor = amount,
            direction = draft.direction ?: MoneyDirection.OUT,
            cadence = draft.cadence,
            until = draft.until,
            createdAt = nowMs
        )
        return data.copy(recurring = data.recurring + def)
    }

    private fun applyPlanned(data: AppData, draft: CaptureDraft, nowMs: Long): AppData {
        val amount = draft.amountMinor ?: return data
        if (amount <= 0L) return data
        val item = PlannedItem(
            id = "p_${UUID.randomUUID().toString().take(8)}",
            name = draft.plannedName.ifBlank { "Planned" },
            amountMinor = amount,
            createdAt = nowMs
        )
        return data.copy(planned = data.planned + item)
    }

    internal fun resolveHabit(
        data: AppData,
        suggestion: HabitSuggestion,
        nowMs: Long
    ): Pair<AppData, HabitDef> {
        if (!suggestion.isCreate) {
            suggestion.habitId?.let { id ->
                data.habitById(id)?.let { return data to it }
                HabitCatalog.byId(id)?.let { cat ->
                    data.findHabitByName(cat.name)?.let { return data to it }
                    val created = cat.toDef(createdAt = nowMs)
                    return data.withHabit(created) to created
                }
            }
        }
        data.findHabitByName(suggestion.name)?.let { return data to it }
        val catalog = HabitCatalog.byName(suggestion.name)
        val habit = if (catalog != null) {
            catalog.toDef(createdAt = nowMs)
        } else {
            HabitDef(
                id = HabitDomain.newId(),
                name = suggestion.name.trim(),
                icon = suggestion.icon.takeUnless { it == "＋" || it.isBlank() } ?: "•",
                why = suggestion.why.takeUnless { it == "Create and log" }.orEmpty(),
                createdAt = nowMs
            )
        }
        return data.withHabit(habit) to habit
    }
}
