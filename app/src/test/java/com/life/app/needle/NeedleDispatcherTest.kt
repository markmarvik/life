package com.life.app.needle

import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.LifeEvent
import com.life.app.data.Money
import com.life.app.data.MoneyDirection
import com.life.app.data.WantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NeedleDispatcherTest {

    private val today = LocalDate.of(2026, 8, 28)
    private val now = 1_724_832_000_000L
    private val data = AppData(currencyCode = "RSD")

    private fun drafts(text: String, app: AppData = data): List<NeedleDraft> {
        val response = NeedleFallback.parse(text, app, today)
        return NeedleDispatcher.drafts(text, app, response, today)
    }

    @Test
    fun popioKreatinLogsCreatine() {
        val d = drafts("popio kreatin").single()
        assertEquals(NeedleKind.HABIT, d.kind)
        assertEquals("Creatine", d.habitName)
        assertFalse(d.needsConfirm)
    }

    @Test
    fun loggedCreatineLogsHabit() {
        val d = drafts("logged creatine").single()
        assertEquals(NeedleKind.HABIT, d.kind)
        assertEquals("Creatine", d.habitName)
    }

    @Test
    fun spentLidlWritesExpense() {
        val d = drafts("spent 450 dinara lidl food").single()
        assertEquals(NeedleKind.FINANCE, d.kind)
        assertTrue(d.signOut)
        assertEquals(Money.toMinor(450.0, "RSD"), d.amountMinor)
        assertEquals("lidl", d.payee.lowercase())
        assertEquals("Food", d.category)
    }

    @Test
    fun sutraBecomesWantTomorrow() {
        val d = drafts("sutra zubar").single()
        assertEquals(NeedleKind.NOTE, d.kind)
        assertTrue(d.isWant)
        assertEquals(today.plusDays(1).toString(), d.dueDate)
    }

    @Test
    fun uradioCompletesMatchingWant() {
        val want = LifeEvent(
            id = "w1",
            kind = EventKind.NOTE,
            createdAt = 1L,
            body = "zubar",
            isWant = true,
            wantStatus = WantStatus.OPEN
        )
        val d = drafts("uradio zubar", data.copy(events = listOf(want))).single()
        assertEquals(NeedleKind.WANT_DONE, d.kind)
        assertEquals("w1", d.wantEventId)
    }

    @Test
    fun queryBalanceDoesNotWrite() {
        val d = drafts("koliko imam").single()
        assertEquals(NeedleKind.BALANCE, d.kind)
        val next = NeedleDispatcher.apply(data, d, nowMs = now, today = today)
        assertTrue(next.events.isEmpty())
    }

    @Test
    fun applyWritesHabitAndExpense() {
        val habit = drafts("popio kreatin")
        val money = drafts("spent 450 dinara lidl food")
        val next = NeedleDispatcher.applyAll(data, habit + money, nowMs = now, today = today)
        assertEquals(1, next.habits.count { it.name.equals("Creatine", ignoreCase = true) })
        assertEquals(1, next.events.count { it.kind == EventKind.HABIT })
        val tx = next.events.first { it.kind == EventKind.FINANCE }
        assertEquals(MoneyDirection.OUT, tx.direction)
        assertEquals(Money.toMinor(450.0, "RSD"), tx.amountMinor)
    }

    @Test
    fun treningMapsToStrength() {
        val d = drafts("trening").single()
        assertEquals(NeedleKind.HABIT, d.kind)
        assertEquals("Strength training", d.habitName)
    }

    @Test
    fun unknownSentenceIsNote() {
        val d = drafts("random thought about clouds").single()
        assertEquals(NeedleKind.NOTE, d.kind)
        assertEquals("random thought about clouds", d.noteBody)
    }

    @Test
    fun interpretSplitsClauses() {
        val drafts = NeedleEngine.interpret("trening; spent 450 dinara lidl food", data, today)
        assertEquals(2, drafts.size)
        assertEquals(NeedleKind.HABIT, drafts[0].kind)
        assertEquals("Strength training", drafts[0].habitName)
        assertEquals(NeedleKind.FINANCE, drafts[1].kind)
        assertTrue(drafts[1].signOut)
        assertEquals(Money.toMinor(450.0, "RSD"), drafts[1].amountMinor)
    }
}
