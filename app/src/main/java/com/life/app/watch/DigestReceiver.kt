package com.life.app.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.life.app.data.AndroidLifeRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(DigestScheduler.EXTRA_SLOT) ?: DigestScheduler.SLOT_MORNING
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliver(app, slot)
            } catch (t: Throwable) {
                runCatching {
                    recordWatchDebug(app, "error", "digest", mapOf("error" to t.javaClass.simpleName))
                }
            } finally {
                runCatching {
                    val prefs = WatchSettingsStore(app).load()
                    DigestScheduler.scheduleAll(app, prefs)
                }
                pending.finish()
            }
        }
    }
}

private suspend fun deliver(context: Context, slot: String) {
    val prefs = WatchSettingsStore(context).load()
    val enabled = if (slot == DigestScheduler.SLOT_EVENING) prefs.eveningDigestEnabled else prefs.morningDigestEnabled
    if (!enabled) return
    val data = AndroidLifeRepository(context).appDataFlow.first()
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val path = TodayPath.digest(data, now, today, zone)
    fun detail(notification: String): Map<String, Any?> = mapOf(
        "habitCount" to path.habits.size,
        "wantCount" to path.wants.size,
        "notification" to notification
    )
    if (path.habits.isEmpty() && path.wants.isEmpty()) {
        recordWatchDebug(context, "info", "digest", detail("empty"))
        return
    }
    if (!DigestNotifications.canNotify(context)) {
        recordWatchDebug(context, "warn", "digest", detail("permission-denied"))
    } else {
        val title = if (slot == DigestScheduler.SLOT_EVENING) "Still open" else "Today's path"
        val text = (path.habits.map { it.name } + path.wants.map { it.title }).joinToString(", ")
        val id = if (slot == DigestScheduler.SLOT_EVENING) DigestScheduler.EVENING_REQUEST else DigestScheduler.MORNING_REQUEST
        DigestNotifications.show(context, title, text, id)
        recordWatchDebug(context, "info", "digest", detail("fired"))
    }
    if (prefs.enabled) {
        GadgetbridgeClient.send(context, prefs, WatchOutgoing.Digest(path.habits, path.wants))
    }
}
