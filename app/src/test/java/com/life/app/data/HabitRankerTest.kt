package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
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
        assertTrue(ranked.any { it.name.startsWith("Brush") && !it.isCreate })
    }
}
