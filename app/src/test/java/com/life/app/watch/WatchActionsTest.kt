package com.life.app.watch

import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.HabitDef
import com.life.app.data.LifeEvent
import com.life.app.data.WantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WatchActionsTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val now = 1_731_216_000_000L

    @Test
    fun logsKnownAndCatalogHabits() {
        val data = AppData(habits = listOf(HabitDef("h_gym", "Gym", createdAt = 1L)))
        val known = WatchActions.apply(data, WatchIncoming.LogHabit("h_gym"), now, today)
        assertTrue(known.outgoing is WatchOutgoing.Ok)
        assertEquals("h_gym", (known.outgoing as WatchOutgoing.Ok).id)
        assertEquals(1, known.data.events.count { it.kind == EventKind.HABIT && it.habitId == "h_gym" })

        val catalog = WatchActions.apply(AppData(), WatchIncoming.LogHabit("cat_brush"), now + 1, today)
        assertEquals("cat_brush", (catalog.outgoing as WatchOutgoing.Ok).id)
        assertTrue(catalog.data.habits.any { it.id == "cat_brush" })
        assertEquals(1, catalog.data.events.count { it.habitId == "cat_brush" })
    }

    @Test
    fun secondTapLogsAgain() {
        val first = WatchActions.apply(AppData(), WatchIncoming.LogHabit("cat_brush"), now, today)
        val second = WatchActions.apply(first.data, WatchIncoming.LogHabit("cat_brush"), now + 5, today)
        assertEquals(2, second.data.events.count { it.habitId == "cat_brush" })
    }

    @Test
    fun unknownHabitWritesNothing() {
        val result = WatchActions.apply(AppData(), WatchIncoming.LogHabit("nope"), now, today)
        assertEquals(WatchOutgoing.Error("unknown_id"), result.outgoing)
        assertTrue(result.data.events.isEmpty())
    }

    @Test
    fun noteAndWantDone() {
        val noted = WatchActions.apply(AppData(), WatchIncoming.Note("  ship it  "), now, today)
        assertEquals(WatchOutgoing.Ok("note", null), noted.outgoing)
        val event = noted.data.events.single()
        assertEquals(EventKind.NOTE, event.kind)
        assertEquals("ship it", event.body)
        assertTrue(event.tags.isEmpty())
        assertEquals(false, event.isWant)

        val open = LifeEvent(
            id = "w1",
            kind = EventKind.NOTE,
            createdAt = 1L,
            body = "Call dentist",
            isWant = true,
            dueDate = "2026-11-10",
            wantStatus = WantStatus.OPEN
        )
        val done = WatchActions.apply(AppData(events = listOf(open)), WatchIncoming.WantDone("w1"), now, today)
        assertEquals(WatchOutgoing.Ok("want_done", "w1"), done.outgoing)
        assertEquals(WantStatus.DONE, done.data.events.single().wantStatus)

        val again = WatchActions.apply(done.data, WatchIncoming.WantDone("w1"), now + 1, today)
        assertEquals(WatchOutgoing.Error("unknown_id"), again.outgoing)
        assertEquals(WantStatus.DONE, again.data.events.single().wantStatus)
    }

    @Test
    fun syncDoesNotWrite() {
        val data = AppData(habits = listOf(HabitDef("h_gym", "Gym")))
        val result = WatchActions.apply(data, WatchIncoming.Sync, now, today)
        assertNull(result.outgoing)
        assertEquals(data, result.data)
    }
}
