package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class HabitDomainTest {

    private fun habit(id: String, name: String, icon: String = "•") =
        HabitDef(id, name, icon = icon, createdAt = 1L)

    private fun log(id: String, habitId: String, at: Long = 1L) = LifeEvent(
        id = id,
        kind = EventKind.HABIT,
        createdAt = at,
        habitId = habitId
    )

    @Test
    fun editRenamesHabitAndKeepsEveryLogOnSameId() {
        val data = AppData(
            habits = listOf(habit("h1", "Meditate", "🪷"), habit("h2", "Run")),
            events = listOf(
                log("e1", "h1"),
                log("e2", "h1", 2L),
                log("e3", "h2", 3L),
                LifeEvent("n1", EventKind.NOTE, 4L, body = "thought")
            )
        )
        val next = HabitDomain.edit(data, "h1", "  Sit quietly  ", "🙏")
        assertEquals("Sit quietly", next.habitById("h1")?.name)
        assertEquals("🙏", next.habitById("h1")?.icon)
        assertEquals(listOf("h1", "h1", "h2"), next.events.filter { it.kind == EventKind.HABIT }.map { it.habitId })
        assertEquals("thought", next.events.first { it.id == "n1" }.body)
        assertEquals("Run", next.habitById("h2")?.name)
    }

    @Test
    fun editMergesLogsWhenNameAlreadyExists() {
        val data = AppData(
            habits = listOf(habit("h1", "Meditate"), habit("h2", "Run")),
            events = listOf(log("e1", "h1"), log("e2", "h1", 2L), log("e3", "h2", 3L))
        )
        val next = HabitDomain.edit(data, "h1", "run", "•")
        assertNull(next.habitById("h1"))
        assertEquals(1, next.habits.size)
        assertEquals(listOf("h2", "h2", "h2"), next.events.map { it.habitId })
    }

    @Test
    fun editNoopsWhenHabitMissingOrNameBlank() {
        val data = AppData(habits = listOf(habit("h1", "Meditate")))
        assertEquals(data, HabitDomain.edit(data, "nope", "Sit", "•"))
        assertEquals("Meditate", HabitDomain.edit(data, "h1", "   ", "•").habitById("h1")?.name)
    }

    @Test
    fun saveAsNewCreatesSeparateHabitAndLeavesLogs() {
        val data = AppData(
            habits = listOf(habit("h1", "Meditate", "🪷")),
            events = listOf(log("e1", "h1"), log("e2", "h1", 2L))
        )
        val next = HabitDomain.saveAsNew(data, "Sit", "🙏", nowMs = 9L)
        assertEquals("Meditate", next.habitById("h1")?.name)
        val created = next.findHabitByName("Sit")
        assertEquals("Sit", created?.name)
        assertEquals("🙏", created?.icon)
        assertNotEquals("h1", created?.id)
        assertEquals(listOf("h1", "h1"), next.events.map { it.habitId })
    }

    @Test
    fun saveAsNewCanReassignOneLog() {
        val data = AppData(
            habits = listOf(habit("h1", "Meditate")),
            events = listOf(log("e1", "h1"), log("e2", "h1", 2L))
        )
        val next = HabitDomain.saveAsNew(data, "Sit", "🙏", reassignEventId = "e1", nowMs = 9L)
        val sit = next.findHabitByName("Sit")!!
        assertEquals(sit.id, next.events.first { it.id == "e1" }.habitId)
        assertEquals("h1", next.events.first { it.id == "e2" }.habitId)
    }

    @Test
    fun saveAsNewReusesExistingNameInsteadOfDuplicating() {
        val data = AppData(
            habits = listOf(habit("h1", "Meditate"), habit("h2", "Run")),
            events = listOf(log("e1", "h1"))
        )
        val next = HabitDomain.saveAsNew(data, "Run", "•", reassignEventId = "e1")
        assertEquals(2, next.habits.size)
        assertEquals("h2", next.events.single().habitId)
    }

    @Test
    fun editClaimsCatalogSlotWithoutLeavingDefault() {
        val data = AppData()
        val next = HabitDomain.edit(data, "cat_brush", "Brush teeth", "🪥", "Dental foundation.", nowMs = 9L)
        val claimed = next.habitById("cat_brush")
        assertEquals("Brush teeth", claimed?.name)
        assertEquals("Dental foundation.", claimed?.why)
        assertEquals(1, next.habits.size)
        val ranked = HabitRanker.suggest(next, 9L, "", ZoneOffset.UTC)
        assertEquals(1, ranked.count { it.habitId == "cat_brush" })
        assertTrue(ranked.none { it.name.equals("Brush + floss", ignoreCase = true) })
        assertEquals("Dental foundation.", ranked.first { it.habitId == "cat_brush" }.why)
    }

    @Test
    fun defForEditUsesCatalogBeforeFirstLog() {
        val def = HabitDomain.defForEdit(AppData(), "cat_brush")
        assertEquals("Brush + floss", def?.name)
        assertEquals("Dental foundation.", def?.why)
        assertEquals("cat_brush", def?.id)
    }

    @Test
    fun logCountCountsOnlyThatHabit() {
        val data = AppData(
            events = listOf(
                log("e1", "h1"),
                log("e2", "h1", 2L),
                log("e3", "h2", 3L),
                LifeEvent("n1", EventKind.NOTE, 4L, body = "x")
            )
        )
        assertEquals(2, HabitDomain.logCount(data, "h1"))
        assertEquals(0, HabitDomain.logCount(data, "missing"))
        assertTrue(HabitDomain.saveAsNew(data, "  ", "•") === data)
    }
}
