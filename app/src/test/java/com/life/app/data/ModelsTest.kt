package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ModelsTest {

    @Test
    fun groupEventsLabelsTodayAndYesterday() {
        val zone = ZoneOffset.UTC
        val now = LocalDateTime.of(2026, 8, 19, 15, 0).toInstant(zone).toEpochMilli()
        val today = LocalDateTime.of(2026, 8, 19, 9, 0).toInstant(zone).toEpochMilli()
        val yest = LocalDateTime.of(2026, 8, 18, 9, 0).toInstant(zone).toEpochMilli()
        val older = LocalDateTime.of(2026, 8, 10, 9, 0).toInstant(zone).toEpochMilli()
        val groups = groupEvents(
            listOf(
                LifeEvent("1", EventKind.NOTE, today, body = "today"),
                LifeEvent("2", EventKind.NOTE, yest, body = "yest"),
                LifeEvent("3", EventKind.NOTE, older, body = "old")
            ),
            nowMs = now,
            zone = zone
        )
        assertEquals(listOf("Today", "Yesterday"), groups.take(2).map { it.label })
        assertEquals("today", groups[0].events.single().body)
    }

    @Test
    fun withEventReplacesByIdAndPrepends() {
        val a = LifeEvent("a", EventKind.NOTE, 1L, body = "one")
        val b = LifeEvent("b", EventKind.NOTE, 2L, body = "two")
        val data = AppData().withEvent(a).withEvent(b)
        assertEquals(listOf("b", "a"), data.events.map { it.id })
        val updated = data.withEvent(a.copy(body = "edited"))
        assertEquals("edited", updated.events.first { it.id == "a" }.body)
        assertEquals(2, updated.events.size)
    }
}
