package com.life.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.app.data.AppData
import com.life.app.data.FinanceFunnel
import com.life.app.data.FunnelSlice
import com.life.app.data.Money
import com.life.app.data.balanceMinor
import com.life.app.ui.theme.ExpenseRed
import com.life.app.ui.theme.IncomeGreen
import java.time.LocalDate

@Composable
fun FunnelHeader(
    data: AppData,
    today: LocalDate = LocalDate.now(),
    onClick: () -> Unit
) {
    val slice = FinanceFunnel.month(data, today)
    if (!slice.hasBudget) {
        BalancePill(Money.format(data.balanceMinor(), data.currencyCode), onClick)
        return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FunnelCell("In", slice.income, data.currencyCode, IncomeGreen)
        Arrow()
        FunnelCell("Save", slice.savings, data.currencyCode, null)
        Arrow()
        FunnelCell("Rata", slice.recurring, data.currencyCode, ExpenseRed)
        Arrow()
        FunnelCell("Plan", slice.planned, data.currencyCode, null)
        Arrow()
        FunnelCell(
            "Left",
            slice.leftover,
            data.currencyCode,
            if (slice.leftover < 0) ExpenseRed else IncomeGreen
        )
    }
}

@Composable
private fun FunnelCell(
    caption: String,
    minor: Long,
    currency: String,
    color: androidx.compose.ui.graphics.Color?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            compact(minor, currency),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            caption,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Arrow() {
    Text(
        "→",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp
    )
}

internal fun compact(minor: Long, currency: String): String {
    val major = Money.toMajor(kotlin.math.abs(minor), currency)
    val sign = if (minor < 0) "−" else ""
    return if (major >= 1000) {
        val k = major / 1000.0
        val body = if (k >= 10) k.toInt().toString() else {
            val t = (kotlin.math.round(k * 10) / 10.0)
            if (t == t.toInt().toDouble()) t.toInt().toString() else t.toString()
        }
        "$sign${body}k"
    } else {
        "$sign${major.toInt()}"
    }
}

fun FunnelSlice.caption(currency: String): String =
    "${compact(income, currency)} → ${compact(leftover, currency)}"

@Composable
fun BalancePill(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
}
