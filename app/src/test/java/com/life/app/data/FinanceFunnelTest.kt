package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class FinanceFunnelTest {

    private val today = LocalDate.of(2026, 8, 24)
    private val zone = ZoneOffset.UTC

    private fun ms(day: Int) =
        LocalDateTime.of(2026, 8, day, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun waterfallIncomeMinusSavingsRecurringPlanned() {
        val data = AppData(
            currencyCode = "RSD",
            monthlySavingsMinor = Money.toMinor(10_000.0, "RSD"),
            events = listOf(
                LifeEvent(
                    "in",
                    EventKind.FINANCE,
                    ms(2),
                    amountMinor = Money.toMinor(80_000.0, "RSD"),
                    direction = MoneyDirection.IN
                )
            ),
            recurring = listOf(
                RecurringDef(
                    id = "r1",
                    name = "Rata",
                    amountMinor = Money.toMinor(12_000.0, "RSD"),
                    until = "2027-12-31"
                )
            ),
            planned = listOf(
                PlannedItem("p1", "laptop", Money.toMinor(80_000.0, "RSD"))
            )
        )
        val slice = FinanceFunnel.month(data, today, zone)
        assertEquals(Money.toMinor(80_000.0, "RSD"), slice.income)
        assertEquals(Money.toMinor(10_000.0, "RSD"), slice.savings)
        assertEquals(Money.toMinor(12_000.0, "RSD"), slice.recurring)
        assertEquals(Money.toMinor(80_000.0, "RSD"), slice.planned)
        assertEquals(Money.toMinor(80_000.0, "RSD") - Money.toMinor(10_000.0, "RSD") -
            Money.toMinor(12_000.0, "RSD") - Money.toMinor(80_000.0, "RSD"), slice.leftover)
        assertTrue(slice.hasBudget)
    }

    @Test
    fun expiredRecurringIsIgnored() {
        val data = AppData(
            recurring = listOf(
                RecurringDef("r1", "Old", 1_000L, until = "2025-12-31")
            )
        )
        val slice = FinanceFunnel.month(data, today, zone)
        assertEquals(0L, slice.recurring)
        assertFalse(slice.hasBudget)
    }

    @Test
    fun weeklyCountsAsFour() {
        val data = AppData(
            recurring = listOf(
                RecurringDef("r1", "Gym", 1_000L, cadence = RecurringCadence.WEEKLY)
            )
        )
        assertEquals(4_000L, FinanceFunnel.month(data, today, zone).recurring)
    }
}
