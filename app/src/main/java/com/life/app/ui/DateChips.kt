package com.life.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.life.app.data.WantDomain
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateChipRow(
    dueDate: String?,
    today: LocalDate = LocalDate.now(),
    onDueDate: (String?) -> Unit
) {
    val context = LocalContext.current
    val tomorrow = today.plusDays(1)
    val due = WantDomain.parseDue(dueDate)
    val custom = due != null && due != today && due != tomorrow

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TagChip("Today", due == today) {
            onDueDate(if (due == today) null else today.toString())
        }
        TagChip("Tomorrow", due == tomorrow) {
            onDueDate(if (due == tomorrow) null else tomorrow.toString())
        }
        if (custom) {
            TagChip(WantDomain.historyLabel(due.toString(), today), true) {
                onDueDate(null)
            }
        }
        TagChip("Pick…", false) {
            val seed = due ?: today
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    onDueDate(LocalDate.of(y, m + 1, d).toString())
                },
                seed.year,
                seed.monthValue - 1,
                seed.dayOfMonth
            ).show()
        }
    }
}
