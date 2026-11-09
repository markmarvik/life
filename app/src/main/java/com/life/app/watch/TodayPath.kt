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
