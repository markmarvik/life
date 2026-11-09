package com.life.app.watch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

sealed class WatchIncoming {
    data object Sync : WatchIncoming()
    data class LogHabit(val habitId: String) : WatchIncoming()
    data class Note(val text: String) : WatchIncoming()
    data class WantDone(val wantId: String) : WatchIncoming()
}

data class WatchHabitItem(val id: String, val name: String, val loggedToday: Boolean)
data class WatchWantItem(val id: String, val title: String, val dueToday: Boolean)

sealed class WatchOutgoing {
    data class Habits(val items: List<WatchHabitItem>, val wants: List<WatchWantItem>) : WatchOutgoing()
    data class Ok(val ref: String, val id: String? = null) : WatchOutgoing()
    data class Error(val reason: String) : WatchOutgoing()
    data class Digest(val habits: List<WatchHabitItem>, val wants: List<WatchWantItem>) : WatchOutgoing()
}

object WatchMessages {
    private val compact = Json { prettyPrint = false }

    fun parseIncoming(json: String): WatchIncoming? {
        val obj = parseObject(json) ?: return null
        if (obj.int("v") != 1) return null
        return when (obj.str("type")) {
            "sync" -> WatchIncoming.Sync
            "log_habit" -> obj.trimmed("habitId")?.let { WatchIncoming.LogHabit(it) }
            "note" -> obj.trimmed("text")?.let { WatchIncoming.Note(it) }
            "want_done" -> obj.trimmed("wantId")?.let { WatchIncoming.WantDone(it) }
            else -> null
        }
    }

    fun encode(outgoing: WatchOutgoing): String = compact.encodeToString(JsonObject.serializer(), jsonObject(outgoing))

    fun parseOutgoing(json: String): WatchOutgoing? {
        val obj = parseObject(json) ?: return null
        if (obj.int("v") != 1) return null
        return when (obj.str("type")) {
            "ok" -> WatchOutgoing.Ok(obj.str("ref").orEmpty(), obj.str("id")?.ifBlank { null })
            "error" -> WatchOutgoing.Error(obj.str("reason").orEmpty())
            "habits" -> WatchOutgoing.Habits(readHabits(obj.array("items")), readWants(obj.array("wants")))
            "digest" -> WatchOutgoing.Digest(readHabits(obj.array("habits")), readWants(obj.array("wants")))
            else -> null
        }
    }

    private fun jsonObject(outgoing: WatchOutgoing): JsonObject = buildJsonObject {
        put("v", 1)
        when (outgoing) {
            is WatchOutgoing.Habits -> {
                put("type", "habits")
                put("items", habitArray(outgoing.items))
                put("wants", wantArray(outgoing.wants))
            }
            is WatchOutgoing.Ok -> {
                put("type", "ok")
                put("ref", outgoing.ref)
                if (outgoing.id != null) put("id", outgoing.id)
            }
            is WatchOutgoing.Error -> {
                put("type", "error")
                put("reason", outgoing.reason)
            }
            is WatchOutgoing.Digest -> {
                put("type", "digest")
                put("habits", habitArray(outgoing.habits))
                put("wants", wantArray(outgoing.wants))
            }
        }
    }

    private fun habitArray(items: List<WatchHabitItem>): JsonArray = buildJsonArray {
        for (item in items) {
            add(buildJsonObject {
                put("id", item.id)
                put("name", item.name)
                put("loggedToday", item.loggedToday)
            })
        }
    }

    private fun wantArray(items: List<WatchWantItem>): JsonArray = buildJsonArray {
        for (item in items) {
            add(buildJsonObject {
                put("id", item.id)
                put("title", item.title)
                put("dueToday", item.dueToday)
            })
        }
    }

    private fun readHabits(arr: JsonArray?): List<WatchHabitItem> {
        if (arr == null) return emptyList()
        return arr.map { el ->
            val o = el as JsonObject
            WatchHabitItem(
                o.str("id").orEmpty(),
                o.str("name").orEmpty(),
                (o["loggedToday"] as? JsonPrimitive)?.booleanOrNull ?: false
            )
        }
    }

    private fun readWants(arr: JsonArray?): List<WatchWantItem> {
        if (arr == null) return emptyList()
        return arr.map { el ->
            val o = el as JsonObject
            WatchWantItem(
                o.str("id").orEmpty(),
                o.str("title").orEmpty(),
                (o["dueToday"] as? JsonPrimitive)?.booleanOrNull ?: false
            )
        }
    }

    private fun parseObject(json: String): JsonObject? {
        val el = runCatching { Json.parseToJsonElement(json) }.getOrNull() ?: return null
        return el as? JsonObject
    }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.trimmed(key: String): String? = str(key)?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
}
