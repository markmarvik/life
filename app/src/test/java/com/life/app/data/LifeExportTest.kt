package com.life.app.data

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LifeExportTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 26)

    private fun at(day: LocalDate, hour: Int): Long =
        day.atTime(hour, 0).toInstant(zone).toEpochMilli()

    @Test
    fun jsonHasSchemaStreaksAndIsoDates() {
        val data = AppData(
            habits = listOf(
                HabitDef("h1", "Walk", icon = "🚶", why = "Out"),
                HabitDef("h2", "A|B", icon = "•")
            ),
            events = listOf(
                LifeEvent("e2", EventKind.HABIT, at(today, 8), habitId = "h1"),
                LifeEvent("e1", EventKind.HABIT, at(today.minusDays(1), 9), habitId = "h1"),
                LifeEvent(
                    "n1",
                    EventKind.NOTE,
                    at(today, 10),
                    body = "hello\nthere",
                    tags = listOf("Work")
                ),
                LifeEvent(
                    "f1",
                    EventKind.FINANCE,
                    at(today.minusDays(2), 12),
                    amountMinor = 450L,
                    direction = MoneyDirection.OUT,
                    payee = "Lidl",
                    category = "Food"
                )
            )
        )
        val now = at(today, 15)
        val export = LifeExportFormat.build(data, now, zone)
        assertEquals(1, export.schemaVersion)
        assertEquals("2026-10-26T15:00:00Z", export.exportedAt)
        assertEquals("Z", export.zone)
        val walk = export.habits.first { it.id == "h1" }
        assertEquals(2, walk.currentStreak)
        assertEquals(2, walk.bestStreak)
        assertTrue(walk.loggedToday)
        assertEquals(listOf("f1", "e1", "e2", "n1"), export.entries.map { it.id })
        assertEquals("2026-10-25T09:00:00Z", export.entries.first { it.id == "e1" }.at)
        assertEquals("2026-10-25", export.entries.first { it.id == "e1" }.date)
        assertEquals("OUT", export.entries.first { it.id == "f1" }.direction)
        assertEquals(450L, export.entries.first { it.id == "f1" }.amountMinor)

        val json = LifeExportFormat.toJson(export)
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"currentStreak\": 2"))
        assertTrue(json.contains("\"at\": \"2026-10-25T09:00:00Z\""))
        assertTrue(json.contains("\"date\": \"2026-10-25\""))
        val back = Json { ignoreUnknownKeys = true }.decodeFromString<LifeExport>(json)
        assertEquals(export, back)

        val md = LifeExportFormat.toMarkdown(export)
        assertTrue(md.contains("schemaVersion: 1"))
        assertTrue(md.contains("| Walk | 2 | 2 | yes |"))
        assertTrue(md.contains("| A\\|B | 0 | 0 | no |"))
        assertTrue(md.contains("| 2026-10-25 | 09:00 | HABIT | Walk |"))
        assertTrue(md.contains("| 2026-10-24 | 12:00 | FINANCE | -450 Lidl (Food) |"))
        assertTrue(md.contains("hello there"))
    }
}
