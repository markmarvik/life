package com.life.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.app.data.AppData
import com.life.app.data.DataLoadDiagnostics
import com.life.app.data.EventKind
import com.life.app.data.HabitDef
import com.life.app.data.HabitDomain
import com.life.app.data.HabitRanker
import com.life.app.data.LifeEvent
import com.life.app.data.Money
import com.life.app.data.availableTags
import com.life.app.data.balanceMinor
import com.life.app.data.WantDomain
import com.life.app.data.groupEvents
import com.life.app.util.rememberHaptics
import java.time.LocalDate

enum class TimelineFilter { ALL, HABIT, NOTE, FINANCE }

sealed interface CaptureUi {
    data object Hidden : CaptureUi
    data object PickType : CaptureUi
    data object Habit : CaptureUi
    data class Note(val editing: LifeEvent? = null) : CaptureUi
    data class Finance(val editing: LifeEvent? = null) : CaptureUi
    data class HabitEvent(val event: LifeEvent) : CaptureUi
    data class EditHabit(
        val habit: HabitDef,
        val sourceEventId: String? = null,
        val returnToPicker: Boolean = false
    ) : CaptureUi
    data class Reflect(val event: LifeEvent) : CaptureUi
}

@Composable
fun TimelineScreen(
    data: AppData,
    vm: LifeViewModel,
    onOpenSettings: () -> Unit
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    var filter by remember { mutableStateOf(TimelineFilter.ALL) }
    var capture by remember { mutableStateOf<CaptureUi>(CaptureUi.Hidden) }
    var habitQuery by remember { mutableStateOf("") }
    var showCorrupt by remember { mutableStateOf(DataLoadDiagnostics.shouldShowBanner()) }
    val today = remember { LocalDate.now() }
    val upcoming = remember(data.events) { WantDomain.upcoming(data, today) }
    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val filtered = remember(data.events, filter) {
        when (filter) {
            TimelineFilter.ALL -> data.events
            TimelineFilter.HABIT -> data.events.filter { it.kind == EventKind.HABIT }
            TimelineFilter.NOTE -> data.events.filter { it.kind == EventKind.NOTE }
            TimelineFilter.FINANCE -> data.events.filter { it.kind == EventKind.FINANCE }
        }
    }
    val groups = remember(filtered) { groupEvents(filtered) }
    val suggestions = remember(data, habitQuery) {
        HabitRanker.suggest(data, System.currentTimeMillis(), habitQuery)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.tick()
                    capture = CaptureUi.PickType
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Life", fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f))
                BalancePill(Money.format(data.balanceMinor(), data.currencyCode)) {
                    haptics.tick()
                    capture = CaptureUi.Finance()
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
            UpcomingStrip(
                items = upcoming,
                today = today,
                onDone = {
                    haptics.success()
                    vm.completeWant(it.id)
                },
                onDidnt = {
                    haptics.tick()
                    capture = CaptureUi.Reflect(it)
                },
                onOpen = { capture = CaptureUi.Note(it) }
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimelineFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = {
                            Text(
                                when (f) {
                                    TimelineFilter.ALL -> "All"
                                    TimelineFilter.HABIT -> "Habit"
                                    TimelineFilter.NOTE -> "Note"
                                    TimelineFilter.FINANCE -> "Finance"
                                }
                            )
                        }
                    )
                }
            }
            if (showCorrupt) {
                Text(
                    "Could not read saved data. Showing empty until you restore a backup.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clickable {
                            DataLoadDiagnostics.dismiss()
                            showCorrupt = false
                        }
                )
            }
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (filter == TimelineFilter.ALL) "Nothing yet."
                            else "Nothing in this filter.",
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap + to log your day.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)
                ) {
                    groups.forEach { day ->
                        item(key = "h-${day.date}") {
                            Text(
                                day.label,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        items(day.events, key = { it.id }) { event ->
                            EventRow(
                                event = event,
                                data = data,
                                onClick = {
                                    capture = when (event.kind) {
                                        EventKind.HABIT -> CaptureUi.HabitEvent(event)
                                        EventKind.NOTE -> CaptureUi.Note(event)
                                        EventKind.FINANCE -> CaptureUi.Finance(event)
                                    }
                                },
                                onLongClick = if (event.kind == EventKind.HABIT) {
                                    {
                                        val habit = event.habitId?.let { HabitDomain.defForEdit(data, it) }
                                        if (habit != null) {
                                            haptics.tick()
                                            capture = CaptureUi.EditHabit(
                                                habit = habit,
                                                sourceEventId = event.id
                                            )
                                        }
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    when (val c = capture) {
        CaptureUi.Hidden -> Unit
        CaptureUi.PickType -> CaptureTypeSheet(
            onPick = {
                haptics.tick()
                capture = when (it) {
                    CaptureKind.HABIT -> {
                        habitQuery = ""
                        CaptureUi.Habit
                    }
                    CaptureKind.NOTE -> CaptureUi.Note()
                    CaptureKind.FINANCE -> CaptureUi.Finance()
                }
            },
            onDismiss = { capture = CaptureUi.Hidden }
        )
        CaptureUi.Habit -> HabitPickerSheet(
            query = habitQuery,
            onQueryChange = { habitQuery = it },
            suggestions = suggestions,
            onPick = {
                haptics.success()
                vm.logHabit(it)
                habitQuery = ""
                capture = CaptureUi.Hidden
            },
            onEdit = { suggestion ->
                val habit = suggestion.habitId?.let { HabitDomain.defForEdit(data, it) }
                    ?: return@HabitPickerSheet
                haptics.tick()
                capture = CaptureUi.EditHabit(
                    habit = habit,
                    returnToPicker = true
                )
            },
            onDismiss = { capture = CaptureUi.Hidden }
        )
        is CaptureUi.Note -> NoteSheet(
            availableTags = data.availableTags(),
            editing = c.editing,
            onSave = { body, tags, isWant, dueDate ->
                haptics.success()
                if (isWant) maybeAskNotifications(context, notifPerm)
                val existing = c.editing
                if (existing == null) vm.logNote(body, tags, isWant, dueDate)
                else vm.updateNote(existing, body, tags, isWant, dueDate)
                capture = CaptureUi.Hidden
            },
            onDelete = c.editing?.let { ev ->
                {
                    vm.deleteEvent(ev.id)
                    capture = CaptureUi.Hidden
                }
            },
            onDismiss = { capture = CaptureUi.Hidden }
        )
        is CaptureUi.Finance -> FinanceSheet(
            data = data,
            editing = c.editing,
            onSave = { dir, amt, payee, cat, memo ->
                haptics.success()
                val existing = c.editing
                if (existing == null) vm.logFinance(dir, amt, payee, cat, memo)
                else vm.updateEvent(
                    existing.copy(
                        direction = dir,
                        amountMinor = amt,
                        payee = payee.trim(),
                        category = cat.trim(),
                        body = memo.trim()
                    )
                )
                capture = CaptureUi.Hidden
            },
            onDelete = c.editing?.let { ev ->
                {
                    vm.deleteEvent(ev.id)
                    capture = CaptureUi.Hidden
                }
            },
            onDismiss = { capture = CaptureUi.Hidden }
        )
        is CaptureUi.HabitEvent -> HabitEventSheet(
            event = c.event,
            habitName = habitNameOf(c.event, data),
            onDelete = {
                vm.deleteEvent(c.event.id)
                capture = CaptureUi.Hidden
            },
            onDismiss = { capture = CaptureUi.Hidden }
        )
        is CaptureUi.EditHabit -> HabitEditSheet(
            habit = c.habit,
            logCount = HabitDomain.logCount(data, c.habit.id),
            reassignEvent = c.sourceEventId != null,
            otherNames = data.habits.filter { !it.archived && it.id != c.habit.id }.map { it.name },
            onSave = { name, icon, why ->
                haptics.success()
                vm.editHabit(c.habit.id, name, icon, why)
                capture = if (c.returnToPicker) CaptureUi.Habit else CaptureUi.Hidden
            },
            onSaveAsNew = { name, icon, why ->
                haptics.success()
                vm.saveHabitAsNew(name, icon, why, reassignEventId = c.sourceEventId)
                capture = if (c.returnToPicker) CaptureUi.Habit else CaptureUi.Hidden
            },
            onDismiss = {
                capture = if (c.returnToPicker) CaptureUi.Habit else CaptureUi.Hidden
            }
        )
        is CaptureUi.Reflect -> ReflectionSheet(
            event = c.event,
            streak = data.wantStrikeStreak,
            onSubmit = { text ->
                haptics.success()
                vm.failWant(c.event.id, text)
                capture = CaptureUi.Hidden
            },
            onDismiss = { capture = CaptureUi.Hidden }
        )
    }
}

private fun maybeAskNotifications(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT < 33) return
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
private fun BalancePill(text: String, onClick: () -> Unit) {
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
