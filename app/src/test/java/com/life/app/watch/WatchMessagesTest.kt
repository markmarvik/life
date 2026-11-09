package com.life.app.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchMessagesTest {
    @Test
    fun parsesSyncLogNoteAndWant() {
        assertEquals(WatchIncoming.Sync, WatchMessages.parseIncoming("""{"v":1,"type":"sync"}"""))
        assertEquals(
            WatchIncoming.LogHabit("cat_brush"),
            WatchMessages.parseIncoming("""{"v":1,"type":"log_habit","habitId":"cat_brush"}""")
        )
        assertEquals(
            WatchIncoming.Note("called dentist"),
            WatchMessages.parseIncoming("""{"v":1,"type":"note","text":"called dentist"}""")
        )
        assertEquals(
            WatchIncoming.WantDone("want-1"),
            WatchMessages.parseIncoming("""{"v":1,"type":"want_done","wantId":"want-1"}""")
        )
    }

    @Test
    fun rejectsBadPayloads() {
        assertNull(WatchMessages.parseIncoming("not-json"))
        assertNull(WatchMessages.parseIncoming("""{"v":1,"type":"nope"}"""))
        assertNull(WatchMessages.parseIncoming("""{"v":1,"type":"log_habit"}"""))
        assertNull(WatchMessages.parseIncoming("""{"v":2,"type":"sync"}"""))
        assertNull(WatchMessages.parseIncoming("""{"v":1,"type":"note","text":"   "}"""))
    }

    @Test
    fun encodesOutgoing() {
        val habits = WatchOutgoing.Habits(
            items = listOf(WatchHabitItem("cat_brush", "Brush + floss", loggedToday = true)),
            wants = listOf(WatchWantItem("w1", "Call dentist", dueToday = true))
        )
        val json = WatchMessages.encode(habits)
        assertTrue(json.contains("\"type\":\"habits\""))
        assertEquals(
            WatchOutgoing.Ok("log_habit", "cat_brush"),
            WatchMessages.parseOutgoing(WatchMessages.encode(WatchOutgoing.Ok("log_habit", "cat_brush")))
        )
        assertEquals(
            WatchOutgoing.Error("unknown_id"),
            WatchMessages.parseOutgoing(WatchMessages.encode(WatchOutgoing.Error("unknown_id")))
        )
    }
}
