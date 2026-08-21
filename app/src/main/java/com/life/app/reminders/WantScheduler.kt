package com.life.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.life.app.data.AppData
import com.life.app.data.LifeEvent
import com.life.app.data.WantDomain
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object WantScheduler {
    const val EXTRA_WANT_ID = "want_id"
    const val EXTRA_KIND = "kind"
    const val KIND_EVE = "eve"
    const val KIND_MORN = "morn"
    const val EVE_HOUR = 20
    const val MORN_HOUR = 8

    fun scheduleAll(
        context: Context,
        data: AppData,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val open = data.events.filter { it.isOpenWant() }
        for (event in open) {
            cancelEvent(context, am, event)
            if (!data.wantRemindersEnabled) continue
            val due = WantDomain.parseDue(event.dueDate) ?: continue
            val eve = at(due.minusDays(1), EVE_HOUR, zone)
            val morn = at(due, MORN_HOUR, zone)
            if (eve > nowMs) setAlarm(context, am, event, KIND_EVE, eve)
            if (morn > nowMs) setAlarm(context, am, event, KIND_MORN, morn)
        }
    }

    private fun at(date: LocalDate, hour: Int, zone: ZoneId): Long =
        date.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun setAlarm(
        context: Context,
        am: AlarmManager,
        event: LifeEvent,
        kind: String,
        triggerAt: Long
    ) {
        val pi = pending(context, event, kind)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancelEvent(context: Context, am: AlarmManager, event: LifeEvent) {
        am.cancel(pending(context, event, KIND_EVE))
        am.cancel(pending(context, event, KIND_MORN))
    }

    private fun pending(context: Context, event: LifeEvent, kind: String): PendingIntent {
        val intent = Intent(context, WantReceiver::class.java).apply {
            putExtra(EXTRA_WANT_ID, event.id)
            putExtra(EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(event.id, kind),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun requestCode(eventId: String, kind: String): Int =
        (eventId + ":" + kind).hashCode() and 0x7fffffff
}
