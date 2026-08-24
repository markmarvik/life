package com.life.app.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
enum class EventKind { HABIT, NOTE, FINANCE }

@Serializable
enum class MoneyDirection { IN, OUT }

@Serializable
enum class WantStatus { NONE, OPEN, DONE, CANCELED, MISSED }

@Serializable
enum class RecurringCadence { WEEKLY, MONTHLY, YEARLY }

@Serializable
data class RecurringDef(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val direction: MoneyDirection = MoneyDirection.OUT,
    val cadence: RecurringCadence = RecurringCadence.MONTHLY,
    val until: String? = null,
    val createdAt: Long = 0L
) {
    fun isActive(today: LocalDate): Boolean {
        val end = until?.let {
            try {
                LocalDate.parse(it)
            } catch (_: Exception) {
                null
            }
        } ?: return true
        return !today.isAfter(end)
    }

    fun monthlyAmountMinor(): Long = when (cadence) {
        RecurringCadence.WEEKLY -> amountMinor * 4
        RecurringCadence.MONTHLY -> amountMinor
        RecurringCadence.YEARLY -> amountMinor / 12
    }
}

@Serializable
data class PlannedItem(
    val id: String,
    val name: String,
    val amountMinor: Long,
    val createdAt: Long = 0L
)

@Serializable
data class HabitDef(
    val id: String,
    val name: String,
    val icon: String = "",
    val why: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0L
)

@Serializable
data class LifeEvent(
    val id: String,
    val kind: EventKind,
    val createdAt: Long,
    val habitId: String? = null,
    val body: String = "",
    val tags: List<String> = emptyList(),
    val amountMinor: Long? = null,
    val direction: MoneyDirection? = null,
    val payee: String = "",
    val category: String = "",
    /** yyyy-MM-dd when this note is dated or a Want. */
    val dueDate: String? = null,
    val isWant: Boolean = false,
    val wantStatus: WantStatus = WantStatus.NONE,
    val reflection: String = "",
    val resolvedAt: Long? = null
) {
    fun isOpenWant(): Boolean = isWant && wantStatus == WantStatus.OPEN

    fun timelineAt(): Long =
        if (isWant && wantStatus != WantStatus.NONE && wantStatus != WantStatus.OPEN) {
            resolvedAt ?: createdAt
        } else {
            createdAt
        }
}

@Serializable
data class AppData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val habits: List<HabitDef> = emptyList(),
    val events: List<LifeEvent> = emptyList(),
    val customTags: List<String> = emptyList(),
    val payees: List<String> = emptyList(),
    val categories: List<String> = Seed.FINANCE_CATEGORIES,
    val openingBalanceMinor: Long = 0L,
    val currencyCode: String = "USD",
    val colorScheme: String = "default",
    val backgroundMode: String = "dark",
    val wantStrikeStreak: Int = 0,
    val wantRemindersEnabled: Boolean = true,
    val recurring: List<RecurringDef> = emptyList(),
    val planned: List<PlannedItem> = emptyList(),
    val monthlySavingsMinor: Long = 0L
) {
    companion object {
        const val SCHEMA_VERSION = 3
    }
}

object Seed {
    val NOTE_TAGS: List<String> = listOf("Ideas", "Thoughts", "Work", "Health", "Gratitude")

    val FINANCE_CATEGORIES: List<String> = listOf(
        "Food", "Transport", "Rent", "Groceries", "Health",
        "Fun", "Shopping", "Bills", "Salary", "Other"
    )
}

fun AppData.balanceMinor(): Long {
    var total = openingBalanceMinor
    for (e in events) {
        if (e.kind != EventKind.FINANCE) continue
        val a = e.amountMinor ?: 0L
        total += if (e.direction == MoneyDirection.IN) a else -a
    }
    return total
}

fun AppData.habitById(id: String): HabitDef? = habits.firstOrNull { it.id == id }

fun AppData.findHabitByName(name: String): HabitDef? {
    val n = name.trim()
    if (n.isEmpty()) return null
    return habits.firstOrNull { !it.archived && it.name.equals(n, ignoreCase = true) }
}

fun AppData.withEvent(event: LifeEvent): AppData {
    val without = events.filterNot { it.id == event.id }
    return copy(events = listOf(event) + without)
}

fun AppData.withoutEvent(id: String): AppData = copy(events = events.filterNot { it.id == id })

fun AppData.withHabit(habit: HabitDef): AppData {
    val without = habits.filterNot { it.id == habit.id }
    return copy(habits = without + habit)
}

fun AppData.withPayee(name: String): AppData {
    val n = name.trim()
    if (n.isEmpty()) return this
    if (payees.any { it.equals(n, ignoreCase = true) }) return this
    return copy(payees = listOf(n) + payees)
}

fun AppData.withCategory(name: String): AppData {
    val n = name.trim()
    if (n.isEmpty()) return this
    if (categories.any { it.equals(n, ignoreCase = true) }) return this
    return copy(categories = categories + n)
}

fun AppData.withTag(name: String): AppData {
    val n = name.trim()
    if (n.isEmpty()) return this
    if (Seed.NOTE_TAGS.any { it.equals(n, ignoreCase = true) }) return this
    if (customTags.any { it.equals(n, ignoreCase = true) }) return this
    return copy(customTags = customTags + n)
}

fun AppData.availableTags(): List<String> {
    val used = events.asSequence()
        .filter { it.kind == EventKind.NOTE }
        .flatMap { it.tags }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    val all = LinkedHashSet<String>()
    all.addAll(Seed.NOTE_TAGS)
    all.addAll(customTags)
    all.addAll(used)
    return all.toList()
}

data class DayGroup(
    val date: String,
    val label: String,
    val events: List<LifeEvent>
)

fun groupEvents(
    events: List<LifeEvent>,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): List<DayGroup> {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    return events
        .filterNot { it.isOpenWant() }
        .sortedByDescending { it.timelineAt() }
        .groupBy { Instant.ofEpochMilli(it.timelineAt()).atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending<LocalDate> { it })
        .map { (date, evs) ->
            val label = when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> date.format(fmt)
            }
            DayGroup(date.toString(), label, evs.sortedByDescending { it.timelineAt() })
        }
}
