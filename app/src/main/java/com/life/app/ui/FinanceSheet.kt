package com.life.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.app.data.AppData
import com.life.app.data.FinanceRanker
import com.life.app.data.LifeEvent
import com.life.app.data.Money
import com.life.app.data.MoneyDirection
import com.life.app.data.balanceMinor
import com.life.app.ui.theme.ExpenseRed
import com.life.app.ui.theme.IncomeGreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FinanceSheet(
    data: AppData,
    editing: LifeEvent? = null,
    onSave: (direction: MoneyDirection, amountMinor: Long, payee: String, category: String, memo: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var direction by remember {
        mutableStateOf(editing?.direction ?: MoneyDirection.OUT)
    }
    var amountText by remember {
        mutableStateOf(
            editing?.amountMinor?.let { Money.formatMajorPlain(it, data.currencyCode) }.orEmpty()
        )
    }
    var payee by remember { mutableStateOf(editing?.payee.orEmpty()) }
    var category by remember { mutableStateOf(editing?.category.orEmpty()) }
    var memo by remember { mutableStateOf(editing?.body.orEmpty()) }
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { amountFocus.requestFocus() }
    }

    val suggestion = remember(data, direction, payee) {
        FinanceRanker.suggest(data, direction, payee, selectedPayee = payee)
    }

    LaunchedEffect(suggestion.suggestedCategory, category) {
        if (category.isBlank() && !suggestion.suggestedCategory.isNullOrBlank()) {
            category = suggestion.suggestedCategory
        }
    }

    val amountMinor = Money.parseToMinor(amountText, data.currencyCode)
    val current = data.balanceMinor()
    val after = if (editing == null && amountMinor != null && amountMinor > 0) {
        current + if (direction == MoneyDirection.IN) amountMinor else -amountMinor
    } else {
        current
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (editing == null) "Finance" else "Edit transaction",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Balance ${Money.format(current, data.currencyCode)}" +
                    if (editing == null && amountMinor != null && amountMinor > 0)
                        "  →  ${Money.format(after, data.currencyCode)}"
                    else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DirButton(
                    label = "+ Income",
                    selected = direction == MoneyDirection.IN,
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f)
                ) { direction = MoneyDirection.IN }
                DirButton(
                    label = "− Expense",
                    selected = direction == MoneyDirection.OUT,
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f)
                ) { direction = MoneyDirection.OUT }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus),
                label = { Text("Amount") },
                placeholder = { Text("0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            if (suggestion.suggestedAmountMinor != null && amountText.isBlank()) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    amountText = Money.formatMajorPlain(suggestion.suggestedAmountMinor, data.currencyCode)
                }) {
                    Text("Use ${Money.format(suggestion.suggestedAmountMinor, data.currencyCode)}")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = payee,
                onValueChange = { payee = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Payee") },
                placeholder = { Text("Coffee, rent, salary…") },
                singleLine = true
            )
            if (suggestion.payees.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestion.payees.take(8).forEach { p ->
                        TagChip(p.name, payee.equals(p.name, ignoreCase = true)) {
                            payee = p.name
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Category", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                data.categories.forEach { c ->
                    TagChip(c, category.equals(c, ignoreCase = true)) { category = c }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Memo (optional)") },
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val amt = amountMinor ?: return@Button
                    onSave(direction, amt, payee, category, memo)
                },
                enabled = amountMinor != null && amountMinor > 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (editing == null) "Save" else "Update")
            }
        }
    }
}

@Composable
private fun DirButton(
    label: String,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) color else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
            .fillMaxWidth(),
        color = fg,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        textAlign = TextAlign.Center
    )
}
