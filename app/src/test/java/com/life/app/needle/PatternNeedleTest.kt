package com.life.app.needle

import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.LifeEvent
import com.life.app.data.MoneyDirection
import com.life.app.data.WantStatus
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatternNeedleTest {

    private val today = LocalDate.of(2026, 8, 29)
    private val ctx = NeedleContext(
        data = AppData(currencyCode = "RSD"),
        today = today,
        currencyCode = "RSD"
    )

    @Test
    fun popioKreatinLogsHabit() {
        val d = PatternNeedle.dispatch("popio kreatin", ctx)
        assertTrue(d is NeedleDecision.Habit)
        assertEquals("kreatin", (d as NeedleDecision.Habit).name.lowercase())
        assertTrue(d.canAutoCommit())
    }

    @Test
    fun loggedCreatineLogsHabit() {
        val d = PatternNeedle.dispatch("logged creatine", ctx)
        assertTrue(d is NeedleDecision.Habit)
        assertEquals("creatine", (d as NeedleDecision.Habit).name.lowercase())
    }

    @Test
    fun spentLidFoodIsFinanceOut() {
        val d = PatternNeedle.dispatch("spent 450 dinara lidl food", ctx)
        assertTrue(d is NeedleDecision.Finance)
        val f = d as NeedleDecision.Finance
        assertEquals(MoneyDirection.OUT, f.direction)
        assertEquals(45000L, f.amountMinor) // RSD has 2 fraction digits
        assertTrue(f.payee.contains("lidl", ignoreCase = true))
        assertEquals("Food", f.category)
        assertTrue(f.canAutoCommit())
    }

    @Test
    fun noteWithTomorrowWant() {
        val d = PatternNeedle.dispatch("want call dentist tomorrow", ctx)
        assertTrue(d is NeedleDecision.Note)
        val n = d as NeedleDecision.Note
        assertEquals(today.plusDays(1).toString(), n.wantDate)
        assertTrue(n.body.contains("dentist", ignoreCase = true))
    }

    @Test
    fun balanceQuery() {
        val d = PatternNeedle.dispatch("koliko imam", ctx)
        assertTrue(d is NeedleDecision.Balance)
    }

    @Test
    fun completeOpenWant() {
        val data = AppData(
            events = listOf(
                LifeEvent(
                    id = "w1",
                    kind = EventKind.NOTE,
                    createdAt = 1,
                    body = "Call dentist",
                    isWant = true,
                    wantStatus = WantStatus.OPEN,
                    dueDate = "2026-08-29"
                )
            )
        )
        val d = PatternNeedle.dispatch("done call dentist", NeedleContext(data, today, "RSD"))
        assertTrue(d is NeedleDecision.CompleteWant)
    }

    @Test
    fun knownCatalogHabitWithoutVerb() {
        val d = PatternNeedle.dispatch("Meditate", ctx)
        assertTrue(d is NeedleDecision.Habit)
        assertEquals("Meditate", (d as NeedleDecision.Habit).name)
    }
}
