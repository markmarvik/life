package com.life.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FinanceRankerTest {

    private fun tx(
        payee: String,
        amount: Long,
        category: String,
        at: Long,
        direction: MoneyDirection = MoneyDirection.OUT
    ) = LifeEvent(
        id = "$payee-$at",
        kind = EventKind.FINANCE,
        createdAt = at,
        amountMinor = amount,
        direction = direction,
        payee = payee,
        category = category
    )

    @Test
    fun coffeeRepeatsAsTopPayeeWithLastAmountAndCategory() {
        val now = 1_000_000L
        val data = AppData(
            events = listOf(
                tx("Coffee", 450, "Food", now - 3_600_000),
                tx("Coffee", 450, "Food", now - 86_400_000),
                tx("Rent", 50_000, "Rent", now - 200_000)
            ),
            payees = listOf("Coffee", "Rent")
        )
        val s = FinanceRanker.suggest(data, MoneyDirection.OUT, "", now, selectedPayee = "Coffee")
        assertEquals("Coffee", s.payees.first().name)
        assertEquals("Food", s.suggestedCategory)
        assertEquals(450L, s.suggestedAmountMinor)
    }

    @Test
    fun queryFuzzyFiltersPayees() {
        val now = 1_000L
        val data = AppData(
            events = listOf(
                tx("Coffee", 450, "Food", now),
                tx("Groceries", 2000, "Groceries", now)
            )
        )
        val s = FinanceRanker.suggest(data, MoneyDirection.OUT, "cof", now)
        assertTrue(s.payees.any { it.name == "Coffee" })
        assertTrue(s.payees.none { it.name == "Groceries" })
    }

    @Test
    fun incomeDoesNotMixWithExpensePayees() {
        val now = 1_000L
        val data = AppData(
            events = listOf(
                tx("Coffee", 450, "Food", now, MoneyDirection.OUT),
                tx("Salary", 100_000, "Salary", now, MoneyDirection.IN)
            )
        )
        val s = FinanceRanker.suggest(data, MoneyDirection.IN, "", now)
        assertTrue(s.payees.any { it.name == "Salary" })
        assertTrue(s.payees.none { it.name == "Coffee" })
    }
}
