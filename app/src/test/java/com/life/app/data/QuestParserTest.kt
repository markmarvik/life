package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class QuestParserTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 10)
    private val questDay: LocalDate = LocalDate.of(2026, 11, 11)

    private val sample = """
        QUESTS 2026-11-11
        - Record intro for the video | 50 | 10:00
        - Gym session | 30
        - Reply to 3 leads | 20 | 16:30
    """.trimIndent()

    @Test
    fun parsesCanonicalSample() {
        val day = QuestParser.parse(sample, today)
        assertEquals(questDay, day.date)
        assertEquals(3, day.quests.size)
        val intro = day.quests[0]
        assertEquals("q-2026-11-11-record-intro-for-the-video", intro.id)
        assertEquals("Record intro for the video", intro.title)
        assertEquals(50, intro.xp)
        assertEquals(LocalTime.of(10, 0), intro.due)
        val gym = day.quests[1]
        assertEquals("q-2026-11-11-gym-session", gym.id)
        assertEquals("Gym session", gym.title)
        assertEquals(30, gym.xp)
        assertNull(gym.due)
        val leads = day.quests[2]
        assertEquals("q-2026-11-11-reply-to-3-leads", leads.id)
        assertEquals("Reply to 3 leads", leads.title)
        assertEquals(20, leads.xp)
        assertEquals(LocalTime.of(16, 30), leads.due)
    }

    @Test
    fun missingHeaderUsesToday() {
        val day = QuestParser.parse("- Gym session | 30", today)
        assertEquals(today, day.date)
        assertEquals(1, day.quests.size)
        assertEquals("q-2026-11-10-gym-session", day.quests[0].id)
        assertEquals("Gym session", day.quests[0].title)
        assertEquals(30, day.quests[0].xp)
        assertNull(day.quests[0].due)
    }

    @Test
    fun headerIsCaseInsensitiveAndColonIsOptional() {
        val day = QuestParser.parse("quests: 2026-12-01\n- Gym session | 30", today)
        assertEquals(LocalDate.of(2026, 12, 1), day.date)
        assertEquals("q-2026-12-01-gym-session", day.quests[0].id)
    }

    @Test
    fun acceptsStarAndBulletMarkersAndExtraSpaces() {
        val text = "QUESTS 2026-11-11\n  *   Gym session  |  30  \n  •   Reply to 3 leads|20|16:30"
        val day = QuestParser.parse(text, today)
        assertEquals(2, day.quests.size)
        assertEquals("Gym session", day.quests[0].title)
        assertEquals(30, day.quests[0].xp)
        assertEquals("Reply to 3 leads", day.quests[1].title)
        assertEquals(20, day.quests[1].xp)
        assertEquals(LocalTime.of(16, 30), day.quests[1].due)
    }

    @Test
    fun ignoresBlankLinesJunkAndWindowsNewlines() {
        val text = "QUESTS 2026-11-11\r\n\r\nhello world\r\n- Gym session | 30\r\nTODO: later\r\n- Reply to 3 leads | 20 | 16:30\r\n"
        val day = QuestParser.parse(text, today)
        assertEquals(questDay, day.date)
        assertEquals(listOf("Gym session", "Reply to 3 leads"), day.quests.map { it.title })
    }

    @Test
    fun trimsTitlesAndSkipsEmptyTitles() {
        val text = "QUESTS 2026-11-11\n-    Gym session    | 30\n- | 10\n-    \n*   | 5\n- !!! | 15"
        val day = QuestParser.parse(text, today)
        assertEquals(2, day.quests.size)
        assertEquals("Gym session", day.quests[0].title)
        assertEquals("!!!", day.quests[1].title)
        assertEquals("q-2026-11-11-quest", day.quests[1].id)
        assertEquals(15, day.quests[1].xp)
    }

    @Test
    fun xpDefaultsToTenWhenMissingOrInvalid() {
        val text = """
            QUESTS 2026-11-11
            - Just a title
            - Bad xp | abc
            - Negative | -5
            - Decimal | 30.5
            - Zero | 0
        """.trimIndent()
        val day = QuestParser.parse(text, today)
        assertEquals(listOf(10, 10, 10, 10, 0), day.quests.map { it.xp })
        assertTrue(day.quests.all { it.due == null })
    }

    @Test
    fun timeOnlySecondFieldIsDueWithDefaultXp() {
        val day = QuestParser.parse("QUESTS 2026-11-11\n- Gym session | 10:00", today)
        assertEquals(1, day.quests.size)
        assertEquals(10, day.quests[0].xp)
        assertEquals(LocalTime.of(10, 0), day.quests[0].due)
    }

    @Test
    fun invalidDueIsNullAndSingleDigitHourIsAccepted() {
        val text = """
            QUESTS 2026-11-11
            - Too late | 30 | 24:00
            - Bad minute | 30 | 10:60
            - Garbage due | 30 | abc
            - Early | 30 | 9:05
        """.trimIndent()
        val day = QuestParser.parse(text, today)
        assertNull(day.quests[0].due)
        assertEquals(30, day.quests[0].xp)
        assertNull(day.quests[1].due)
        assertNull(day.quests[2].due)
        assertEquals(LocalTime.of(9, 5), day.quests[3].due)
        assertEquals(30, day.quests[3].xp)
    }

    @Test
    fun capsAtTwentyQuests() {
        val lines = (1..25).joinToString("\n") { "- Quest $it | 10" }
        val day = QuestParser.parse(lines, today)
        assertEquals(20, day.quests.size)
        assertEquals("Quest 1", day.quests.first().title)
        assertEquals("Quest 20", day.quests.last().title)
    }

    @Test
    fun idsAreStableForTheSameDateAndTitle() {
        val a = QuestParser.parse(sample, today)
        val b = QuestParser.parse(sample, LocalDate.of(2026, 1, 1))
        assertEquals(a.quests.map { it.id }, b.quests.map { it.id })
        assertEquals(a, b)
    }

    @Test
    fun duplicateTitlesGetDistinctStableIds() {
        val text = "QUESTS 2026-11-11\n- Gym session | 30\n- Gym session | 40"
        val a = QuestParser.parse(text, today)
        val b = QuestParser.parse(text, today)
        assertEquals("q-2026-11-11-gym-session", a.quests[0].id)
        assertEquals("q-2026-11-11-gym-session-2", a.quests[1].id)
        assertEquals(30, a.quests[0].xp)
        assertEquals(40, a.quests[1].xp)
        assertEquals(a.quests.map { it.id }, b.quests.map { it.id })
    }

    @Test
    fun sameTitleOnDifferentDatesGetsDifferentIds() {
        val eleven = QuestParser.parse("QUESTS 2026-11-11\n- Gym session | 30", today)
        val twelve = QuestParser.parse("QUESTS 2026-11-12\n- Gym session | 30", today)
        assertEquals("q-2026-11-11-gym-session", eleven.quests[0].id)
        assertEquals("q-2026-11-12-gym-session", twelve.quests[0].id)
        assertNotEquals(eleven.quests[0].id, twelve.quests[0].id)
    }

    @Test
    fun emptyInputIsEmptyDayForToday() {
        val day = QuestParser.parse("", today)
        assertEquals(today, day.date)
        assertTrue(day.quests.isEmpty())
    }

    @Test
    fun invalidHeaderDateFallsBackToToday() {
        val day = QuestParser.parse("QUESTS 2026-13-99\n- Gym session | 30", today)
        assertEquals(today, day.date)
        assertEquals("q-2026-11-10-gym-session", day.quests[0].id)
    }
}
