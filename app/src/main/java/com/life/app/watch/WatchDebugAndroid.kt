package com.life.app.watch

import android.content.Context
import android.util.Log

object WatchDebugAndroid {
    const val TAG = "LifeWatch"

    fun write(level: String, line: String) {
        when (level) {
            "error" -> Log.e(TAG, line)
            "warn" -> Log.w(TAG, line)
            else -> Log.i(TAG, line)
        }
    }
}

suspend fun recordWatchDebug(context: Context, level: String, name: String, detail: Map<String, Any?>) {
    val event = WatchLogs.log.record(System.currentTimeMillis(), level, name, detail)
    val line = WatchLogs.log.formatLine(event)
    WatchDebugAndroid.write(level, line)
    WatchSettingsStore(context.applicationContext).appendDebug(line)
}
