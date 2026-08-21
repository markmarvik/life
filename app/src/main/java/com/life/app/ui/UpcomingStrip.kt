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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.app.data.LifeEvent
import com.life.app.data.WantDomain
import com.life.app.ui.theme.ExpenseRed
import java.time.LocalDate

@Composable
fun UpcomingStrip(
    items: List<LifeEvent>,
    today: LocalDate,
    onDone: (LifeEvent) -> Unit,
    onDidnt: (LifeEvent) -> Unit,
    onOpen: (LifeEvent) -> Unit
) {
    if (items.isEmpty()) return
    val shown = items.take(5)
    val extra = items.size - shown.size
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            "Upcoming",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        shown.forEach { event ->
            WantCard(
                event = event,
                today = today,
                onDone = { onDone(event) },
                onDidnt = { onDidnt(event) },
                onOpen = { onOpen(event) }
            )
            Spacer(Modifier.height(8.dp))
        }
        if (extra > 0) {
            Text(
                "and $extra more",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun WantCard(
    event: LifeEvent,
    today: LocalDate,
    onDone: () -> Unit,
    onDidnt: () -> Unit,
    onOpen: () -> Unit
) {
    val overdue = WantDomain.isOverdue(event, today)
    val label = WantDomain.upcomingLabel(event.dueDate, today)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                event.body,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = if (overdue) ExpenseRed else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDidnt) { Text("Didn’t") }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}
