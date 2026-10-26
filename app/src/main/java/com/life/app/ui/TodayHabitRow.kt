package com.life.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.life.app.data.HabitStreak
import com.life.app.data.TodayHabit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayHabitRow(
    habits: List<TodayHabit>,
    onToggle: (TodayHabit) -> Unit,
    onEdit: (TodayHabit) -> Unit,
    modifier: Modifier = Modifier
) {
    if (habits.isEmpty()) return
    Column(modifier) {
        Text(
            "Today",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(habits, key = { it.id }) { habit ->
                TodayHabitCard(
                    habit = habit,
                    onToggle = { onToggle(habit) },
                    onEdit = { onEdit(habit) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayHabitCard(
    habit: TodayHabit,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val streak = habit.streak
    val done = streak.loggedToday
    val container = if (done) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = if (done) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            habit.icon.ifBlank { "•" },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            habit.name,
            style = MaterialTheme.typography.titleSmall,
            color = onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            streakLabel(streak),
            style = MaterialTheme.typography.labelMedium,
            color = onContainer.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            streak.recent.forEach { filled ->
                Spacer(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else onContainer.copy(alpha = 0.22f)
                        )
                )
            }
        }
    }
}

private fun streakLabel(streak: HabitStreak): String = when {
    streak.current > 0 && streak.best > streak.current -> "${streak.current} · best ${streak.best}"
    streak.current > 0 -> "${streak.current} day"
    streak.best > 0 -> "best ${streak.best}"
    else -> "Tap"
}
