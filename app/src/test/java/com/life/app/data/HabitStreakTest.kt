package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HabitStreakTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 26)

    private fun at(day: LocalDate, hour: Int = 9): Long =
        day.atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun log(id: String, habitId: String, day: LocalDate, hour: Int = 9) = LifeEvent(
        id = id,
        kind = EventKind.HABIT,
        createdAt = at(day, hour),
        habitId = habitId
    )

    private fun streak(data: AppData, habitId: String = "h1"): HabitStreak =
        HabitStreaks.forAll(data, today, zone).getValue(habitId)

    @Test
    fun sameDayLogsCountAsOneDay() {
        val data = AppData(
            habits = listOf(HabitDef("h1", "Walk")),
            events = listOf(log("a", "h1", today, 8), log("b", "h1", today, 18))
        )
        val streak = streak(data)
        assertEquals(1, streak.current)
        assertEquals(1, streak.best)
        assertTrue(streak.loggedToday)
        assertEquals(7, streak.recent.size)
        assertTrue(streak.recent.last())
        assertEquals(1, streak.recentDone)
    }

    @Test
    fun todayStillOpenKeepsYesterdaysRun() {
        val data = AppData(
            habits = listOf(HabitDef("h1", "Walk")),
            events = listOf(
                log("a", "h1", LocalDate.of(2026, 10, 20)),
                log("b", "h1", LocalDate.of(2026, 10, 21)),
                log("c", "h1", LocalDate.of(2026, 10, 22)),
                log("d", "h1", LocalDate.of(2026, 10, 25))
            )
        )
        val streak = streak(data)
        assertFalse(streak.loggedToday)
        assertEquals(1, streak.current)
        assertEquals(3, streak.best)
        assertEquals(listOf(true, true, true, false, false, true, false), streak.recent)
    }

    @Test
    fun gapBeforeTodayBreaksTheCurrentRun() {
        val data = AppData(
            habits = listOf(HabitDef("h1", "Walk")),
            events = listOf(
                log("a", "h1", LocalDate.of(2026, 10, 23)),
                log("b", "h1", LocalDate.of(2026, 10, 24)),
                log("c", "h1", today)
            )
        )
        val streak = streak(data)
        assertEquals(1, streak.current)
        assertEquals(2, streak.best)
    }

    @Test
    fun toggleMarksTodayThenClearsOnlyToday() {
        var data = AppData(
            habits = listOf(HabitDef("h1", "Walk")),
            events = listOf(log("old", "h1", today.minusDays(1)))
        )
        data = HabitStreaks.toggleToday(data, "h1", at(today, 8), zone)
        assertEquals(2, data.events.size)
        assertTrue(streak(data).loggedToday)
        assertEquals(2, streak(data).current)
        data = HabitStreaks.toggleToday(data, "h1", at(today, 11), zone)
        assertEquals(listOf("old"), data.events.map { it.id })
        assertEquals(1, streak(data).current)
        assertFalse(streak(data).loggedToday)
    }

    @Test
    fun toggleClaimsCatalogHabit() {
        val data = HabitStreaks.toggleToday(AppData(), "cat_meditate", at(today), zone)
        assertEquals("Meditate", data.habits.single().name)
        assertEquals("cat_meditate", data.events.single().habitId)
        assertEquals(1, streak(data, "cat_meditate").current)
    }

    @Test
    fun todayRowLeadsWithTheLatestHabit() {
        val data = AppData(
            habits = listOf(HabitDef("h1", "Walk"), HabitDef("h2", "Read")),
            events = listOf(
                log("a", "h1", today.minusDays(3)),
                log("b", "h2", today.minusDays(1))
            )
        )
        assertEquals(listOf("h2", "h1"), HabitStreaks.todayRow(data, today, zone).map { it.id })
    }
}
