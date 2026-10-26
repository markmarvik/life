package com.life.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class HabitStreak(
    val current: Int,
    val best: Int,
    val loggedToday: Boolean,
    val recent: List<Boolean>
) {
    val recentDone: Int get() = recent.count { it }
}

data class TodayHabit(
    val id: String,
    val name: String,
    val icon: String,
    val streak: HabitStreak
)

object HabitStreaks {

    const val RECENT_DAYS = 7

    fun forAll(
        data: AppData,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<String, HabitStreak> {
        val days = daysByHabit(data, zone, today)
        val ids = LinkedHashSet<String>()
        data.habits.forEach { ids.add(it.id) }
        ids.addAll(days.keys)
        return ids.associateWith { compute(days[it].orEmpty(), today) }
    }

    fun todayRow(
        data: AppData,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TodayHabit> {
        val streaks = forAll(data, today, zone)
        val active = data.habits.filter { !it.archived }
        val last = lastLogAt(data)
        val source: List<Pair<String, Triple<String, String, HabitStreak>>> = if (active.isNotEmpty()) {
            active.sortedWith(
                compareByDescending<HabitDef> { last[it.id] ?: 0L }
                    .thenBy { it.name.lowercase() }
            ).map { habit ->
                habit.id to Triple(
                    habit.name,
                    habit.icon.ifBlank { "•" },
                    streaks[habit.id] ?: compute(emptySet(), today)
                )
            }
        } else {
            val taken = data.habits.map { it.id }.toSet()
            HabitCatalog.stillSuggested(data).filter { it.id !in taken }.take(8).map { cat ->
                cat.id to Triple(cat.name, cat.icon, compute(emptySet(), today))
            }
        }
        return source.map { (id, row) ->
            TodayHabit(id, row.first, row.second, row.third)
        }
    }

    fun toggleToday(
        data: AppData,
        habitId: String,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): AppData {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val todayIds = data.events.mapNotNull { event ->
            if (event.kind != EventKind.HABIT || event.habitId != habitId) return@mapNotNull null
            val day = Instant.ofEpochMilli(event.createdAt).atZone(zone).toLocalDate()
            if (day == today) event.id else null
        }.toSet()
        if (todayIds.isNotEmpty()) {
            return data.copy(events = data.events.filterNot { it.id in todayIds })
        }
        val existing = data.habitById(habitId)
        if (existing?.archived == true) return data
        val habit = existing ?: HabitCatalog.byId(habitId)?.toDef(nowMs) ?: return data
        val base = if (existing == null) data.withHabit(habit) else data
        return base.withEvent(
            LifeEvent(
                id = UUID.randomUUID().toString(),
                kind = EventKind.HABIT,
                createdAt = nowMs,
                habitId = habit.id
            )
        )
    }

    internal fun compute(dates: Set<LocalDate>, today: LocalDate): HabitStreak {
        val days = dates.filter { !it.isAfter(today) }.toSet()
        val loggedToday = today in days
        var current = 0
        var cursor = if (loggedToday) today else today.minusDays(1)
        while (cursor in days && current < 20000) {
            current++
            cursor = cursor.minusDays(1)
        }
        var best = 0
        if (days.isNotEmpty()) {
            val sorted = days.sorted()
            var run = 1
            best = 1
            for (i in 1 until sorted.size) {
                val prev = sorted[i - 1]
                val date = sorted[i]
                if (date == prev.plusDays(1)) {
                    run++
                    if (run > best) best = run
                } else if (date != prev) {
                    run = 1
                }
            }
        }
        if (current > best) best = current
        val recent = (RECENT_DAYS - 1 downTo 0).map { offset ->
            today.minusDays(offset.toLong()) in days
        }
        return HabitStreak(current, best, loggedToday, recent)
    }

    private fun daysByHabit(
        data: AppData,
        zone: ZoneId,
        today: LocalDate
    ): Map<String, Set<LocalDate>> {
        val out = mutableMapOf<String, MutableSet<LocalDate>>()
        for (event in data.events) {
            if (event.kind != EventKind.HABIT) continue
            val id = event.habitId ?: continue
            val day = Instant.ofEpochMilli(event.createdAt).atZone(zone).toLocalDate()
            if (day.isAfter(today)) continue
            out.getOrPut(id) { mutableSetOf() }.add(day)
        }
        return out
    }

    private fun lastLogAt(data: AppData): Map<String, Long> {
        val last = mutableMapOf<String, Long>()
        for (event in data.events) {
            if (event.kind != EventKind.HABIT) continue
            val id = event.habitId ?: continue
            val prev = last[id]
            if (prev == null || event.createdAt > prev) last[id] = event.createdAt
        }
        return last
    }
}
