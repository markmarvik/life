package com.life.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.app.data.HabitCatalog
import com.life.app.data.HabitDef
import com.life.app.data.HabitSuggestion
import com.life.app.data.LifeEvent
import com.life.app.data.habitById
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitPickerSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<HabitSuggestion>,
    onPick: (HabitSuggestion) -> Unit,
    onEdit: (HabitSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Log a habit", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text(
                "Hold a habit to edit it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                placeholder = { Text("Search or create…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val first = suggestions.firstOrNull()
                        if (first != null) onPick(first)
                    }
                )
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(suggestions, key = { "${it.isCreate}:${it.habitId}:${it.name}" }) { s ->
                    HabitSuggestionRow(
                        s = s,
                        onClick = { onPick(s) },
                        onLongClick = if (s.canEdit) {
                            { onEdit(s) }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitSuggestionRow(
    s: HabitSuggestion,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    HabitLine(
        icon = s.icon,
        title = if (s.isCreate) "Create “${s.name}”" else s.name,
        why = s.why,
        titleWeight = if (s.isCreate) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEventSheet(
    event: LifeEvent,
    habitName: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
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
            Text(habitName, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            val whenLogged = java.time.Instant.ofEpochMilli(event.createdAt)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))
            Text(
                whenLogged,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

fun habitNameOf(event: LifeEvent, data: com.life.app.data.AppData): String =
    event.habitId?.let { data.habitById(it)?.name } ?: "Habit"

@Composable
private fun HabitLine(
    icon: String,
    title: String,
    why: String,
    titleWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(icon.ifBlank { "•" }, fontSize = 20.sp, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = titleWeight, fontSize = 16.sp)
            if (why.isNotBlank()) {
                Text(why, color = muted, fontSize = 12.sp)
            }
        }
    }
}

private val HABIT_ICONS: List<String> = (
    listOf("•") + HabitCatalog.ALL.map { it.icon }
    ).distinct()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditSheet(
    habit: HabitDef,
    logCount: Int,
    reassignEvent: Boolean,
    otherNames: List<String> = emptyList(),
    onSave: (name: String, icon: String, why: String) -> Unit,
    onSaveAsNew: (name: String, icon: String, why: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(habit.name) }
    var icon by remember { mutableStateOf(habit.icon.ifBlank { "•" }) }
    var why by remember { mutableStateOf(habit.why) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }

    val trimmed = name.trim()
    val trimmedWhy = why.trim()
    val canSave = trimmed.isNotEmpty()
    val sameName = trimmed.equals(habit.name, ignoreCase = true)
    val nameTaken = otherNames.any { it.equals(trimmed, ignoreCase = true) }
    val canSaveAsNew = canSave && !sameName && !nameTaken
    val icons = remember(habit.icon) {
        val current = habit.icon.ifBlank { "•" }
        if (current in HABIT_ICONS) HABIT_ICONS else listOf(current) + HABIT_ICONS
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
            Text("Edit habit", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            HabitLine(
                icon = icon,
                title = trimmed.ifBlank { habit.name },
                why = trimmedWhy,
                titleWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = why,
                onValueChange = { why = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Gray text") },
                placeholder = { Text("Shown under the name") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Text("Icon", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icons.forEach { choice ->
                    val selected = icon == choice
                    Text(
                        choice,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { icon = choice }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSave(trimmed, icon, trimmedWhy) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save changes")
            }
            Text(
                when {
                    nameTaken -> "Merges all logs into the existing “$trimmed” habit."
                    logCount <= 0 -> "No logs yet. This just updates the habit."
                    logCount == 1 -> "Updates this habit on the one timeline log."
                    else -> "Updates this habit on all $logCount timeline logs."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            OutlinedButton(
                onClick = { onSaveAsNew(trimmed, icon, trimmedWhy) },
                enabled = canSaveAsNew,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save as new")
            }
            Text(
                if (reassignEvent) {
                    "This log becomes “$trimmed”. Other logs stay on “${habit.name}”."
                } else {
                    "Creates a separate habit. Existing logs stay on “${habit.name}”."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
