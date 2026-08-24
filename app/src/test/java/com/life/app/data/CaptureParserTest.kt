package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CaptureParserTest {

    private val today = LocalDate.of(2026, 8, 24)
    private val now = 1_724_457_600_000L
    private val data = AppData(currencyCode = "RSD")

    private fun parse(text: String, app: AppData = data) =
        CaptureParser.parse(text, app, nowMs = now, today = today)

    @Test
    fun treningAndLidlExpense() {
        val drafts = parse("trening; platio lidl 2300")
        assertEquals(2, drafts.size)
        assertEquals(ParsedKind.HABIT, drafts[0].kind)
        assertEquals("Strength training", drafts[0].habitName)
        assertEquals(CaptureConfidence.HIGH, drafts[0].confidence)
        assertEquals(ParsedKind.FINANCE, drafts[1].kind)
        assertEquals(MoneyDirection.OUT, drafts[1].direction)
        assertEquals("lidl", drafts[1].payee.lowercase())
        assertEquals(Money.toMinor(2300.0, "RSD"), drafts[1].amountMinor)
        assertEquals(CaptureConfidence.HIGH, drafts[1].confidence)
    }

    @Test
    fun uradioZubeLogsBrush() {
        val drafts = parse("uradio zube")
        assertEquals(1, drafts.size)
        assertEquals(ParsedKind.HABIT, drafts[0].kind)
        assertEquals("Brush + floss", drafts[0].habitName)
        assertEquals(CaptureConfidence.HIGH, drafts[0].confidence)
    }

    @Test
    fun uradioCompletesMatchingWant() {
        val want = LifeEvent(
            id = "w1",
            kind = EventKind.NOTE,
            createdAt = 1L,
            body = "zube",
            isWant = true,
            wantStatus = WantStatus.OPEN
        )
        val drafts = parse("uradio zube", data.copy(events = listOf(want)))
        assertEquals(ParsedKind.WANT_DONE, drafts.single().kind)
        assertEquals("w1", drafts.single().wantEventId)
    }

    @Test
    fun plataIsIncome() {
        val drafts = parse("plata 80000")
        assertEquals(ParsedKind.FINANCE, drafts.single().kind)
        assertEquals(MoneyDirection.IN, drafts.single().direction)
        assertEquals(Money.toMinor(80_000.0, "RSD"), drafts.single().amountMinor)
        assertEquals(CaptureConfidence.HIGH, drafts.single().confidence)
    }

    @Test
    fun rataUntilYear() {
        val drafts = parse("rata 12k do 2027")
        val d = drafts.single()
        assertEquals(ParsedKind.RECURRING, d.kind)
        assertEquals(RecurringCadence.MONTHLY, d.cadence)
        assertEquals("2027-12-31", d.until)
        assertEquals(Money.toMinor(12_000.0, "RSD"), d.amountMinor)
        assertEquals(CaptureConfidence.HIGH, d.confidence)
    }

    @Test
    fun hocuLaptopIsPlanned() {
        val drafts = parse("hoću laptop 80k")
        val d = drafts.single()
        assertEquals(ParsedKind.PLANNED, d.kind)
        assertEquals("laptop", d.plannedName.lowercase())
        assertEquals(Money.toMinor(80_000.0, "RSD"), d.amountMinor)
    }

    @Test
    fun sutraBecomesWantTomorrow() {
        val drafts = parse("sutra zubar")
        val d = drafts.single()
        assertEquals(ParsedKind.NOTE, d.kind)
        assertTrue(d.isWant)
        assertEquals(today.plusDays(1).toString(), d.dueDate)
        assertEquals(CaptureConfidence.HIGH, d.confidence)
    }

    @Test
    fun unknownTextIsLowNote() {
        val drafts = parse("random thought about clouds")
        val d = drafts.single()
        assertEquals(ParsedKind.NOTE, d.kind)
        assertEquals(CaptureConfidence.LOW, d.confidence)
        assertEquals("random thought about clouds", d.noteBody)
    }

    @Test
    fun applyWritesHabitAndExpense() {
        val drafts = parse("trening; platio lidl 2300")
        val next = CaptureApply.applyAll(data, drafts, nowMs = now, today = today)
        assertEquals(1, next.habits.count { it.name == "Strength training" })
        assertEquals(1, next.events.count { it.kind == EventKind.HABIT })
        val money = next.events.first { it.kind == EventKind.FINANCE }
        assertEquals(MoneyDirection.OUT, money.direction)
        assertEquals(Money.toMinor(2300.0, "RSD"), money.amountMinor)
    }

    @Test
    fun twelveKParsesAsThousands() {
        assertEquals(Money.toMinor(12_000.0, "RSD"), CaptureParser.extractAmount("12k", "RSD"))
        assertEquals(Money.toMinor(2_300.0, "RSD"), CaptureParser.extractAmount("2.300", "RSD"))
    }
}
