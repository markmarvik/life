package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * Locks in picker bugs we already shipped once:
 * premade rows not editable, default sitting next to an edit, gray
 * subtitle vanishing, Evening hygiene as a second brush habit.
 */
class HabitCatalogRegressionTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun catalogIdsAndNamesAreUnique() {
        val ids = HabitCatalog.ALL.map { it.id }
        val names = HabitCatalog.ALL.map { it.name.lowercase() }
        assertEquals(ids.distinct(), ids)
        assertEquals(names.distinct(), names)
        assertTrue(HabitCatalog.ALL.all { it.why.isNotBlank() })
    }

    @Test
    fun eveningHygieneIsNotASeparateHabit() {
        assertFalse(HabitCatalog.ALL.any { it.id.contains("evening", ignoreCase = true) })
        assertFalse(HabitCatalog.ALL.any { it.name.contains("hygiene", ignoreCase = true) })
        assertFalse(HabitCatalog.ALL.any { it.why.contains("at night", ignoreCase = true) })
    }

    @Test
    fun everyPremadeRowIsEditableBeforeFirstLog() {
        val empty = AppData()
        val ranked = HabitRanker.suggest(empty, 1L, "", zone)
        for (cat in HabitCatalog.ALL) {
            val def = HabitDomain.defForEdit(empty, cat.id)
            assertEquals(cat.id, def?.id, cat.name)
            assertEquals(cat.name, def?.name)
            assertEquals(cat.why, def?.why)
            val row = ranked.first { it.habitId == cat.id }
            assertTrue(row.canEdit, cat.name)
            assertEquals(cat.why, row.why)
        }
        assertTrue(ranked.none { it.isCreate })
        assertEquals(HabitCatalog.ALL.size, ranked.count { it.canEdit })
    }

    @Test
    fun claimingPremadeDoesNotLeaveDefaultBesideEdit() {
        for (cat in HabitCatalog.ALL) {
            val next = HabitDomain.edit(
                AppData(),
                cat.id,
                "My ${cat.name}",
                cat.icon,
                cat.why,
                nowMs = 9L
            )
            assertEquals(1, next.habits.count { it.id == cat.id })
            val ranked = HabitRanker.suggest(next, 9L, "", zone)
            val ids = ranked.mapNotNull { it.habitId }
            assertEquals(ids.distinct(), ids, cat.id)
            assertEquals(1, ranked.count { it.habitId == cat.id }, cat.id)
            assertTrue(ranked.none { it.name == cat.name }, cat.name)
            assertEquals(cat.why, ranked.first { it.habitId == cat.id }.why)
        }
    }

    @Test
    fun loggedCatalogWithoutStoredWhyStillShowsGrayText() {
        val data = AppData(
            habits = listOf(
                HabitDef("cat_brush", "Brush + floss", icon = "🪥", why = "", createdAt = 1L)
            )
        )
        val ranked = HabitRanker.suggest(data, 1L, "", zone)
        val row = ranked.first { it.habitId == "cat_brush" }
        assertEquals("Dental foundation.", row.why)
        assertTrue(row.canEdit)
        assertNotNull(HabitDomain.defForEdit(data, "cat_brush")?.why?.takeIf { it.isNotBlank() })
        assertTrue(ranked.none { it.name == "Brush + floss" && it.habitId != "cat_brush" })
    }

    @Test
    fun createRowIsNotEditable() {
        val ranked = HabitRanker.suggest(AppData(), 1L, "Cold plunge", zone)
        assertTrue(ranked.first().isCreate)
        assertFalse(ranked.first().canEdit)
    }
}
