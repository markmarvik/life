package com.life.app.data

import java.time.Instant
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln

data class HabitSuggestion(
    val name: String,
    val icon: String,
    val why: String = "",
    val habitId: String?,
    val score: Double,
    val isCreate: Boolean = false
) {
    /** Premade catalog rows and saved habits are long-press editable. */
    val canEdit: Boolean get() = !isCreate && !habitId.isNullOrBlank()
}

object HabitRanker {

    data class Usage(
        val count30d: Int,
        val lastAt: Long?,
        val hourHits: Int,
        val hourTotal: Int
    )

    fun suggest(
        data: AppData,
        nowMs: Long,
        query: String,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<HabitSuggestion> {
        val q = query.trim()
        val usage = usageByHabit(data, nowMs, zone)

        val created = data.habits.filter { !it.archived }.map { habit ->
            val u = usage[habit.id]
            HabitSuggestion(
                name = habit.name,
                icon = habit.icon.ifBlank { "•" },
                why = habit.displayWhy(),
                habitId = habit.id,
                score = usageScore(u, nowMs) + 0.5
            )
        }

        val catalog = HabitCatalog.stillSuggested(data)
            .map { cat ->
                HabitSuggestion(
                    name = cat.name,
                    icon = cat.icon,
                    why = cat.why,
                    habitId = cat.id,
                    score = 0.05
                )
            }

        val all = (created + catalog).distinctBy { it.habitId ?: "create:${it.name.lowercase()}" }

        val filtered = if (q.isEmpty()) {
            all.sortedWith(
                compareByDescending<HabitSuggestion> { it.habitId != null && it.habitId in usage }
                    .thenByDescending { it.score }
                    .thenBy { it.name.lowercase() }
            )
        } else {
            all.mapNotNull { s ->
                val f = Fuzzy.score(q, s.name)
                if (f < 0.45) null else s.copy(score = f * 4.0 + s.score)
            }.sortedByDescending { it.score }
        }

        val exact = q.isNotEmpty() && all.any { it.name.equals(q, ignoreCase = true) }
        if (q.isNotEmpty() && !exact) {
            val create = HabitSuggestion(
                name = q,
                icon = "＋",
                why = "Create and log",
                habitId = null,
                score = 100.0,
                isCreate = true
            )
            return listOf(create) + filtered
        }
        return filtered
    }

    private fun usageByHabit(
        data: AppData,
        nowMs: Long,
        zone: ZoneId
    ): Map<String, Usage> {
        val windowStart = nowMs - 30L * 24 * 60 * 60 * 1000
        val currentHour = Instant.ofEpochMilli(nowMs).atZone(zone).hour
        val hours = setOf((currentHour + 23) % 24, currentHour, (currentHour + 1) % 24)
        val acc = mutableMapOf<String, MutableList<LifeEvent>>()
        for (e in data.events) {
            if (e.kind != EventKind.HABIT) continue
            val id = e.habitId ?: continue
            acc.getOrPut(id) { mutableListOf() }.add(e)
        }
        return acc.mapValues { (_, evs) ->
            val last = evs.maxOfOrNull { it.createdAt }
            val count30 = evs.count { it.createdAt >= windowStart }
            var hits = 0
            var total = 0
            for (e in evs) {
                total++
                val h = Instant.ofEpochMilli(e.createdAt).atZone(zone).hour
                if (h in hours) hits++
            }
            Usage(count30, last, hits, total)
        }
    }

    internal fun usageScore(u: Usage?, nowMs: Long): Double {
        if (u == null) return 0.0
        val freq = 3.0 * ln(1.0 + u.count30d)
        val recency = if (u.lastAt != null) {
            val hours = ((nowMs - u.lastAt).coerceAtLeast(0)).toDouble() / 3_600_000.0
            2.0 * exp(-hours / 36.0)
        } else {
            0.0
        }
        val affinity = if (u.hourTotal > 0) 2.5 * (u.hourHits.toDouble() / u.hourTotal) else 0.0
        return freq + recency + affinity
    }
}
