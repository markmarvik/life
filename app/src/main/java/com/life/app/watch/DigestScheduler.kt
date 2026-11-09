package com.life.app.watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DigestScheduler {
    const val EXTRA_SLOT = "slot"
    const val SLOT_MORNING = "morning"
    const val SLOT_EVENING = "evening"
    const val MORNING_REQUEST = 41001
    const val EVENING_REQUEST = 41002

    fun scheduleAll(
        context: Context,
        prefs: WatchPreferences,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        arm(context, am, prefs.morningDigestEnabled, MORNING_REQUEST, SLOT_MORNING, prefs.morningHour, nowMs, zone)
        arm(context, am, prefs.eveningDigestEnabled, EVENING_REQUEST, SLOT_EVENING, prefs.eveningHour, nowMs, zone)
        if (prefs.morningDigestEnabled || prefs.eveningDigestEnabled) {
            CoroutineScope(Dispatchers.IO).launch {
                recordWatchDebug(context, "info", "schedule", mapOf("notification" to "scheduled"))
            }
        }
    }

    private fun arm(
        context: Context,
        am: AlarmManager,
        enabled: Boolean,
        request: Int,
        slot: String,
        hour: Int,
        nowMs: Long,
        zone: ZoneId
    ) {
        val pi = pending(context, request, slot)
        if (!enabled) {
            am.cancel(pi)
            return
        }
        val trigger = next(hour, nowMs, zone)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun next(hour: Int, nowMs: Long, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        var at = now.toLocalDate().atTime(LocalTime.of(hour.coerceIn(0, 23), 0)).atZone(zone)
        if (!at.toInstant().isAfter(now.toInstant())) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    private fun pending(context: Context, request: Int, slot: String): PendingIntent {
        val intent = Intent(context, DigestReceiver::class.java).apply {
            putExtra(EXTRA_SLOT, slot)
        }
        return PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
