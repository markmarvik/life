# Life BIP Max Watch App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a BIP Max watch app log habits and short notes into Life, complete today’s Wants, and show morning/evening path digests through Gadgetbridge watch-app messages.

**Architecture:** Pure Kotlin in `com.life.app.watch` parses JSON, writes through the same `AppData` / `HabitDomain` / `WantDomain` functions the phone UI uses, and builds “today’s path.” An exported `BroadcastReceiver` accepts `WATCH_APP_MESSAGE` even when the timeline is not open. A small Zepp OS app in `watch/` talks to Gadgetbridge over the existing JS messaging path (`appId` `3266101`). Watch-only preferences live in a separate DataStore key, not in `AppData`, so iPhone JSON backups stay compatible.

**Tech Stack:** Kotlin 2.2, JUnit 5 (existing `app/src/test` style), Jetpack Compose Settings, DataStore, AlarmManager, Gadgetbridge Intent API, Zepp OS / Zeus CLI for `watch/`.

**Spec:** `/workspace/docs/superpowers/specs/2026-11-10-life-watch-app-design.md` (copy into the Life repo as `docs/superpowers/specs/2026-11-10-life-watch-app-design.md` in Task 10).

## Global Constraints

- Application id is `com.life.app`. Watch app id is the decimal string `3266101`. Bip Max `deviceSource` is `11206915`.
- Default Gadgetbridge package is `nodomain.freeyourgadget.gadgetbridge.zepptest` until PR #7290 is in Nightly.
- Send command: `nodomain.freeyourgadget.gadgetbridge.command.SEND_WATCH_APP_MESSAGE` extras `device` (optional), `appId`, `payload`, and `replyTo` (`PendingIntent`). Listen-only: same command with `replyTo` and no `payload`. Incoming: `nodomain.freeyourgadget.gadgetbridge.action.WATCH_APP_MESSAGE` extras `device`, `appId`, base64 `payload`.
- Outgoing `payload` encoding must match https://codeberg.org/markmarvik/zepp-os-message-test `phone/` (the sidecar that already round-tripped on a Bip Max). Incoming `payload` is base64 of the watch body’s UTF-8 bytes; decode that to JSON.
- A tap always appends a new habit log. It does not undo today’s log.
- No money, voice, habit schedules, skip, due lists, streak scores, or “you skipped” copy.
- Notes from the watch are body-only (no tags, no Want date).
- Open Wants on the watch and in digests are those with `dueDate` on or before today (today + overdue). That keeps missed commitments visible.
- Do not queue logs on the watch. No “Logged” until Life replies `ok`.
- Debug log (logcat tag `LifeWatch` + last 200 in-app events) must never contain raw payloads, note text, notification body that repeats a note, MAC addresses, or tokens. Allowed: `type`, `appId`, payload size, habit id, Want id, GB package, listener registered, digest counts, notification scheduled/fired/permission-denied, error class/`reason`.
- No code comments. Do not modify `/home/tux/Code/Gadgetbridge` or replace Nightly.
- `expiredTime` for the watch package must be far in the future (not 48 hours).
- Do not claim it works on every Zepp OS watch. Device proof is Bip Max + Gadgetbridge (Zepp test) + Life.

## File map

Create:

- `app/src/main/java/com/life/app/watch/WatchMessages.kt` — JSON contract
- `app/src/main/java/com/life/app/watch/WatchActions.kt` — apply incoming messages to `AppData`
- `app/src/main/java/com/life/app/watch/TodayPath.kt` — ranked today list + digest contents
- `app/src/main/java/com/life/app/watch/WatchDebugLog.kt` — redacted ring buffer
- `app/src/main/java/com/life/app/watch/WatchSettings.kt` — preferences (not in `AppData`)
- `app/src/main/java/com/life/app/watch/GadgetbridgeClient.kt` — listen + send intents
- `app/src/main/java/com/life/app/watch/WatchMessageReceiver.kt` — incoming broadcasts
- `app/src/main/java/com/life/app/watch/DigestScheduler.kt` — morning/evening alarms
- `app/src/main/java/com/life/app/watch/DigestReceiver.kt`
- `app/src/main/java/com/life/app/watch/DigestNotifications.kt`
- `app/src/test/java/com/life/app/watch/WatchMessagesTest.kt`
- `app/src/test/java/com/life/app/watch/WatchActionsTest.kt`
- `app/src/test/java/com/life/app/watch/TodayPathTest.kt`
- `app/src/test/java/com/life/app/watch/WatchDebugLogTest.kt`
- `watch/` — Zepp OS app (Zeus), `appId` 3266101
- `docs/superpowers/specs/2026-11-10-life-watch-app-design.md`
- `docs/superpowers/plans/2026-11-10-life-watch-app.md`

Modify:

- `app/src/main/java/com/life/app/data/HabitDomain.kt` — `ensureHabit`
- `app/src/main/AndroidManifest.xml` — receiver, queries
- `app/src/main/java/com/life/app/MainActivity.kt` — register listener + digest alarms on start
- `app/src/main/java/com/life/app/reminders/BootReceiver.kt` — same after boot
- `app/src/main/java/com/life/app/ui/SettingsScreen.kt` — Watch section
- `app/src/main/java/com/life/app/ui/LifeViewModel.kt` — settings helpers that do not belong in `AppData`
- `AGENTS.md` — wearable waiver for this companion only
- `README.md` — one short paragraph that a BIP Max app can log into Life via Gadgetbridge

---

### Task 1: Message contract

**Files:**
- Create: `app/src/main/java/com/life/app/watch/WatchMessages.kt`
- Test: `app/src/test/java/com/life/app/watch/WatchMessagesTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `WatchIncoming`, `WatchOutgoing`, `WatchMessages.parseIncoming(json: String): WatchIncoming?`, `WatchMessages.encode(outgoing: WatchOutgoing): String`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./build.sh test` or `./gradlew test --tests com.life.app.watch.WatchMessagesTest`

Expected: FAIL compiling `WatchMessages` / `WatchIncoming` not found.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.life.app.watch

import org.json.JSONArray
import org.json.JSONObject

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
    fun parseIncoming(json: String): WatchIncoming? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (obj.optInt("v", -1) != 1) return null
        return when (obj.optString("type")) {
            "sync" -> WatchIncoming.Sync
            "log_habit" -> obj.optString("habitId").trim().takeIf { it.isNotEmpty() }?.let { WatchIncoming.LogHabit(it) }
            "note" -> obj.optString("text").trim().takeIf { it.isNotEmpty() }?.let { WatchIncoming.Note(it) }
            "want_done" -> obj.optString("wantId").trim().takeIf { it.isNotEmpty() }?.let { WatchIncoming.WantDone(it) }
            else -> null
        }
    }

    fun encode(outgoing: WatchOutgoing): String {
        val obj = JSONObject().put("v", 1)
        when (outgoing) {
            is WatchOutgoing.Habits -> {
                obj.put("type", "habits")
                obj.put("items", habitArray(outgoing.items))
                obj.put("wants", wantArray(outgoing.wants))
            }
            is WatchOutgoing.Ok -> {
                obj.put("type", "ok")
                obj.put("ref", outgoing.ref)
                if (outgoing.id != null) obj.put("id", outgoing.id)
            }
            is WatchOutgoing.Error -> {
                obj.put("type", "error")
                obj.put("reason", outgoing.reason)
            }
            is WatchOutgoing.Digest -> {
                obj.put("type", "digest")
                obj.put("habits", habitArray(outgoing.habits))
                obj.put("wants", wantArray(outgoing.wants))
            }
        }
        return obj.toString()
    }

    fun parseOutgoing(json: String): WatchOutgoing? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (obj.optInt("v", -1) != 1) return null
        return when (obj.optString("type")) {
            "ok" -> WatchOutgoing.Ok(obj.optString("ref"), obj.optString("id").ifBlank { null })
            "error" -> WatchOutgoing.Error(obj.optString("reason"))
            "habits" -> WatchOutgoing.Habits(readHabits(obj.optJSONArray("items")), readWants(obj.optJSONArray("wants")))
            "digest" -> WatchOutgoing.Digest(readHabits(obj.optJSONArray("habits")), readWants(obj.optJSONArray("wants")))
            else -> null
        }
    }

    private fun habitArray(items: List<WatchHabitItem>): JSONArray {
        val arr = JSONArray()
        for (item in items) {
            arr.put(JSONObject().put("id", item.id).put("name", item.name).put("loggedToday", item.loggedToday))
        }
        return arr
    }

    private fun wantArray(items: List<WatchWantItem>): JSONArray {
        val arr = JSONArray()
        for (item in items) {
            arr.put(JSONObject().put("id", item.id).put("title", item.title).put("dueToday", item.dueToday))
        }
        return arr
    }

    private fun readHabits(arr: JSONArray?): List<WatchHabitItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WatchHabitItem(o.getString("id"), o.getString("name"), o.optBoolean("loggedToday"))
        }
    }

    private fun readWants(arr: JSONArray?): List<WatchWantItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WatchWantItem(o.getString("id"), o.getString("title"), o.optBoolean("dueToday"))
        }
    }
}
```

If unit tests on the JVM cannot see `org.json`, use `kotlinx.serialization` instead (the app already depends on it in `LifeRepository`). Same types, same test assertions.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew test --tests com.life.app.watch.WatchMessagesTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/life/app/watch/WatchMessages.kt app/src/test/java/com/life/app/watch/WatchMessagesTest.kt
git commit -m "Add Life watch message contract"
```

---

### Task 2: Apply incoming messages to AppData

**Files:**
- Modify: `app/src/main/java/com/life/app/data/HabitDomain.kt` (add `ensureHabit`)
- Create: `app/src/main/java/com/life/app/watch/WatchActions.kt`
- Test: `app/src/test/java/com/life/app/watch/WatchActionsTest.kt`

**Interfaces:**
- Consumes: `WatchIncoming`, `HabitCatalog`, `WantDomain.withCompletedWant`, `AppData.withEvent` / `withHabit`
- Produces: `WatchActions.Result(data: AppData, outgoing: WatchOutgoing)` and `WatchActions.apply(data, incoming, nowMs, today): Result`

`HabitDomain.ensureHabit` must match `LifeViewModel.resolveHabit` for a non-create id: existing def, else catalog claim (reuse same-name habit, otherwise `catalog.toDef` + `withHabit`), else null.

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.life.app.watch.WatchActionsTest`

Expected: FAIL, `WatchActions` not found.

- [ ] **Step 3: Write minimal implementation**

Add to `HabitDomain`:

```kotlin
fun ensureHabit(data: AppData, habitId: String, nowMs: Long): Pair<AppData, HabitDef>? {
    data.habitById(habitId)?.let { return data to it }
    val catalog = HabitCatalog.byId(habitId) ?: return null
    data.findHabitByName(catalog.name)?.let { return data to it }
    val created = catalog.toDef(createdAt = nowMs)
    return data.withHabit(created) to created
}
```

```kotlin
package com.life.app.watch

import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.HabitDomain
import com.life.app.data.LifeEvent
import com.life.app.data.WantDomain
import com.life.app.data.WantStatus
import com.life.app.data.withEvent
import java.time.LocalDate
import java.util.UUID

object WatchActions {
    data class Result(val data: AppData, val outgoing: WatchOutgoing?)

    fun apply(data: AppData, incoming: WatchIncoming, nowMs: Long, today: LocalDate): Result {
        return when (incoming) {
            WatchIncoming.Sync -> Result(data, null)
            is WatchIncoming.LogHabit -> {
                val ensured = HabitDomain.ensureHabit(data, incoming.habitId, nowMs)
                    ?: return Result(data, WatchOutgoing.Error("unknown_id"))
                val (next, habit) = ensured
                Result(
                    next.withEvent(
                        LifeEvent(
                            id = UUID.randomUUID().toString(),
                            kind = EventKind.HABIT,
                            createdAt = nowMs,
                            habitId = habit.id
                        )
                    ),
                    WatchOutgoing.Ok("log_habit", habit.id)
                )
            }
            is WatchIncoming.Note -> Result(
                data.withEvent(
                    LifeEvent(
                        id = UUID.randomUUID().toString(),
                        kind = EventKind.NOTE,
                        createdAt = nowMs,
                        body = incoming.text.trim(),
                        wantStatus = WantStatus.NONE
                    )
                ),
                WatchOutgoing.Ok("note", null)
            )
            is WatchIncoming.WantDone -> {
                val event = data.events.firstOrNull { it.id == incoming.wantId }
                if (event == null || !event.isOpenWant()) {
                    return Result(data, WatchOutgoing.Error("unknown_id"))
                }
                Result(
                    WantDomain.withCompletedWant(data, incoming.wantId, nowMs),
                    WatchOutgoing.Ok("want_done", incoming.wantId)
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew test --tests com.life.app.watch.WatchActionsTest`

Expected: PASS. Also re-run `HabitDomainTest` and `HabitCatalogRegressionTest`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/life/app/data/HabitDomain.kt app/src/main/java/com/life/app/watch/WatchActions.kt app/src/test/java/com/life/app/watch/WatchActionsTest.kt
git commit -m "Apply watch habit, note, and Want messages to Life data"
```

---

### Task 3: Today’s path and digest contents

**Files:**
- Create: `app/src/main/java/com/life/app/watch/TodayPath.kt`
- Test: `app/src/test/java/com/life/app/watch/TodayPathTest.kt`

**Interfaces:**
- Consumes: `HabitRanker.suggest(data, nowMs, "")`, `WantDomain.upcoming`, `WatchHabitItem`, `WatchWantItem`
- Produces: `TodayPath(habits, wants)`, `TodayPath.build(data, nowMs, today, zone)`, `TodayPath.digest(data, nowMs, today, zone)` (digest drops unused catalog-only rows and keeps only habits not logged today)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.life.app.watch

import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.HabitDef
import com.life.app.data.LifeEvent
import com.life.app.data.WantStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TodayPathTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val zone = ZoneOffset.UTC
    private val noon = today.atTime(12, 0).toInstant(zone).toEpochMilli()

    @Test
    fun ranksAndPutsUnloggedFirst() {
        val gym = HabitDef("h_gym", "Gym", createdAt = 1L)
        val water = HabitDef("h_water", "Water", createdAt = 2L)
        val data = AppData(
            habits = listOf(gym, water),
            events = listOf(
                LifeEvent("e1", EventKind.HABIT, noon - 3_600_000, habitId = "h_gym"),
                LifeEvent("w1", EventKind.NOTE, 1L, body = "Call dentist", isWant = true, dueDate = "2026-11-10", wantStatus = WantStatus.OPEN),
                LifeEvent("w0", EventKind.NOTE, 1L, body = "Overdue bill", isWant = true, dueDate = "2026-11-08", wantStatus = WantStatus.OPEN),
                LifeEvent("later", EventKind.NOTE, 1L, body = "Next week", isWant = true, dueDate = "2026-11-20", wantStatus = WantStatus.OPEN)
            )
        )
        val path = TodayPath.build(data, noon, today, zone)
        assertEquals("h_water", path.habits.first { it.id == "h_water" || it.id == "h_gym" }.let {
            path.habits.first { h -> !h.loggedToday }.id
        })
        assertTrue(path.habits.first { it.id == "h_gym" }.loggedToday)
        assertFalse(path.habits.first { it.id == "h_water" }.loggedToday)
        val wantIds = path.wants.map { it.id }
        assertTrue(wantIds.containsAll(listOf("w1", "w0")))
        assertFalse(wantIds.contains("later"))
        assertTrue(path.wants.first { it.id == "w0" }.dueToday)
    }

    @Test
    fun digestOmitsLoggedAndUnusedCatalog() {
        val gym = HabitDef("h_gym", "Gym", createdAt = 1L)
        val data = AppData(
            habits = listOf(gym),
            events = listOf(LifeEvent("e1", EventKind.HABIT, noon - 1_000, habitId = "h_gym"))
        )
        val digest = TodayPath.digest(data, noon, today, zone)
        assertTrue(digest.habits.none { it.id == "h_gym" })
        assertTrue(digest.habits.none { it.id.startsWith("cat_") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.life.app.watch.TodayPathTest`

Expected: FAIL, `TodayPath` not found.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.life.app.watch

import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.HabitRanker
import com.life.app.data.WantDomain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TodayPath(
    val habits: List<WatchHabitItem>,
    val wants: List<WatchWantItem>
) {
    companion object {
        fun build(data: AppData, nowMs: Long, today: LocalDate, zone: ZoneId): TodayPath {
            val ranked = HabitRanker.suggest(data, nowMs, "").filter { !it.isCreate && !it.habitId.isNullOrBlank() }
            val items = ranked.map { suggestion ->
                val id = suggestion.habitId!!
                WatchHabitItem(id, suggestion.name, loggedToday = loggedToday(data, id, today, zone))
            }
            val sorted = items.sortedBy { it.loggedToday }
            return TodayPath(sorted, openWantsDue(data, today))
        }

        fun digest(data: AppData, nowMs: Long, today: LocalDate, zone: ZoneId): TodayPath {
            val createdIds = data.habits.filter { !it.archived }.map { it.id }.toSet()
            val full = build(data, nowMs, today, zone)
            return TodayPath(
                habits = full.habits.filter { it.id in createdIds && !it.loggedToday },
                wants = full.wants
            )
        }

        private fun loggedToday(data: AppData, habitId: String, today: LocalDate, zone: ZoneId): Boolean =
            data.events.any { e ->
                e.kind == EventKind.HABIT && e.habitId == habitId &&
                    Instant.ofEpochMilli(e.createdAt).atZone(zone).toLocalDate() == today
            }

        private fun openWantsDue(data: AppData, today: LocalDate): List<WatchWantItem> =
            WantDomain.upcoming(data, today).mapNotNull { event ->
                val due = WantDomain.parseDue(event.dueDate) ?: return@mapNotNull null
                if (due.isAfter(today)) return@mapNotNull null
                WatchWantItem(event.id, event.body, dueToday = true)
            }
    }
}
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew test --tests com.life.app.watch.TodayPathTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/life/app/watch/TodayPath.kt app/src/test/java/com/life/app/watch/TodayPathTest.kt
git commit -m "Build today’s path and digest lists for the watch"
```

---

### Task 4: Redacted debug log

**Files:**
- Create: `app/src/main/java/com/life/app/watch/WatchDebugLog.kt`
- Test: `app/src/test/java/com/life/app/watch/WatchDebugLogTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `WatchDebugEvent(atMs, level, name, detail)`, `WatchDebugLog` with `record`, `snapshot`, `formatLine`, `render` (newest first, max 200), never includes forbidden substrings when recording note/payload fields

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.life.app.watch.WatchDebugLogTest`

Expected: FAIL, `WatchDebugLog` not found.

- [ ] **Step 3: Write minimal implementation**

Keep an in-memory `ArrayDeque` of 200. `record` copies only allowlisted keys: `type`, `appId`, `bytes`, `habitId`, `wantId`, `package`, `registered`, `habitCount`, `wantCount`, `notification`, `reason`, `error`. Drop `text`, `payload`, `body`, `mac`. `render()` joins `formatLine` with newlines. Also print each accepted event with `android.util.Log` tag `LifeWatch` inside an `android` wrapper function `WatchDebugLog.android(name, detail)` used by receivers — the unit-tested class itself must not import `android.util.Log` so JVM tests stay green (put the logcat call in `WatchDebugAndroid.kt` if needed).

Allowed detail keys are the only ones copied. That is the redaction.

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `./gradlew test --tests com.life.app.watch.WatchDebugLogTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/life/app/watch/WatchDebugLog.kt app/src/test/java/com/life/app/watch/WatchDebugLogTest.kt
git commit -m "Add redacted LifeWatch debug log"
```

---

### Task 5: Watch preferences

**Files:**
- Create: `app/src/main/java/com/life/app/watch/WatchSettings.kt`
- Modify: `app/src/main/java/com/life/app/ui/LifeViewModel.kt` only if a thin helper is needed later (Task 8). Not in `AppData`.

**Interfaces:**
- Produces:

```kotlin
data class WatchPreferences(
    val enabled: Boolean = true,
    val gadgetbridgePackage: String = WatchSettings.DEFAULT_GB_PACKAGE,
    val morningDigestEnabled: Boolean = true,
    val eveningDigestEnabled: Boolean = true,
    val morningHour: Int = 8,
    val eveningHour: Int = 20
) {
    companion object {
        // unused — defaults live on WatchSettings
    }
}

object WatchSettings {
    const val DEFAULT_GB_PACKAGE = "nodomain.freeyourgadget.gadgetbridge.zepptest"
    const val APP_ID = "3266101"
}
```

Store JSON of `WatchPreferences` under a DataStore key `watch_prefs` on the existing `lifeDataStore` (a second key beside `app_data`). Load/save with `kotlinx.serialization`. Defaults apply when the key is missing.

Persist the debug log snapshot under a third key `watch_debug_log` (plain text, last render), written by the receiver after each event so Settings can show it after process death.

No unit test required beyond defaults if DataStore needs Android. Do not add these fields to `AppData.SCHEMA_VERSION`.

- [ ] **Step 1: Implement `WatchSettingsStore(context)` with `load(): WatchPreferences`, `save(prefs)`, `loadDebug(): String`, `appendDebug(text)`**
- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/life/app/watch/WatchSettings.kt
git commit -m "Store watch preferences outside Life backup JSON"
```

---

### Task 6: Gadgetbridge listen/send and incoming receiver

**Files:**
- Create: `app/src/main/java/com/life/app/watch/GadgetbridgeClient.kt`
- Create: `app/src/main/java/com/life/app/watch/WatchMessageReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `WatchMessages`, `WatchActions`, `TodayPath`, `WatchSettingsStore`, `WatchDebugLog`, `AndroidLifeRepository`
- Produces: `GadgetbridgeClient.registerListener(context)`, `GadgetbridgeClient.send(context, outgoing, deviceAddress: String?)`, `WatchMessageReceiver` handling incoming broadcasts

`registerListener` sends `SEND_WATCH_APP_MESSAGE` to `prefs.gadgetbridgePackage` with `appId=3266101`, no `payload`, and `replyTo` = `PendingIntent.getBroadcast` pointing at `WatchMessageReceiver` (immutable, update current). Include `device` only if a MAC is already known from the last incoming extra — never invent or log it. If `prefs.enabled` is false, do not register.

`send` sets extras `appId`, `payload` (same encoding as `zepp-os-message-test` phone app), optional `device`, and the same `replyTo`.

`WatchMessageReceiver.onReceive`:

1. `goAsync()`, read `appId`, `payload`, `device`.
2. If `appId` != `3266101`, ignore.
3. Decode incoming payload: `Base64.decode(payload, DEFAULT)` then UTF-8. On failure, record `bad_payload` and stop.
4. `WatchMessages.parseIncoming`. On null, record `bad_payload` and stop (write nothing).
5. Load `AppData` via `AndroidLifeRepository.appDataFlow.first()`, `WatchActions.apply`, `saveData` if data changed.
6. For `Sync` or after a successful write, reply with `WatchOutgoing.Habits` from `TodayPath.build` (so the watch refreshes). For `log_habit` / `note` / `want_done` also send `ok` or `error` first, then the updated `habits` list (two sends is fine; one combined send is not in the spec — send `ok`/`error`, then `habits` so the watch can show “Logged” and refresh).
7. Record debug events with sizes and types only.
8. `pending.finish()`.

Manifest additions inside `<application>`:

```xml
<receiver
    android:name=".watch.WatchMessageReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="nodomain.freeyourgadget.gadgetbridge.action.WATCH_APP_MESSAGE" />
    </intent-filter>
</receiver>
```

And beside `uses-permission`:

```xml
<queries>
    <package android:name="nodomain.freeyourgadget.gadgetbridge.zepptest" />
    <package android:name="nodomain.freeyourgadget.gadgetbridge" />
    <package android:name="nodomain.freeyourgadget.gadgetbridge.nightly" />
</queries>
```

`exported=true` is required so Gadgetbridge can deliver the explicit package-targeted broadcast.

- [ ] **Step 1: Implement client + receiver**
- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/life/app/watch/GadgetbridgeClient.kt app/src/main/java/com/life/app/watch/WatchMessageReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "Receive Gadgetbridge watch messages in Life"
```

---

### Task 7: Morning and evening digests

**Files:**
- Create: `app/src/main/java/com/life/app/watch/DigestScheduler.kt`
- Create: `app/src/main/java/com/life/app/watch/DigestReceiver.kt`
- Create: `app/src/main/java/com/life/app/watch/DigestNotifications.kt`

**Interfaces:**
- Consumes: `TodayPath.digest`, `WatchPreferences`, `WantNotifications` channel pattern
- Produces: `DigestScheduler.scheduleAll(context, prefs)` (next morning 08:00 and evening 20:00, or the stored hours), `DigestReceiver` showing an Android notification and, if the listener is enabled, sending `WatchOutgoing.Digest`

Follow `WantScheduler` / `WantReceiver` / `WantNotifications`: exact alarm when allowed, `goAsync`, `AndroidLifeRepository`. New channel id `path`, name `Today's path`, importance default. Notification titles: morning `Today's path`, evening `Still open`. Text is a comma-separated list of habit names and Want titles (Want titles are already user content on the phone notification, matching existing Want reminders). Do not write Want/note bodies into `WatchDebugLog`.

If `POST_NOTIFICATIONS` is denied, skip `notify` and record `notification=permission-denied`.

Request codes: stable ints `41001` morning, `41002` evening.

If the digest lists are both empty, do not show a notification (nothing useful to say; no shame empty-state).

Also send `WatchOutgoing.Digest` through `GadgetbridgeClient.send` when `prefs.enabled`.

- [ ] **Step 1: Implement scheduler, receiver, notifications**
- [ ] **Step 2: Register `DigestReceiver` in the manifest as `exported=false` (alarm is internal)**
- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/life/app/watch/DigestScheduler.kt app/src/main/java/com/life/app/watch/DigestReceiver.kt app/src/main/java/com/life/app/watch/DigestNotifications.kt app/src/main/AndroidManifest.xml
git commit -m "Schedule morning and evening path digest notifications"
```

---

### Task 8: Settings UI and startup registration

**Files:**
- Modify: `app/src/main/java/com/life/app/ui/SettingsScreen.kt` (add a “Watch” section after “Reminders”)
- Modify: `app/src/main/java/com/life/app/MainActivity.kt`
- Modify: `app/src/main/java/com/life/app/reminders/BootReceiver.kt`

**Interfaces:**
- Consumes: `WatchSettingsStore`, `GadgetbridgeClient.registerListener`, `DigestScheduler.scheduleAll`

Settings section copy, in this order:

- Switch: “Watch listener” bound to `prefs.enabled`. Subtitle: listener talks to Gadgetbridge (Zepp test) until Nightly has the bridge.
- Text status: “Registered” or “Off” from the last `registerListener` result (package exists + enabled).
- Outlined field: Gadgetbridge package, default `nodomain.freeyourgadget.gadgetbridge.zepptest`.
- Switch: morning digest (default on) and evening digest (default on).
- If notification permission is missing on API 33+, a line: “Notification permission is off, so digests will not fire.”
- “Debug log” showing `WatchSettingsStore.loadDebug()` in a small monospace block, plus a button that copies it with `ClipboardManager` labeled “Copy debug log”.

Saving package / toggles calls `save`, then `GadgetbridgeClient.registerListener` and `DigestScheduler.scheduleAll`.

`MainActivity.onCreate` (after `CaptureRuntime.initContext`): if watch enabled, `registerListener` and `scheduleAll`.

`BootReceiver`: after existing work, do the same.

- [ ] **Step 1: Implement UI + startup hooks**
- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/life/app/ui/SettingsScreen.kt app/src/main/java/com/life/app/MainActivity.kt app/src/main/java/com/life/app/reminders/BootReceiver.kt
git commit -m "Add Life watch settings and register the listener on start"
```

---

### Task 9: BIP Max watch app

**Files:**
- Create: `watch/` (Zepp OS sources, Zeus project)
- Mirror the BLE header and send/receive helpers from https://codeberg.org/markmarvik/zepp-os-message-test `watch/` (the app that already round-tripped). Change identity only.

`watch/app.json` (and `src/app.json` if Zeus expects it there):

- `app.appId`: `3266101`
- `app.appName`: `Life`
- `app.version`: `1.0.0`
- `targets` for Bip Max only, `deviceSource` `11206915`
- API min 3.0 / target 4.0
- `expiredTime`: a Unix timestamp at least one year ahead (not 172800 seconds from build)

UI:

- On open: send `{"v":1,"type":"sync"}`, show “Open Life on your phone” until `habits` arrives or 8 seconds pass.
- List: Want rows first (title, tap sends `want_done`), then habit rows (name; if `loggedToday` show a small mark; tap sends `log_habit`). After `ok` with matching id, show “Logged” on that row. After `habits` refresh, use server `loggedToday`.
- Note: input field + Send. Send `{"v":1,"type":"note","text":"..."}`. Show “Logged” only after `ok` / `ref=note`. Empty text does not send.
- On `error` / timeout: do not show “Logged”; show “Open Life on your phone”.
- Do not store a retry queue.

Build with the same Zeus CLI used for the test app (`@zeppos/zeus-cli`). Produce `life-3266101.zab` for Gadgetbridge File Installer.

- [ ] **Step 1: Scaffold `watch/` from the test app, change app id and UI**
- [ ] **Step 2: Build the `.zab` and keep sources in git (the binary can be a release artifact)**
- [ ] **Step 3: Commit**

```bash
git add watch/
git commit -m "Add BIP Max Life watch app sources"
```

---

### Task 10: Docs and repo notes

**Files:**
- Create: `docs/superpowers/specs/2026-11-10-life-watch-app-design.md` (copy the approved spec)
- Create: `docs/superpowers/plans/2026-11-10-life-watch-app.md` (this plan)
- Modify: `AGENTS.md` — add under Product rules: a BIP Max companion that only logs and reminds through Gadgetbridge is allowed; do not add Steady-style wearable sync, health metrics, or Path.
- Modify: `README.md` — one paragraph: optional BIP Max app in `watch/` logs habits and notes into Life through Gadgetbridge.

- [ ] **Step 1: Copy docs and update AGENTS.md / README.md**
- [ ] **Step 2: Run `./build.sh test`**

Expected: existing tests plus the four new watch test classes PASS.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers AGENTS.md README.md
git commit -m "Document the Life BIP Max watch companion"
```

---

### Task 11: Device proof (not done until this passes)

On Pix, with Nightly left installed:

1. Install the updated Life debug APK (`./build.sh installDebug`).
2. Open Life → Settings → confirm watch listener is on and package is `nodomain.freeyourgadget.gadgetbridge.zepptest`. Confirm debug log shows a register event.
3. Force-stop Nightly. Connect the BIP Max in Gadgetbridge (Zepp test). Turn on “Allow watch app messages”.
4. Install `life-3266101.zab` with File Installer. Open Life on the watch.
5. Sync shows habits and today’s/overdue Wants.
6. Tap a habit → timeline has a new habit event and the watch shows “Logged”. Debug log has `type=log_habit` and the habit id, not the name if you can avoid it (name is okay; note text is not).
7. Send a note → timeline has the note. Debug log has `type=note` and `bytes=…`, not the text.
8. Mark a Want done → it is Done in Life.
9. Trigger a digest (temporarily set the morning hour to the current hour, or send the alarm) and confirm an Android notification appears. Empty digest stays silent.
10. Nightly package `nodomain.freeyourgadget.gadgetbridge.nightly` is still installed.

Do not call this done until steps 5–10 pass. Do not claim support for every Zepp OS watch.

---

## Self-review

Spec coverage:

- Habits + notes + Want done → Task 2, 9
- Listener inside Life, replyTo, drop if not listening → Task 6, 8
- Today’s path ranking, unlogged first, overdue+today Wants → Task 3
- Morning/evening digests + existing Want reminders untouched → Task 7
- Settings (enable, GB package, status, permission, debug log) → Task 4, 5, 8
- No queue, no Logged without ok → Task 6, 9
- Logging redaction → Task 4, 11
- Watch sources in `watch/`, long expiry, Bip Max deviceSource → Task 9
- Sidecar GB default, Nightly untouched → Task 5, 11
- Money/voice/schedules out of scope → no task adds them
- Device proof → Task 11

Type names used across tasks: `WatchIncoming`, `WatchOutgoing`, `WatchHabitItem`, `WatchWantItem`, `WatchMessages`, `WatchActions.Result`, `TodayPath`, `WatchPreferences`, `WatchSettings.APP_ID`, `WatchSettings.DEFAULT_GB_PACKAGE`.
