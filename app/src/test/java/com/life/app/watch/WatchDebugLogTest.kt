package com.life.app.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchDebugLogTest {
    @Test
    fun keepsTwoHundredNewestFirstAndRedacts() {
        val log = WatchDebugLog()
        repeat(210) { i -> log.record(i.toLong(), "info", "recv", mapOf("type" to "note", "bytes" to 12, "text" to "secret note")) }
        val snap = log.snapshot()
        assertEquals(200, snap.size)
        assertEquals(209L, snap.first().atMs)
        val rendered = log.render()
        assertFalse(rendered.contains("secret note"))
        assertTrue(rendered.contains("type=note"))
        assertTrue(rendered.contains("bytes=12"))
        assertFalse(rendered.contains("payload="))
    }
}
