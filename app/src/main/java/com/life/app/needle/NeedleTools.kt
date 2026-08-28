package com.life.app.needle

import com.life.app.data.AppData
import com.life.app.data.HabitCatalog
import com.life.app.data.balanceMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object NeedleTools {
    /** Five tools. Needle's window is ~256 tokens — keep this tiny. */
    val JSON: String = """
        [
          {"name":"log_habit","description":"Log a habit that just happened. EN+SR (kreatin/creatine, zube/brush, trening/gym).","parameters":{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}},
          {"name":"add_note","description":"Save a thought. Set want_date to make a dated want.","parameters":{"type":"object","properties":{"body":{"type":"string"},"want_date":{"type":"string","description":"today, tomorrow, or YYYY-MM-DD"}},"required":["body"]}},
          {"name":"log_finance","description":"Log money in major units. spent/platio=out, plata/salary=in.","parameters":{"type":"object","properties":{"sign":{"type":"string","enum":["in","out"]},"amount":{"type":"number"},"payee":{"type":"string"},"category":{"type":"string"}},"required":["sign","amount"]}},
          {"name":"complete_want","description":"Mark an open want done. Pass the want text.","parameters":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}},
          {"name":"query_balance","description":"Read cash balance. Do not log.","parameters":{"type":"object","properties":{}}}
        ]
    """.trimIndent().replace(Regex("\\s+"), " ")

    private val dateFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm", Locale.US)

    fun systemPrompt(
        data: AppData,
        now: java.time.ZonedDateTime = java.time.ZonedDateTime.now(),
        locale: Locale = Locale.getDefault()
    ): String {
        val habits = linkedSetOf<String>()
        for (h in data.habits) if (!h.archived) habits += h.name
        for (c in HabitCatalog.ALL) habits += c.name
        val habitList = habits.take(18).joinToString(", ")
        val wants = data.events
            .filter { it.isOpenWant() }
            .take(6)
            .joinToString("; ") { it.body.take(40) }
        val loc = locale.toLanguageTag()
        return buildString {
            append("date: ").append(now.format(dateFmt)).append("; locale: ").append(loc)
            append("; currency: ").append(data.currencyCode)
            append("; device: phone")
            append("\nhabits: ").append(habitList.ifBlank { "none" })
            if (wants.isNotEmpty()) append("\nopen wants: ").append(wants)
        }
    }

    fun fold(s: String): String =
        s.lowercase()
            .replace('č', 'c')
            .replace('ć', 'c')
            .replace('š', 's')
            .replace('ž', 'z')
            .replace("đ", "dj")

    fun parseWantDate(raw: String?, today: LocalDate): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val f = fold(t)
        return when {
            f == "today" || f == "danas" -> today.toString()
            f == "tomorrow" || f == "sutra" -> today.plusDays(1).toString()
            else -> try {
                LocalDate.parse(t.take(10)).toString()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun formatBalance(data: AppData): String =
        com.life.app.data.Money.format(data.balanceMinor(), data.currencyCode)
}
