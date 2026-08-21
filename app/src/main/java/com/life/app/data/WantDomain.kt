package com.life.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object WantDomain {
    const val BASE_WORDS = 10
    const val STEP = 5
    const val CAP = 40

    private val dueFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val wordRe = Regex("\\w+")

    fun wordCount(text: String): Int = wordRe.findAll(text).count()

    fun wordFloor(streak: Int): Int =
        (BASE_WORDS + STEP * streak.coerceAtLeast(0)).coerceAtMost(CAP)

    fun canSubmitReflection(text: String, streak: Int): Boolean =
        wordCount(text) >= wordFloor(streak)

    fun parseDue(dueDate: String?): LocalDate? {
        val s = dueDate?.trim().orEmpty()
        if (s.isEmpty()) return null
        return try {
            LocalDate.parse(s)
        } catch (_: Exception) {
            null
        }
    }

    fun isOverdue(event: LifeEvent, today: LocalDate): Boolean {
        if (!event.isOpenWant()) return false
        val due = parseDue(event.dueDate) ?: return false
        return due.isBefore(today)
    }

    fun upcoming(data: AppData, today: LocalDate): List<LifeEvent> =
        data.events.filter { it.isOpenWant() }
            .sortedWith(
                compareBy<LifeEvent> { parseDue(it.dueDate) ?: LocalDate.MAX }
                    .thenBy { it.createdAt }
            )

    fun resolveDueOnSave(isWant: Boolean, dueDate: String?, today: LocalDate): String? {
        if (!isWant) return dueDate
        return dueDate ?: today.toString()
    }

    fun upcomingLabel(dueDate: String?, today: LocalDate): String {
        val due = parseDue(dueDate) ?: return "Someday"
        return when {
            due.isBefore(today) -> "Overdue"
            due == today -> "Today"
            due == today.plusDays(1) -> "Tomorrow"
            else -> due.format(dueFmt)
        }
    }

    fun historyLabel(dueDate: String?, today: LocalDate): String {
        val due = parseDue(dueDate) ?: return ""
        return when (due) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> due.format(dueFmt)
        }
    }

    fun withCompletedWant(data: AppData, id: String, nowMs: Long): AppData {
        val event = data.events.firstOrNull { it.id == id } ?: return data
        if (!event.isOpenWant()) return data
        return data
            .withEvent(
                event.copy(
                    wantStatus = WantStatus.DONE,
                    resolvedAt = nowMs
                )
            )
            .copy(wantStrikeStreak = 0)
    }

    fun withFailedWant(
        data: AppData,
        id: String,
        reflection: String,
        nowMs: Long,
        today: LocalDate
    ): AppData {
        val event = data.events.firstOrNull { it.id == id } ?: return data
        if (!event.isOpenWant()) return data
        if (!canSubmitReflection(reflection, data.wantStrikeStreak)) return data
        val missed = isOverdue(event, today)
        return data
            .withEvent(
                event.copy(
                    wantStatus = if (missed) WantStatus.MISSED else WantStatus.CANCELED,
                    reflection = reflection.trim(),
                    resolvedAt = nowMs
                )
            )
            .copy(wantStrikeStreak = data.wantStrikeStreak + 1)
    }

    fun applyWantFields(
        event: LifeEvent,
        isWant: Boolean,
        dueDate: String?,
        today: LocalDate
    ): LifeEvent {
        val due = resolveDueOnSave(isWant, dueDate, today)
        if (!isWant) {
            return event.copy(
                isWant = false,
                dueDate = dueDate,
                wantStatus = WantStatus.NONE,
                reflection = if (event.wantStatus == WantStatus.OPEN) "" else event.reflection
            )
        }
        val status = when (event.wantStatus) {
            WantStatus.DONE, WantStatus.CANCELED, WantStatus.MISSED -> event.wantStatus
            else -> WantStatus.OPEN
        }
        return event.copy(
            isWant = true,
            dueDate = due,
            wantStatus = status
        )
    }
}
