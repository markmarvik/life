package com.life.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.app.data.AppData
import com.life.app.data.FinanceFunnel
import com.life.app.data.Money
import com.life.app.data.MoneyDirection
import com.life.app.data.PlannedItem
import com.life.app.data.RecurringCadence
import com.life.app.data.RecurringDef
import com.life.app.data.balanceMinor
import com.life.app.ui.theme.ExpenseRed
import com.life.app.ui.theme.IncomeGreen
import java.time.LocalDate

sealed interface FunnelEdit {
    data object Overview : FunnelEdit
    data class Recurring(val def: RecurringDef) : FunnelEdit
    data class Planned(val item: PlannedItem) : FunnelEdit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunnelSheet(
    data: AppData,
    today: LocalDate = LocalDate.now(),
    onSaveRecurring: (RecurringDef) -> Unit,
    onDeleteRecurring: (String) -> Unit,
    onSavePlanned: (PlannedItem) -> Unit,
    onDeletePlanned: (String) -> Unit,
    onSaveSavings: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var edit by remember { mutableStateOf<FunnelEdit>(FunnelEdit.Overview) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        when (val e = edit) {
            FunnelEdit.Overview -> FunnelOverview(
                data = data,
                today = today,
                onOpenRecurring = { edit = FunnelEdit.Recurring(it) },
                onOpenPlanned = { edit = FunnelEdit.Planned(it) },
                onSaveSavings = onSaveSavings
            )
            is FunnelEdit.Recurring -> RecurringEdit(
                def = e.def,
                currency = data.currencyCode,
                onSave = {
                    onSaveRecurring(it)
                    edit = FunnelEdit.Overview
                },
                onDelete = {
                    onDeleteRecurring(e.def.id)
                    edit = FunnelEdit.Overview
                },
                onBack = { edit = FunnelEdit.Overview }
            )
            is FunnelEdit.Planned -> PlannedEdit(
                item = e.item,
                currency = data.currencyCode,
                onSave = {
                    onSavePlanned(it)
                    edit = FunnelEdit.Overview
                },
                onDelete = {
                    onDeletePlanned(e.item.id)
                    edit = FunnelEdit.Overview
                },
                onBack = { edit = FunnelEdit.Overview }
            )
        }
    }
}

@Composable
private fun FunnelOverview(
    data: AppData,
    today: LocalDate,
    onOpenRecurring: (RecurringDef) -> Unit,
    onOpenPlanned: (PlannedItem) -> Unit,
    onSaveSavings: (Long) -> Unit
) {
    val slice = FinanceFunnel.month(data, today)
    var savingsText by remember(data.monthlySavingsMinor, data.currencyCode) {
        mutableStateOf(Money.formatMajorPlain(data.monthlySavingsMinor, data.currencyCode))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Text("This month", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cash ${Money.format(data.balanceMinor(), data.currencyCode)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        FunnelRow("Income", slice.income, data.currencyCode, IncomeGreen)
        FunnelRow("Savings", slice.savings, data.currencyCode, null)
        FunnelRow("Recurring", slice.recurring, data.currencyCode, ExpenseRed)
        FunnelRow("Planned", slice.planned, data.currencyCode, null)
        FunnelRow(
            "Leftover",
            slice.leftover,
            data.currencyCode,
            if (slice.leftover < 0) ExpenseRed else IncomeGreen
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = savingsText,
            onValueChange = { savingsText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Monthly savings") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val minor = Money.parseToMinor(savingsText, data.currencyCode) ?: 0L
                onSaveSavings(minor)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save savings") }

        Spacer(Modifier.height(20.dp))
        Text("Recurring", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        if (data.recurring.isEmpty()) {
            Text(
                "Type “rata 12k do 2027” in the composer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            data.recurring.forEach { def ->
                ListRow(
                    title = def.name,
                    subtitle = "${Money.format(def.amountMinor, data.currencyCode)} · ${def.cadence.name.lowercase()}" +
                        (def.until?.let { " · until $it" } ?: ""),
                    onClick = { onOpenRecurring(def) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Planned", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        if (data.planned.isEmpty()) {
            Text(
                "Type “hoću laptop 80k” in the composer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            data.planned.forEach { item ->
                ListRow(
                    title = item.name,
                    subtitle = Money.format(item.amountMinor, data.currencyCode),
                    onClick = { onOpenPlanned(item) }
                )
            }
        }
    }
}

@Composable
private fun FunnelRow(
    label: String,
    minor: Long,
    currency: String,
    color: androidx.compose.ui.graphics.Color?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            Money.format(minor, currency),
            fontWeight = FontWeight.Medium,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ListRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RecurringEdit(
    def: RecurringDef,
    currency: String,
    onSave: (RecurringDef) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(def.name) }
    var amountText by remember {
        mutableStateOf(Money.formatMajorPlain(def.amountMinor, currency))
    }
    var cadence by remember { mutableStateOf(def.cadence) }
    var until by remember { mutableStateOf(def.until.orEmpty()) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Text("Edit recurring", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            amountText,
            { amountText = it },
            Modifier.fillMaxWidth(),
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurringCadence.entries.forEach { c ->
                TagChip(c.name.lowercase().replaceFirstChar { it.titlecase() }, cadence == c) { cadence = c }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            until,
            { until = it },
            Modifier.fillMaxWidth(),
            label = { Text("Until (yyyy-MM-dd)") },
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val amt = Money.parseToMinor(amountText, currency) ?: return@Button
                onSave(
                    def.copy(
                        name = name.trim().ifBlank { def.name },
                        amountMinor = amt,
                        cadence = cadence,
                        until = until.trim().ifBlank { null },
                        direction = MoneyDirection.OUT
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PlannedEdit(
    item: PlannedItem,
    currency: String,
    onSave: (PlannedItem) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var amountText by remember {
        mutableStateOf(Money.formatMajorPlain(item.amountMinor, currency))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Text("Edit planned", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            amountText,
            { amountText = it },
            Modifier.fillMaxWidth(),
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val amt = Money.parseToMinor(amountText, currency) ?: return@Button
                onSave(item.copy(name = name.trim().ifBlank { item.name }, amountMinor = amt))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
