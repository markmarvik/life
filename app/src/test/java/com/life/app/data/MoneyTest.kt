package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale

class MoneyTest {

    @Test
    fun usdFourFiftyToMinor() {
        assertEquals(450L, Money.parseToMinor("4.50", "USD"))
        assertEquals(450L, Money.parseToMinor("4,50", "USD"))
        assertEquals(1200L, Money.parseToMinor("12", "USD"))
    }

    @Test
    fun jpyHasNoFraction() {
        assertEquals(1000L, Money.parseToMinor("1000", "JPY"))
        assertEquals("1000", Money.formatMajorPlain(1000L, "JPY"))
    }

    @Test
    fun rejectsNegativeAndGarbage() {
        assertNull(Money.parseToMinor("", "USD"))
        assertNull(Money.parseToMinor("-3", "USD"))
        assertNull(Money.parseToMinor("abc", "USD"))
    }

    @Test
    fun balanceIsOpeningPlusInMinusOut() {
        val data = AppData(
            openingBalanceMinor = 10_000L,
            events = listOf(
                LifeEvent("1", EventKind.FINANCE, 1L, amountMinor = 500L, direction = MoneyDirection.IN),
                LifeEvent("2", EventKind.FINANCE, 2L, amountMinor = 250L, direction = MoneyDirection.OUT),
                LifeEvent("3", EventKind.NOTE, 3L, body = "ignore me")
            )
        )
        assertEquals(10_250L, data.balanceMinor())
    }

    @Test
    fun defaultCurrencyDoesNotCrash() {
        val code = Money.defaultCurrencyCode(Locale.US)
        assertEquals("USD", code)
    }

    @Test
    fun roundTripFormatPlain() {
        val minor = Money.parseToMinor("4.50", "USD")!!
        assertEquals("4.50", Money.formatMajorPlain(minor, "USD"))
    }
}
