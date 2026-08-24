package com.life.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class FunnelSlice(
    val income: Long,
    val savings: Long,
    val recurring: Long,
    val planned: Long,
    val leftover: Long
) {
    val hasBudget: Boolean
        get() = income != 0L || savings != 0L || recurring != 0L || planned != 0L
}

object FinanceFunnel {

    fun month(
        data: AppData,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): FunnelSlice {
        val ym = YearMonth.from(today)
        val start = ym.atDay(1)
        val end = ym.atEndOfMonth()
        var income = 0L
        for (e in data.events) {
            if (e.kind != EventKind.FINANCE) continue
            if (e.direction != MoneyDirection.IN) continue
            val day = Instant.ofEpochMilli(e.createdAt).atZone(zone).toLocalDate()
            if (day.isBefore(start) || day.isAfter(end)) continue
            income += e.amountMinor ?: 0L
        }
        for (r in data.recurring) {
            if (!r.isActive(today)) continue
            if (r.direction != MoneyDirection.IN) continue
            income += r.monthlyAmountMinor()
        }
        var recurringOut = 0L
        for (r in data.recurring) {
            if (!r.isActive(today)) continue
            if (r.direction != MoneyDirection.OUT) continue
            recurringOut += r.monthlyAmountMinor()
        }
        val planned = data.planned.sumOf { it.amountMinor }
        val savings = data.monthlySavingsMinor.coerceAtLeast(0L)
        val leftover = income - savings - recurringOut - planned
        return FunnelSlice(
            income = income,
            savings = savings,
            recurring = recurringOut,
            planned = planned,
            leftover = leftover
        )
    }
}
