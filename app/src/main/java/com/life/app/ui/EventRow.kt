package com.life.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.LifeEvent
import com.life.app.data.Money
import com.life.app.data.MoneyDirection
import com.life.app.data.WantDomain
import com.life.app.data.WantStatus
import com.life.app.data.habitById
import com.life.app.ui.theme.ExpenseRed
import com.life.app.ui.theme.IncomeGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventRow(
    event: LifeEvent,
    data: AppData,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val time = Instant.ofEpochMilli(event.timelineAt())
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(timeFmt)
    val today = LocalDate.now()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        EventGlyph(event, data)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            when (event.kind) {
                EventKind.HABIT -> {
                    val habit = event.habitId?.let { data.habitById(it) }
                    Text(
                        habit?.name ?: "Habit",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
                EventKind.NOTE -> {
                    Text(
                        event.body,
                        fontSize = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    val chips = buildList {
                        if (event.isWant) add(
                            when (event.wantStatus) {
                                WantStatus.DONE -> "Want · done"
                                WantStatus.CANCELED -> "Want · didn’t"
                                WantStatus.MISSED -> "Want · missed"
                                else -> "Want"
                            }
                        )
                        event.dueDate?.let { add(WantDomain.historyLabel(it, today)) }
                        addAll(event.tags)
                    }.filter { it.isNotBlank() }
                    if (chips.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        TagStrip(chips)
                    }
                    if (event.reflection.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            event.reflection,
                            color = muted,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                EventKind.FINANCE -> {
                    val dir = event.direction ?: MoneyDirection.OUT
                    val color = if (dir == MoneyDirection.IN) IncomeGreen else ExpenseRed
                    Text(
                        Money.formatSigned(event.amountMinor ?: 0L, dir, data.currencyCode),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = color
                    )
                    val subtitle = listOf(event.payee, event.category)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, color = muted, fontSize = 13.sp)
                    }
                    if (event.body.isNotBlank()) {
                        Text(
                            event.body,
                            color = muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(time, color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun EventGlyph(event: LifeEvent, data: AppData) {
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        when (event.kind) {
            EventKind.HABIT -> {
                val icon = event.habitId?.let { data.habitById(it)?.icon }.orEmpty()
                Text(if (icon.isNotBlank()) icon else "•", fontSize = 18.sp)
            }
            EventKind.NOTE -> Text(
                when {
                    event.wantStatus == WantStatus.DONE -> "✓"
                    event.wantStatus == WantStatus.CANCELED || event.wantStatus == WantStatus.MISSED -> "✕"
                    else -> "✎"
                },
                fontSize = 16.sp
            )
            EventKind.FINANCE -> {
                val dir = event.direction ?: MoneyDirection.OUT
                Text(
                    if (dir == MoneyDirection.IN) "+" else "−",
                    color = if (dir == MoneyDirection.IN) IncomeGreen else ExpenseRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagStrip(tags: List<String>, muted: Boolean = true) {
    val color = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { tag ->
            Text(
                tag,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 11.sp,
                color = color
            )
        }
    }
}
