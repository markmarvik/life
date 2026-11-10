package com.life.app.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.life.app.data.AndroidLifeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.ZoneId

class WatchMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != null && action != WatchPayload.ACTION_RECEIVE) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                gate.withLock { handle(app, intent) }
            } catch (t: Throwable) {
                runCatching {
                    recordWatchDebug(app, "error", "recv", mapOf("error" to t.javaClass.simpleName))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val gate = Mutex()

        @Volatile
        var lastDevice: String? = null
    }
}

private suspend fun handle(context: Context, intent: Intent) {
    val prefs = WatchSettingsStore(context).load()
    if (!prefs.enabled) {
        recordWatchDebug(
            context,
            "info",
            "recv",
            mapOf("registered" to false, "package" to prefs.gadgetbridgePackage)
        )
        return
    }
    val canonical = WatchPayload.canonicalAppId(intent.getStringExtra(WatchPayload.EXTRA_APP_ID))
    if (canonical != WatchSettings.APP_ID) {
        recordWatchDebug(context, "info", "ignore", mapOf("appId" to canonical.orEmpty()))
        return
    }
    GadgetbridgeClient.rememberDevice(context, intent.getStringExtra(WatchPayload.EXTRA_DEVICE))
    val encoded = intent.getStringExtra(WatchPayload.EXTRA_PAYLOAD)
    val body = WatchPayload.decode(encoded)
    if (body == null) {
        recordWatchDebug(
            context,
            "warn",
            "recv",
            mapOf("reason" to "bad_payload", "bytes" to (encoded?.length ?: 0), "appId" to canonical)
        )
        return
    }
    val text = decodeUtf8(body)
    val incoming = text?.let { WatchMessages.parseIncoming(it) }
    if (incoming == null) {
        recordWatchDebug(
            context,
            "warn",
            "recv",
            mapOf("reason" to "bad_payload", "bytes" to body.size, "appId" to canonical)
        )
        return
    }
    val detail = linkedMapOf<String, Any?>(
        "type" to incomingType(incoming),
        "appId" to canonical,
        "bytes" to body.size
    )
    when (incoming) {
        is WatchIncoming.LogHabit -> detail["habitId"] = incoming.habitId
        is WatchIncoming.WantDone -> detail["wantId"] = incoming.wantId
        else -> Unit
    }
    recordWatchDebug(context, "info", "recv", detail)
    val repo = AndroidLifeRepository(context)
    val data = repo.appDataFlow.first()
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val result = WatchActions.apply(data, incoming, now, today)
    if (result.data != data) repo.saveData(result.data)
    val path = TodayPath.build(result.data, now, today, zone)
    result.outgoing?.let { GadgetbridgeClient.send(context, prefs, it) }
    GadgetbridgeClient.send(context, prefs, WatchOutgoing.Habits(path.habits, path.wants))
}

private fun incomingType(incoming: WatchIncoming): String = when (incoming) {
    WatchIncoming.Sync -> "sync"
    is WatchIncoming.LogHabit -> "log_habit"
    is WatchIncoming.Note -> "note"
    is WatchIncoming.WantDone -> "want_done"
}

private fun decodeUtf8(body: ByteArray): String? = runCatching {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    decoder.decode(ByteBuffer.wrap(body)).toString()
}.getOrNull()
