package com.life.app.watch

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WatchPayloadTest {
    @Test
    fun matchesProvenPhoneEncoding() {
        val body = "hello-from-watch".toByteArray(Charsets.UTF_8)
        assertEquals("aGVsbG8tZnJvbS13YXRjaA==", WatchPayload.encode(body))
        assertArrayEquals(body, WatchPayload.decode("aGVsbG8tZnJvbS13YXRjaA=="))
        val json = """{"v":1,"type":"sync"}""".toByteArray(Charsets.UTF_8)
        assertArrayEquals(json, WatchPayload.decode(WatchPayload.encode(json)))
    }

    @Test
    fun rejectsNonCanonicalPayloads() {
        assertNull(WatchPayload.decode("abc"))
        assertNull(WatchPayload.decode("aGVsbG8tZnJvbS13YXRjaA==\n"))
        assertNull(WatchPayload.decode(null))
        assertEquals("3266101", WatchPayload.canonicalAppId("3266101"))
        assertNull(WatchPayload.canonicalAppId("3266101a"))
    }
}
