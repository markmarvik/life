package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WantDomainTest {

    private val today = LocalDate.of(2026, 8, 19)

    private fun want(
        id: String,
        due: String,
        createdAt: Long = 1L,
        status: WantStatus = WantStatus.OPEN
    ) = LifeEvent(
        id = id,
        kind = EventKind.NOTE,
        createdAt = createdAt,
        body = "do $id",
        isWant = true,
        dueDate = due,
        wantStatus = status
    )

    @Test
    fun wordCountIgnoresExtraSpace() {
        assertEquals(10, WantDomain.wordCount("  one two  three four five six seven eight nine ten  "))
        assertEquals(9, WantDomain.wordCount("one two three four five six seven eight nine"))
    }

    @Test
    fun floorScalesAndCaps() {
        assertEquals(10, WantDomain.wordFloor(0))
        assertEquals(15, WantDomain.wordFloor(1))
        assertEquals(40, WantDomain.wordFloor(100))
    }

    @Test
    fun completeResetsStreak() {
        val data = AppData(
            events = listOf(want("a", "2026-08-19")),
            wantStrikeStreak = 3
        )
        val next = WantDomain.withCompletedWant(data, "a", 99L)
        assertEquals(0, next.wantStrikeStreak)
        assertEquals(WantStatus.DONE, next.events.first().wantStatus)
        assertEquals(99L, next.events.first().resolvedAt)
    }

    @Test
    fun failRequiresFloorAndIncrementsStreak() {
        val data = AppData(events = listOf(want("a", "2026-08-19")), wantStrikeStreak = 0)
        val tooShort = WantDomain.withFailedWant(data, "a", "too short", 1L, today)
        assertEquals(WantStatus.OPEN, tooShort.events.first().wantStatus)
        assertEquals(0, tooShort.wantStrikeStreak)

        val ten = "one two three four five six seven eight nine ten"
        val canceled = WantDomain.withFailedWant(data, "a", ten, 2L, today)
        assertEquals(WantStatus.CANCELED, canceled.events.first().wantStatus)
        assertEquals(1, canceled.wantStrikeStreak)

        val overdue = AppData(events = listOf(want("b", "2026-08-10")), wantStrikeStreak = 1)
        val fifteen = "$ten eleven twelve thirteen fourteen fifteen"
        val missed = WantDomain.withFailedWant(overdue, "b", fifteen, 3L, today)
        assertEquals(WantStatus.MISSED, missed.events.first().wantStatus)
        assertEquals(2, missed.wantStrikeStreak)
    }

    @Test
    fun upcomingOrdersOverdueThenTodayThenLater() {
        val data = AppData(
            events = listOf(
                want("later", "2026-08-22", 3),
                want("today", "2026-08-19", 2),
                want("old", "2026-08-10", 1),
                LifeEvent("note", EventKind.NOTE, 4L, body = "just a thought", dueDate = "2026-08-19")
            )
        )
        val ids = WantDomain.upcoming(data, today).map { it.id }
        assertEquals(listOf("old", "today", "later"), ids)
    }

    @Test
    fun dateOnlyNoteIsNotUpcoming() {
        val data = AppData(
            events = listOf(
                LifeEvent("n", EventKind.NOTE, 1L, body = "idea", dueDate = "2026-08-20")
            )
        )
        assertTrue(WantDomain.upcoming(data, today).isEmpty())
    }

    @Test
    fun groupEventsOmitsOpenWantsAndUsesResolvedDay() {
        val open = want("open", "2026-08-20", createdAt = 1_000L)
        val done = want("done", "2026-08-18", createdAt = 1_000L, status = WantStatus.DONE)
            .copy(resolvedAt = 86_400_000L * 2) // ~ 1970-01-03
        val note = LifeEvent("n", EventKind.NOTE, 2_000L, body = "hi")
        val groups = groupEvents(listOf(open, done, note), nowMs = 2_000L)
        val ids = groups.flatMap { it.events }.map { it.id }
        assertFalse(ids.contains("open"))
        assertTrue(ids.contains("n"))
        assertTrue(ids.contains("done"))
    }

    @Test
    fun wantWithoutDateDefaultsToToday() {
        assertEquals("2026-08-19", WantDomain.resolveDueOnSave(true, null, today))
        assertEquals(null, WantDomain.resolveDueOnSave(false, null, today))
        assertEquals("2026-08-21", WantDomain.resolveDueOnSave(false, "2026-08-21", today))
    }
}
