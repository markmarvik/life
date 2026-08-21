package com.life.app.data

import java.util.UUID

object HabitDomain {

    fun newId(): String = "h_${UUID.randomUUID().toString().take(8)}"

    fun logCount(data: AppData, habitId: String): Int =
        data.events.count { it.kind == EventKind.HABIT && it.habitId == habitId }

    /** Existing def, or a catalog template so premade rows can be edited before first log. */
    fun defForEdit(data: AppData, habitId: String): HabitDef? {
        val existing = data.habitById(habitId)
        val cat = HabitCatalog.byId(habitId)
        if (existing != null) {
            return if (existing.why.isBlank() && cat != null) existing.copy(why = cat.why) else existing
        }
        return cat?.toDef(createdAt = 0L)
    }

    /**
     * Rename / re-icon an existing habit, or claim a premade catalog slot.
     * Claiming uses the catalog id so the default row does not come back
     * beside the edited one.
     *
     * Logs keep the same [habitId]. If the new name already belongs to a
     * different habit, logs are merged onto that habit and this definition
     * is dropped.
     */
    fun edit(
        data: AppData,
        habitId: String,
        name: String,
        icon: String,
        why: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): AppData {
        val catalog = HabitCatalog.byId(habitId)
        val habit = data.habitById(habitId) ?: catalog?.toDef(nowMs) ?: return data
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return data
        val nextIcon = icon.trim().ifBlank { habit.icon }
        val nextWhy = why?.trim() ?: habit.why

        val other = data.findHabitByName(trimmed)?.takeIf { it.id != habitId }
        if (other != null) {
            return data.copy(
                habits = data.habits.filterNot { it.id == habitId },
                events = data.events.map { e ->
                    if (e.kind == EventKind.HABIT && e.habitId == habitId) {
                        e.copy(habitId = other.id)
                    } else {
                        e
                    }
                }
            )
        }

        val updated = habit.copy(name = trimmed, icon = nextIcon, why = nextWhy)
        return if (data.habitById(habitId) == null) {
            data.copy(habits = data.habits + updated)
        } else {
            data.copy(habits = data.habits.map { if (it.id == habitId) updated else it })
        }
    }

    /**
     * Insert a new habit with [name] / [icon]. Existing logs stay on their
     * original habit. If [reassignEventId] is set, that one log moves onto
     * the new habit.
     *
     * A same-name habit is reused instead of duplicating.
     */
    fun saveAsNew(
        data: AppData,
        name: String,
        icon: String,
        why: String = "",
        reassignEventId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): AppData {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return data
        val existing = data.findHabitByName(trimmed)
        val habit = existing ?: run {
            val catalog = HabitCatalog.byName(trimmed)
            HabitDef(
                id = newId(),
                name = trimmed,
                icon = icon.trim().ifBlank { catalog?.icon ?: "•" },
                why = why.trim().ifBlank { catalog?.why.orEmpty() },
                createdAt = nowMs
            )
        }
        var next = if (existing == null) data.copy(habits = data.habits + habit) else data
        if (reassignEventId != null) {
            next = next.copy(
                events = next.events.map { e ->
                    if (e.id == reassignEventId && e.kind == EventKind.HABIT) {
                        e.copy(habitId = habit.id)
                    } else {
                        e
                    }
                }
            )
        }
        return next
    }
}
