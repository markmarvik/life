package com.life.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.life.app.data.AndroidLifeRepository
import com.life.app.data.AppData
import com.life.app.data.CaptureApply
import com.life.app.data.CaptureDraft
import com.life.app.data.EventKind
import com.life.app.data.HabitDomain
import com.life.app.data.HabitSuggestion
import com.life.app.data.LifeEvent
import com.life.app.data.LifeRepository
import com.life.app.data.MoneyDirection
import com.life.app.data.PlannedItem
import com.life.app.data.RecurringDef
import com.life.app.data.WantDomain
import com.life.app.data.WantStatus
import com.life.app.data.withCategory
import com.life.app.data.withEvent
import com.life.app.data.withPayee
import com.life.app.data.withTag
import com.life.app.data.withoutEvent
import com.life.app.reminders.WantScheduler
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class LifeViewModel(
    private val repository: LifeRepository,
    private val appContext: Context? = null
) : ViewModel() {

    private val mutex = Mutex()

    val appData: StateFlow<AppData> = repository.appDataFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AndroidLifeRepository.defaultData())

    fun logHabit(suggestion: HabitSuggestion) {
        mutate { data ->
            val now = System.currentTimeMillis()
            val (next, habit) = CaptureApply.resolveHabit(data, suggestion, now)
            next.withEvent(
                LifeEvent(
                    id = newId(),
                    kind = EventKind.HABIT,
                    createdAt = now,
                    habitId = habit.id
                )
            )
        }
    }

    fun applyDrafts(drafts: List<CaptureDraft>) {
        if (drafts.isEmpty()) return
        mutate { CaptureApply.applyAll(it, drafts) }
    }

    fun updateRecurring(def: RecurringDef) {
        mutate { data ->
            val without = data.recurring.filterNot { it.id == def.id }
            data.copy(recurring = without + def)
        }
    }

    fun deleteRecurring(id: String) {
        mutate { it.copy(recurring = it.recurring.filterNot { r -> r.id == id }) }
    }

    fun updatePlanned(item: PlannedItem) {
        mutate { data ->
            val without = data.planned.filterNot { it.id == item.id }
            data.copy(planned = without + item)
        }
    }

    fun deletePlanned(id: String) {
        mutate { it.copy(planned = it.planned.filterNot { p -> p.id == id }) }
    }

    fun editHabit(habitId: String, name: String, icon: String, why: String = "") {
        mutate { HabitDomain.edit(it, habitId, name, icon, why) }
    }

    fun saveHabitAsNew(
        name: String,
        icon: String,
        why: String = "",
        reassignEventId: String? = null
    ) {
        mutate { HabitDomain.saveAsNew(it, name, icon, why, reassignEventId) }
    }

    fun logNote(body: String, tags: List<String>, isWant: Boolean = false, dueDate: String? = null) {
        val text = body.trim()
        if (text.isEmpty()) return
        mutate { data ->
            val today = LocalDate.now()
            var next = data
            for (t in tags) next = next.withTag(t)
            val want = isWant
            val due = WantDomain.resolveDueOnSave(want, dueDate, today)
            next.withEvent(
                LifeEvent(
                    id = newId(),
                    kind = EventKind.NOTE,
                    createdAt = System.currentTimeMillis(),
                    body = text,
                    tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                    dueDate = due,
                    isWant = want,
                    wantStatus = if (want) WantStatus.OPEN else WantStatus.NONE
                )
            )
        }
    }

    fun completeWant(id: String) {
        mutate { WantDomain.withCompletedWant(it, id, System.currentTimeMillis()) }
    }

    fun failWant(id: String, reflection: String) {
        mutate {
            WantDomain.withFailedWant(it, id, reflection, System.currentTimeMillis(), LocalDate.now())
        }
    }

    fun logFinance(
        direction: MoneyDirection,
        amountMinor: Long,
        payee: String,
        category: String,
        memo: String
    ) {
        if (amountMinor <= 0L) return
        mutate { data ->
            data.withPayee(payee)
                .withCategory(category)
                .withEvent(
                    LifeEvent(
                        id = newId(),
                        kind = EventKind.FINANCE,
                        createdAt = System.currentTimeMillis(),
                        body = memo.trim(),
                        amountMinor = amountMinor,
                        direction = direction,
                        payee = payee.trim(),
                        category = category.trim()
                    )
                )
        }
    }

    fun updateEvent(event: LifeEvent) {
        mutate { data ->
            var next = data.withEvent(event)
            if (event.kind == EventKind.NOTE) {
                for (t in event.tags) next = next.withTag(t)
            }
            if (event.kind == EventKind.FINANCE) {
                next = next.withPayee(event.payee).withCategory(event.category)
            }
            next
        }
    }

    fun updateNote(
        existing: LifeEvent,
        body: String,
        tags: List<String>,
        isWant: Boolean,
        dueDate: String?
    ) {
        val today = LocalDate.now()
        val updated = WantDomain.applyWantFields(
            existing.copy(
                body = body.trim(),
                tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            ),
            isWant,
            dueDate,
            today
        )
        updateEvent(updated)
    }

    fun deleteEvent(id: String) {
        mutate { it.withoutEvent(id) }
    }

    fun updateSettings(
        openingBalanceMinor: Long? = null,
        currencyCode: String? = null,
        colorScheme: String? = null,
        backgroundMode: String? = null,
        wantRemindersEnabled: Boolean? = null,
        monthlySavingsMinor: Long? = null
    ) {
        mutate { data ->
            data.copy(
                openingBalanceMinor = openingBalanceMinor ?: data.openingBalanceMinor,
                currencyCode = currencyCode?.ifBlank { data.currencyCode } ?: data.currencyCode,
                colorScheme = colorScheme ?: data.colorScheme,
                backgroundMode = backgroundMode ?: data.backgroundMode,
                wantRemindersEnabled = wantRemindersEnabled ?: data.wantRemindersEnabled,
                monthlySavingsMinor = monthlySavingsMinor ?: data.monthlySavingsMinor
            )
        }
    }

    fun exportJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            val payload = repository.encodeBackupJson(appData.value, pretty = true)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    fun importJson(context: Context, uri: Uri, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Could not read file")
                }
                val parsed = repository.decodeBackupJson(text)
                mutex.withLock {
                    repository.saveData(parsed)
                    appContext?.let { WantScheduler.scheduleAll(it, parsed) }
                }
            }
            onDone(result.map { })
        }
    }

    private fun mutate(block: (AppData) -> AppData) {
        viewModelScope.launch {
            mutex.withLock {
                val next = block(appData.value)
                repository.saveData(next)
                val ctx = appContext
                if (ctx != null) WantScheduler.scheduleAll(ctx, next)
            }
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext
                    return LifeViewModel(
                        AndroidLifeRepository(app),
                        app
                    ) as T
                }
            }
    }
}
