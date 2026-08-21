package com.life.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.life.app.data.AndroidLifeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WantReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(WantScheduler.EXTRA_WANT_ID) ?: return
        val kind = intent.getStringExtra(WantScheduler.EXTRA_KIND) ?: return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = AndroidLifeRepository(app).appDataFlow.first()
                if (!data.wantRemindersEnabled) return@launch
                val event = data.events.firstOrNull { it.id == id && it.isOpenWant() } ?: return@launch
                val title = if (kind == WantScheduler.KIND_EVE) "Tomorrow" else "Today"
                WantNotifications.show(
                    app,
                    title,
                    event.body,
                    WantScheduler.requestCode(id, kind)
                )
            } finally {
                pending.finish()
            }
        }
    }
}
