package com.life.app.needle

import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.FinanceRanker
import com.life.app.data.Fuzzy
import com.life.app.data.HabitCatalog
import com.life.app.data.HabitDef
import com.life.app.data.HabitDomain
import com.life.app.data.LifeEvent
import com.life.app.data.Money
import com.life.app.data.MoneyDirection
import com.life.app.data.WantDomain
import com.life.app.data.WantStatus
import com.life.app.data.findHabitByName
import com.life.app.data.habitById
import com.life.app.data.toDef
import com.life.app.data.withCategory
import com.life.app.data.withEvent
import com.life.app.data.withHabit
import com.life.app.data.withPayee
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.JsonElement

object NeedleDispatcher {

    const val CONFIRM_BELOW = 0.75

    private val aliases: Map<String, String> = mapOf(
        "kreatin" to "creatine",
        "creatine" to "creatine",
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

    fun drafts(
        raw: String,
        data: AppData,
        response: NeedleResponse,
        today: LocalDate = LocalDate.now()
    ): List<NeedleDraft> {
        val calls = response.functionCalls
        if (calls.isEmpty()) {
            val body = raw.trim()
            if (body.isEmpty()) return emptyList()
            return listOf(
                NeedleDraft(
                    raw = raw,
                    kind = NeedleKind.NOTE,
                    label = body.take(42),
                    confidence = response.confidence ?: 0.4,
                    needsConfirm = false,
                    noteBody = body
                )
            )
        }
        return calls.map { call -> toDraft(raw, data, call, response.confidence, today) }
    }

    fun applyAll(
        data: AppData,
        drafts: List<NeedleDraft>,
        nowMs: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): AppData = drafts.fold(data) { acc, d -> apply(acc, d, nowMs, today) }

    fun apply(
        data: AppData,
        draft: NeedleDraft,
        nowMs: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): AppData = when (draft.kind) {
        NeedleKind.HABIT -> applyHabit(data, draft, nowMs)
        NeedleKind.NOTE -> applyNote(data, draft, nowMs, today)
        NeedleKind.FINANCE -> applyFinance(data, draft, nowMs)
        NeedleKind.WANT_DONE -> {
            val id = draft.wantEventId ?: return data
            WantDomain.withCompletedWant(data, id, nowMs)
        }
        NeedleKind.BALANCE -> data
    }

    private fun toDraft(
        raw: String,
        data: AppData,
        call: NeedleCall,
        confidence: Double?,
        today: LocalDate
    ): NeedleDraft {
        val args = call.arguments
        val conf = confidence
        val low = conf != null && conf < CONFIRM_BELOW
        return when (call.name) {
            "log_habit" -> habitDraft(raw, data, args.str("name").ifBlank { raw }, conf, low)
            "add_note" -> noteDraft(raw, args, today, conf, low)
            "log_finance" -> financeDraft(raw, data, args, conf, low)
            "complete_want" -> wantDraft(raw, data, args.str("text").ifBlank { raw }, conf, low)
            "query_balance" -> NeedleDraft(
                raw = raw,
                kind = NeedleKind.BALANCE,
                label = NeedleTools.formatBalance(data),
                confidence = conf ?: 1.0,
                needsConfirm = false,
                balanceLabel = NeedleTools.formatBalance(data)
            )
            else -> NeedleDraft(
                raw = raw,
                kind = NeedleKind.NOTE,
                label = raw.trim().take(42),
                confidence = conf ?: 0.3,
                needsConfirm = true,
                noteBody = raw.trim()
            )
        }
    }

    private fun habitDraft(
        raw: String,
        data: AppData,
        name: String,
        conf: Double?,
        low: Boolean
    ): NeedleDraft {
        val resolved = resolveHabitName(data, name)
        return NeedleDraft(
            raw = raw,
            kind = NeedleKind.HABIT,
            label = resolved.name,
            confidence = conf,
            needsConfirm = low,
            habitName = resolved.name
        )
    }

    private fun noteDraft(
        raw: String,
        args: Map<String, JsonElement>,
        today: LocalDate,
        conf: Double?,
        low: Boolean
    ): NeedleDraft {
        val body = args.str("body").ifBlank { raw }.trim()
        val due = NeedleTools.parseWantDate(args.str("want_date").takeIf { it.isNotEmpty() }, today)
        val want = due != null
        val label = if (want) "Want · $body" else body
        return NeedleDraft(
            raw = raw,
            kind = NeedleKind.NOTE,
            label = label.take(42),
            confidence = conf,
            needsConfirm = low && !want,
            noteBody = body,
            isWant = want,
            dueDate = due
        )
    }

    private fun financeDraft(
        raw: String,
        data: AppData,
        args: Map<String, JsonElement>,
        conf: Double?,
        low: Boolean
    ): NeedleDraft {
        val signRaw = NeedleTools.fold(args.str("sign"))
        val out = signRaw != "in" && signRaw != "+" && signRaw != "income"
        val amount = args.num("amount")
        val minor = amount?.let { Money.toMinor(it, data.currencyCode) }
        val payee = args.str("payee").trim()
        var category = args.str("category").trim()
        val dir = if (out) MoneyDirection.OUT else MoneyDirection.IN
        if (category.isEmpty()) {
            category = FinanceRanker.suggest(data, dir, payee, selectedPayee = payee)
                .suggestedCategory.orEmpty()
        }
        if (category.isEmpty() && !out) category = "Salary"
        val missing = minor == null || minor <= 0L
        val sign = if (out) "−" else "+"
        val who = payee.ifBlank { if (out) "Expense" else "Income" }
        val amountLabel = minor?.let { Money.format(it, data.currencyCode) } ?: "?"
        return NeedleDraft(
            raw = raw,
            kind = NeedleKind.FINANCE,
            label = "$sign$amountLabel $who",
            confidence = conf,
            needsConfirm = low || missing,
            signOut = out,
            amountMinor = minor,
            payee = payee,
            category = category
        )
    }

    private fun wantDraft(
        raw: String,
        data: AppData,
        text: String,
        conf: Double?,
        low: Boolean
    ): NeedleDraft {
        val hit = matchOpenWant(data, text)
        if (hit == null) {
            return NeedleDraft(
                raw = raw,
                kind = NeedleKind.NOTE,
                label = text.take(42),
                confidence = conf,
                needsConfirm = true,
                noteBody = text
            )
        }
        return NeedleDraft(
            raw = raw,
            kind = NeedleKind.WANT_DONE,
            label = "Done · ${hit.body}",
            confidence = conf,
            needsConfirm = low,
            wantEventId = hit.id
        )
    }

    data class ResolvedHabit(val name: String, val catalogId: String?)

    fun resolveHabitName(data: AppData, name: String): ResolvedHabit {
        val q = name.trim()
        if (q.isEmpty()) return ResolvedHabit(name, null)
        val folded = NeedleTools.fold(q)
        aliases[folded]?.let { target ->
            if (target.startsWith("cat_")) {
                val cat = HabitCatalog.byId(target)
                if (cat != null) {
                    val live = data.habitById(cat.id) ?: data.findHabitByName(cat.name)
                    return ResolvedHabit(live?.name ?: cat.name, cat.id)
                }
            } else {
                data.findHabitByName(target)?.let { return ResolvedHabit(it.name, it.id) }
                return ResolvedHabit(target.replaceFirstChar { it.titlecase() }, null)
            }
        }
        data.findHabitByName(q)?.let { return ResolvedHabit(it.name, it.id) }
        HabitCatalog.byName(q)?.let { return ResolvedHabit(it.name, it.id) }
        val liveBest = data.habits.filter { !it.archived }
            .maxByOrNull { Fuzzy.score(q, it.name) }
        if (liveBest != null && Fuzzy.score(q, liveBest.name) >= 0.45) {
            return ResolvedHabit(liveBest.name, liveBest.id)
        }
        val catBest = HabitCatalog.ALL.maxByOrNull { Fuzzy.score(q, it.name) }
        if (catBest != null && Fuzzy.score(q, catBest.name) >= 0.45) {
            return ResolvedHabit(catBest.name, catBest.id)
        }
        return ResolvedHabit(q, null)
    }

    fun matchOpenWant(data: AppData, query: String): LifeEvent? {
        val q = query.trim()
        if (q.isEmpty()) return null
        return data.events
            .filter { it.isOpenWant() }
            .map { it to maxOf(Fuzzy.score(q, it.body), Fuzzy.score(NeedleTools.fold(q), NeedleTools.fold(it.body))) }
            .filter { it.second >= 0.45 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun applyHabit(data: AppData, draft: NeedleDraft, nowMs: Long): AppData {
        val (next, habit) = materializeHabit(data, draft.habitName, nowMs)
        return next.withEvent(
            LifeEvent(
                id = UUID.randomUUID().toString(),
                kind = EventKind.HABIT,
                createdAt = nowMs,
                habitId = habit.id
            )
        )
    }

    internal fun materializeHabit(
        data: AppData,
        name: String,
        nowMs: Long
    ): Pair<AppData, HabitDef> {
        val resolved = resolveHabitName(data, name)
        resolved.catalogId?.let { id ->
            data.habitById(id)?.let { return data to it }
            HabitCatalog.byId(id)?.let { cat ->
                data.findHabitByName(cat.name)?.let { return data to it }
                val created = cat.toDef(createdAt = nowMs)
                return data.withHabit(created) to created
            }
        }
        data.findHabitByName(resolved.name)?.let { return data to it }
        val catalog = HabitCatalog.byName(resolved.name)
        val habit = if (catalog != null) {
            catalog.toDef(createdAt = nowMs)
        } else {
            HabitDef(
                id = HabitDomain.newId(),
                name = resolved.name.trim(),
                icon = "•",
                createdAt = nowMs
            )
        }
        return data.withHabit(habit) to habit
    }

    private fun applyNote(
        data: AppData,
        draft: NeedleDraft,
        nowMs: Long,
        today: LocalDate
    ): AppData {
        val text = draft.noteBody.trim().ifBlank { draft.raw.trim() }
        if (text.isEmpty()) return data
        val want = draft.isWant
        val due = WantDomain.resolveDueOnSave(want, draft.dueDate, today)
        return data.withEvent(
            LifeEvent(
                id = UUID.randomUUID().toString(),
                kind = EventKind.NOTE,
                createdAt = nowMs,
                body = text,
                dueDate = due,
                isWant = want,
                wantStatus = if (want) WantStatus.OPEN else WantStatus.NONE
            )
        )
    }

    private fun applyFinance(data: AppData, draft: NeedleDraft, nowMs: Long): AppData {
        val amount = draft.amountMinor ?: return data
        if (amount <= 0L) return data
        val dir = if (draft.signOut) MoneyDirection.OUT else MoneyDirection.IN
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

    private fun Map<String, JsonElement>.str(key: String): String = this[key].asString().trim()
    private fun Map<String, JsonElement>.num(key: String): Double? = this[key].asDouble()
}
