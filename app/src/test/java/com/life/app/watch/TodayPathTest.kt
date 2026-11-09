package com.life.app.watch

import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.HabitDef
import com.life.app.data.LifeEvent
import com.life.app.data.WantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TodayPathTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val zone = ZoneOffset.UTC
    private val noon = today.atTime(12, 0).toInstant(zone).toEpochMilli()

    @Test
    fun ranksAndPutsUnloggedFirst() {
        val gym = HabitDef("h_gym", "Gym", createdAt = 1L)
        val water = HabitDef("h_water", "Water", createdAt = 2L)
        val data = AppData(
            habits = listOf(gym, water),
            events = listOf(
                LifeEvent("e1", EventKind.HABIT, noon - 3_600_000, habitId = "h_gym"),
                LifeEvent("w1", EventKind.NOTE, 1L, body = "Call dentist", isWant = true, dueDate = "2026-11-10", wantStatus = WantStatus.OPEN),
                LifeEvent("w0", EventKind.NOTE, 1L, body = "Overdue bill", isWant = true, dueDate = "2026-11-08", wantStatus = WantStatus.OPEN),
                LifeEvent("later", EventKind.NOTE, 1L, body = "Next week", isWant = true, dueDate = "2026-11-20", wantStatus = WantStatus.OPEN)
            )
        )
        val path = TodayPath.build(data, noon, today, zone)
        assertEquals("h_water", path.habits.first { it.id == "h_water" || it.id == "h_gym" }.let {
            path.habits.first { h -> !h.loggedToday }.id
        })
        assertTrue(path.habits.first { it.id == "h_gym" }.loggedToday)
        assertFalse(path.habits.first { it.id == "h_water" }.loggedToday)
        val wantIds = path.wants.map { it.id }
        assertTrue(wantIds.containsAll(listOf("w1", "w0")))
        assertFalse(wantIds.contains("later"))
        assertTrue(path.wants.first { it.id == "w0" }.dueToday)
    }

    @Test
    fun digestOmitsLoggedAndUnusedCatalog() {
        val gym = HabitDef("h_gym", "Gym", createdAt = 1L)
        val data = AppData(
            habits = listOf(gym),
            events = listOf(LifeEvent("e1", EventKind.HABIT, noon - 1_000, habitId = "h_gym"))
        )
        val digest = TodayPath.digest(data, noon, today, zone)
        assertTrue(digest.habits.none { it.id == "h_gym" })
        assertTrue(digest.habits.none { it.id.startsWith("cat_") })
    }
}
