package com.life.app.watch

import com.life.app.data.AppData
import com.life.app.data.EventKind
import com.life.app.data.HabitDomain
import com.life.app.data.LifeEvent
import com.life.app.data.WantDomain
import com.life.app.data.WantStatus
import com.life.app.data.withEvent
import java.time.LocalDate
import java.util.UUID

object WatchActions {
    data class Result(val data: AppData, val outgoing: WatchOutgoing?)

    @Suppress("UNUSED_PARAMETER")
    fun apply(data: AppData, incoming: WatchIncoming, nowMs: Long, today: LocalDate): Result {
        return when (incoming) {
            WatchIncoming.Sync -> Result(data, null)
            is WatchIncoming.LogHabit -> {
                val ensured = HabitDomain.ensureHabit(data, incoming.habitId, nowMs)
                    ?: return Result(data, WatchOutgoing.Error("unknown_id"))
                val (next, habit) = ensured
                Result(
                    next.withEvent(
                        LifeEvent(
                            id = UUID.randomUUID().toString(),
                            kind = EventKind.HABIT,
                            createdAt = nowMs,
                            habitId = habit.id
                        )
                    ),
                    WatchOutgoing.Ok("log_habit", habit.id)
                )
            }
            is WatchIncoming.Note -> Result(
                data.withEvent(
                    LifeEvent(
                        id = UUID.randomUUID().toString(),
                        kind = EventKind.NOTE,
                        createdAt = nowMs,
                        body = incoming.text.trim(),
                        wantStatus = WantStatus.NONE
                    )
                ),
                WatchOutgoing.Ok("note", null)
            )
            is WatchIncoming.WantDone -> {
                val event = data.events.firstOrNull { it.id == incoming.wantId }
                if (event == null || !event.isOpenWant()) {
                    return Result(data, WatchOutgoing.Error("unknown_id"))
                }
                Result(
                    WantDomain.withCompletedWant(data, incoming.wantId, nowMs),
                    WatchOutgoing.Ok("want_done", incoming.wantId)
                )
            }
        }
    }
}
