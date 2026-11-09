package com.life.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.life.app.data.AndroidLifeRepository
import com.life.app.watch.DigestScheduler
import com.life.app.watch.GadgetbridgeClient
import com.life.app.watch.WatchSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = AndroidLifeRepository(app).appDataFlow.first()
                WantScheduler.scheduleAll(app, data)
                val prefs = WatchSettingsStore(app).load()
                if (prefs.enabled) GadgetbridgeClient.registerListener(app, prefs)
                DigestScheduler.scheduleAll(app, prefs)
            } finally {
                pending.finish()
            }
        }
    }
}
