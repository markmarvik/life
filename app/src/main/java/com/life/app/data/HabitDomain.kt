package com.life.app.data

import java.util.UUID

object HabitDomain {

    fun newId(): String = "h_${UUID.randomUUID().toString().take(8)}"

    fun logCount(data: AppData, habitId: String): Int =
        data.events.count { it.kind == EventKind.HABIT && it.habitId == habitId }

    /**
     * Rename / re-icon an existing habit. Every timeline log that points at
     * [habitId] keeps that id, so the whole habit history shows the new name.
     *
     * If the new name already belongs to a different habit, logs are merged
     * onto that habit and this definition is dropped.
     */
    fun edit(data: AppData, habitId: String, name: String, icon: String): AppData {
        val habit = data.habitById(habitId) ?: return data
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return data
        val nextIcon = icon.trim().ifBlank { habit.icon }

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

        val updated = habit.copy(name = trimmed, icon = nextIcon)
        return data.copy(
            habits = data.habits.map { if (it.id == habitId) updated else it }
        )
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
