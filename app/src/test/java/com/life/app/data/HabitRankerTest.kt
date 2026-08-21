package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class HabitRankerTest {

    private val zone = ZoneOffset.UTC

    private fun ms(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun habit(id: String, name: String) = HabitDef(id, name, icon = "•", createdAt = 1L)

    private fun log(habitId: String, at: Long) = LifeEvent(
        id = UUID.randomUUID().toString(),
        kind = EventKind.HABIT,
        createdAt = at,
        habitId = habitId
    )

    @Test
    fun emptyQueryRanksMorningHabitHigherAtEight() {
        val morning = habit("h_sun", "Morning sunlight")
        val evening = habit("h_read", "Read before bed")
        val now = ms(2026, 8, 19, 8)
        val data = AppData(
            habits = listOf(morning, evening),
            events = listOf(
                log("h_sun", ms(2026, 8, 18, 8)),
                log("h_sun", ms(2026, 8, 17, 8)),
                log("h_sun", ms(2026, 8, 16, 7)),
                log("h_read", ms(2026, 8, 18, 22)),
                log("h_read", ms(2026, 8, 17, 22))
            )
        )
        val ranked = HabitRanker.suggest(data, now, "", zone)
        assertEquals("Morning sunlight", ranked.first().name)
    }

    @Test
    fun unknownQueryOffersCreateFirst() {
        val data = AppData(habits = listOf(habit("h1", "Meditate")))
        val ranked = HabitRanker.suggest(data, 1L, "Cold plunge", zone)
        assertTrue(ranked.first().isCreate)
        assertEquals("Cold plunge", ranked.first().name)
    }

    @Test
    fun fuzzyHitsExistingHabitAndDoesNotCreateWhenExact() {
        val data = AppData(habits = listOf(habit("h1", "Meditate")))
        val ranked = HabitRanker.suggest(data, 1L, "meditate", zone)
        assertTrue(ranked.none { it.isCreate })
        assertEquals("Meditate", ranked.first().name)
    }

    @Test
    fun catalogAppearsUntilCreated() {
        val ranked = HabitRanker.suggest(AppData(), 1L, "Brush", zone)
        val brush = ranked.first { it.name.startsWith("Brush") && !it.isCreate }
        assertEquals("Dental foundation.", brush.why)
    }

    @Test
    fun renamedCatalogKeepsWhyAndDoesNotResurfaceDefault() {
        val data = AppData(
            habits = listOf(
                HabitDef("cat_brush", "Brush teeth", icon = "🪥", why = "Dental foundation.", createdAt = 1L)
            )
        )
        val ranked = HabitRanker.suggest(data, 1L, "", zone)
        assertEquals(1, ranked.count { it.habitId == "cat_brush" })
        assertEquals("Brush teeth", ranked.first { it.habitId == "cat_brush" }.name)
        assertEquals("Dental foundation.", ranked.first { it.habitId == "cat_brush" }.why)
        assertTrue(ranked.none { it.name == "Brush + floss" })
    }

    @Test
    fun eveningHygieneIsNotInCatalog() {
        assertFalse(HabitCatalog.ALL.any { it.name.contains("hygiene", ignoreCase = true) })
        val ranked = HabitRanker.suggest(AppData(), 1L, "Evening", zone)
        assertTrue(ranked.none { it.name.contains("hygiene", ignoreCase = true) && !it.isCreate })
    }
}
