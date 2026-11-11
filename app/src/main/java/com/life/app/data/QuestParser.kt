package com.life.app.data

import java.time.LocalDate
import java.time.LocalTime

data class QuestDay(val date: LocalDate, val quests: List<Quest>)
data class Quest(val id: String, val title: String, val xp: Int, val due: LocalTime?)

object QuestParser {
    fun parse(text: String, today: LocalDate): QuestDay {
        val normalizedText = text.replace(Regex("\r\n?"), "\n")
        val lines = normalizedText.split("\n")

        var date: LocalDate? = null
        val headerRegex = Regex("(?i)^QUESTS?\\s*:?\\s*(\\d{4}-\\d{2}-\\d{2})\\b")
        for (line in lines) {
            val matchResult = headerRegex.matchEntire(line)
            if (matchResult != null) {
                val dateString = matchResult.groupValues[1]
                try {
                    date = LocalDate.parse(dateString)
                    break
                } catch (e: Exception) {
                }
            }
        }
        date = date ?: today

        val quests = mutableListOf<Quest>()
        var questCount = 0

        for (line in lines) {
            if (questCount >= 20) break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•")) {
                val questLine = trimmed.substring(1).trim()
                val parts = questLine.split(Regex("\\s*\\|\\s*")).map { it.trim() }
                if (parts.isEmpty()) continue
                val title = parts[0].trim()
                if (title.isEmpty()) continue

                var xp: Int = 10
                var due: LocalTime? = null

                if (parts.size >= 2) {
                    val xpStr = parts[1]
                    if (xpStr.matches(Regex("\\d+"))) {
                        xp = xpStr.toInt()
                    } else {
                        if (parts.size == 2) {
                            due = parseDueTime(xpStr)
                            xp = 10
                        } else {
                            xp = 10
                        }
                    }
                }

                if (parts.size >= 3) {
                    due = parseDueTime(parts[2])
                }

                var slug = title.lowercase()
                slug = slug.replace(Regex("[^a-z0-9]+"), "-")
                slug = slug.trim { it == '-' }
                if (slug.isEmpty()) {
                    slug = "quest"
                }
                val baseId = "q-${date}-$slug"

                var id = baseId
                var suffix = 2
                while (quests.any { it.id == id }) {
                    id = "$baseId-$suffix"
                    suffix++
                }

                quests.add(Quest(id, title, xp, due))
                questCount++
            }
        }

        return QuestDay(date, quests)
    }

    private fun parseDueTime(timeStr: String): LocalTime? {
        val match = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(timeStr) ?: return null
        return LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }
}
