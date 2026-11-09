package com.life.app.watch

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object GadgetbridgeClient {
    private val mac = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    fun statusLabel(context: Context, prefs: WatchPreferences): String {
        if (!prefs.enabled) return "Off"
        return if (packageInstalled(context, normalize(prefs.gadgetbridgePackage))) "Registered" else "Off"
    }

    suspend fun registerListener(context: Context, prefs: WatchPreferences): String {
        val pkg = normalize(prefs.gadgetbridgePackage)
        val registered = prefs.enabled && packageInstalled(context, pkg)
        if (registered) {
            val intent = command(context, pkg)
            val device = WatchMessageReceiver.lastDevice
            if (!device.isNullOrBlank()) intent.putExtra(WatchPayload.EXTRA_DEVICE, device)
            runCatching { context.sendBroadcast(intent) }
        }
        recordWatchDebug(
            context,
            "info",
            "register",
            mapOf(
                "package" to pkg,
                "registered" to registered,
                "appId" to WatchSettings.APP_ID
            )
        )
        return if (registered) "Registered" else "Off"
    }

    suspend fun send(context: Context, prefs: WatchPreferences, outgoing: WatchOutgoing) {
        val pkg = normalize(prefs.gadgetbridgePackage)
        val type = typeOf(outgoing)
        if (!prefs.enabled || !packageInstalled(context, pkg)) {
            recordWatchDebug(
                context,
                "warn",
                "send",
                mapOf("registered" to false, "package" to pkg, "type" to type)
            )
            return
        }
        val json = WatchMessages.encode(outgoing)
        val body = json.toByteArray(Charsets.UTF_8)
        val payload = WatchPayload.encode(body)
        if (payload == null) {
            recordWatchDebug(
                context,
                "error",
                "send",
                mapOf("reason" to "bad_payload", "bytes" to body.size, "type" to type)
            )
            return
        }
        val intent = command(context, pkg).putExtra(WatchPayload.EXTRA_PAYLOAD, payload)
        val device = WatchMessageReceiver.lastDevice
        if (!device.isNullOrBlank()) intent.putExtra(WatchPayload.EXTRA_DEVICE, device)
        val sent = runCatching { context.sendBroadcast(intent) }
        val detail = linkedMapOf<String, Any?>(
            "type" to type,
            "bytes" to body.size,
            "appId" to WatchSettings.APP_ID,
            "package" to pkg
        )
        when (outgoing) {
            is WatchOutgoing.Ok -> {
                if (outgoing.ref == "want_done") detail["wantId"] = outgoing.id
                else if (outgoing.id != null) detail["habitId"] = outgoing.id
            }
            is WatchOutgoing.Error -> detail["reason"] = outgoing.reason
            is WatchOutgoing.Habits -> {
                detail["habitCount"] = outgoing.items.size
                detail["wantCount"] = outgoing.wants.size
            }
            is WatchOutgoing.Digest -> {
                detail["habitCount"] = outgoing.habits.size
                detail["wantCount"] = outgoing.wants.size
            }
        }
        if (sent.isFailure) detail["error"] = sent.exceptionOrNull()?.javaClass?.simpleName
        recordWatchDebug(context, if (sent.isSuccess) "info" else "error", "send", detail)
    }

    fun rememberDevice(raw: String?) {
        val address = raw?.trim()?.uppercase() ?: return
        if (mac.matches(address)) WatchMessageReceiver.lastDevice = address
    }

    private fun command(context: Context, pkg: String): Intent =
        Intent(WatchPayload.COMMAND_SEND)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            .putExtra(WatchPayload.EXTRA_APP_ID, WatchSettings.APP_ID)
            .putExtra(WatchPayload.EXTRA_REPLY_TO, replyTo(context))

    private fun replyTo(context: Context): PendingIntent {
        val reply = Intent(context, WatchMessageReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            reply,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun packageInstalled(context: Context, name: String): Boolean {
        if (!name.contains('.')) return false
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(name, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun normalize(raw: String): String = raw.trim().ifBlank { WatchSettings.DEFAULT_GB_PACKAGE }

    private fun typeOf(outgoing: WatchOutgoing): String = when (outgoing) {
        is WatchOutgoing.Habits -> "habits"
        is WatchOutgoing.Ok -> outgoing.ref
        is WatchOutgoing.Error -> "error"
        is WatchOutgoing.Digest -> "digest"
    }
}
