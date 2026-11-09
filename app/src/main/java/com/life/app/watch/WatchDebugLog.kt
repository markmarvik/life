package com.life.app.watch

data class WatchDebugEvent(
    val atMs: Long,
    val level: String,
    val name: String,
    val detail: Map<String, String>
)

class WatchDebugLog {
    private val events = ArrayDeque<WatchDebugEvent>()

    fun record(atMs: Long, level: String, name: String, detail: Map<String, Any?>): WatchDebugEvent {
        val clean = LinkedHashMap<String, String>()
        for ((key, value) in detail) {
            if (key !in ALLOWED || value == null) continue
            clean[key] = value.toString()
        }
        val event = WatchDebugEvent(atMs, level, name, clean)
        synchronized(events) {
            events.addFirst(event)
            while (events.size > MAX) events.removeLast()
        }
        return event
    }

    fun snapshot(): List<WatchDebugEvent> = synchronized(events) { events.toList() }

    fun formatLine(event: WatchDebugEvent): String {
        val details = event.detail.entries.joinToString(" ") { "${it.key}=${it.value}" }
        return listOf(event.atMs.toString(), event.level, event.name, details)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    fun render(): String = snapshot().joinToString("\n") { formatLine(it) }

    companion object {
        const val MAX = 200
        val ALLOWED = setOf(
            "type",
            "appId",
            "bytes",
            "habitId",
            "wantId",
            "package",
            "registered",
            "habitCount",
            "wantCount",
            "notification",
            "reason",
            "error"
        )
    }
}

object WatchLogs {
    val log = WatchDebugLog()
}
