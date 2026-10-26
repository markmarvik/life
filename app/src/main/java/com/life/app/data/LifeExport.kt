package com.life.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class LifeExport(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,
    val zone: String,
    val habits: List<ExportHabit> = emptyList(),
    val entries: List<ExportEntry> = emptyList()
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class ExportHabit(
    val id: String,
    val name: String,
    val icon: String = "",
    val why: String = "",
    val archived: Boolean = false,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val loggedToday: Boolean = false
)

@Serializable
data class ExportEntry(
    val id: String,
    val kind: String,
    val at: String,
    val date: String,
    val habitId: String? = null,
    val body: String = "",
    val tags: List<String> = emptyList(),
    val amountMinor: Long? = null,
    val direction: String? = null,
    val payee: String = "",
    val category: String = "",
    val dueDate: String? = null,
    val isWant: Boolean = false,
    val wantStatus: String = "NONE",
    val reflection: String = "",
    val resolvedAt: String? = null
)

object LifeExportFormat {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun build(
        data: AppData,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): LifeExport {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val streaks = HabitStreaks.forAll(data, today, zone)
        val habits = data.habits.map { habit ->
            val streak = streaks[habit.id] ?: HabitStreaks.compute(emptySet(), today)
            ExportHabit(
                id = habit.id,
                name = habit.name,
                icon = habit.icon,
                why = habit.why,
                archived = habit.archived,
                currentStreak = streak.current,
                bestStreak = streak.best,
                loggedToday = streak.loggedToday
            )
        }
        val entries = data.events
            .sortedWith(compareBy<LifeEvent> { it.createdAt }.thenBy { it.id })
            .map { event -> toEntry(event, zone) }
        return LifeExport(
            schemaVersion = LifeExport.SCHEMA_VERSION,
            exportedAt = Instant.ofEpochMilli(nowMs).toString(),
            zone = zone.id,
            habits = habits,
            entries = entries
        )
    }

    fun toJson(export: LifeExport): String = json.encodeToString(export)

    fun toMarkdown(export: LifeExport): String {
        val names = export.habits.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append("# Life export\n\n")
        sb.append("- schemaVersion: ").append(export.schemaVersion).append('\n')
        sb.append("- exportedAt: ").append(export.exportedAt).append('\n')
        sb.append("- zone: ").append(export.zone).append("\n\n")
        sb.append("## Habits\n\n")
        sb.append("| Name | Current streak | Best streak | Logged today |\n")
        sb.append("| --- | --- | --- | --- |\n")
        if (export.habits.isEmpty()) {
            sb.append("| — | 0 | 0 | no |\n")
        } else {
            for (habit in export.habits) {
                sb.append("| ")
                    .append(cell(habit.name))
                    .append(" | ")
                    .append(habit.currentStreak)
                    .append(" | ")
                    .append(habit.bestStreak)
                    .append(" | ")
                    .append(if (habit.loggedToday) "yes" else "no")
                    .append(" |\n")
            }
        }
        sb.append("\n## Entries\n\n")
        sb.append("| Date | Time | Kind | Detail |\n")
        sb.append("| --- | --- | --- | --- |\n")
        if (export.entries.isEmpty()) {
            sb.append("| — | — | — | — |\n")
        } else {
            for (entry in export.entries) {
                val time = runCatching {
                    Instant.parse(entry.at).atZone(ZoneId.of(export.zone)).toLocalTime().format(timeFmt)
                }.getOrDefault("")
                sb.append("| ")
                    .append(cell(entry.date))
                    .append(" | ")
                    .append(cell(time))
                    .append(" | ")
                    .append(cell(entry.kind))
                    .append(" | ")
                    .append(cell(detail(entry, names)))
                    .append(" |\n")
            }
        }
        sb.append('\n')
        return sb.toString()
    }

    private fun toEntry(event: LifeEvent, zone: ZoneId): ExportEntry {
        val zoned = Instant.ofEpochMilli(event.createdAt).atZone(zone)
        return ExportEntry(
            id = event.id,
            kind = event.kind.name,
            at = Instant.ofEpochMilli(event.createdAt).toString(),
            date = zoned.toLocalDate().toString(),
            habitId = event.habitId,
            body = event.body,
            tags = event.tags,
            amountMinor = event.amountMinor,
            direction = event.direction?.name,
            payee = event.payee,
            category = event.category,
            dueDate = event.dueDate,
            isWant = event.isWant,
            wantStatus = event.wantStatus.name,
            reflection = event.reflection,
            resolvedAt = event.resolvedAt?.let { Instant.ofEpochMilli(it).toString() }
        )
    }

    private fun detail(entry: ExportEntry, names: Map<String, String>): String = when (entry.kind) {
        "HABIT" -> names[entry.habitId] ?: entry.habitId ?: "Habit"
        "FINANCE" -> buildString {
            append(if (entry.direction == "IN") "+" else "-")
            append(entry.amountMinor ?: 0L)
            if (entry.payee.isNotBlank()) append(' ').append(entry.payee)
            if (entry.category.isNotBlank()) append(" (").append(entry.category).append(')')
            if (entry.body.isNotBlank()) append(" — ").append(entry.body)
        }
        else -> entry.body
    }

    private fun cell(value: String): String =
        value.replace("|", "\\|").replace("\n", " ").trim()
}
