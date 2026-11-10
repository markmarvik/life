package com.life.app.watch

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GadgetbridgeClient {
    private const val GB_CONNECTED = "nodomain.freeyourgadget.gadgetbridge.BLUETOOTH_CONNECTED"
    private const val GB_ADDRESS = "EXTRA_DEVICE_ADDRESS"

    @Volatile
    private var linkRegistered = false

    fun needsBluetoothPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
    }

    fun ensureLinkListener(context: Context) {
        if (linkRegistered) return
        val app = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(GB_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        ContextCompat.registerReceiver(app, linkReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        linkRegistered = true
    }

    fun statusLabel(context: Context, prefs: WatchPreferences): String =
        decision(context, prefs).status

    suspend fun registerListener(context: Context, prefs: WatchPreferences): String {
        ensureLinkListener(context)
        val pkg = normalize(prefs.gadgetbridgePackage)
        val installed = packageInstalled(context, pkg)
        val decision = WatchRegistration.decide(prefs.enabled, installed, prefs.deviceAddress, bondedDevices(context))
        val address = decision.deviceAddress
        if (address != null && address != prefs.deviceAddress) {
            WatchSettingsStore(context).save(prefs.copy(deviceAddress = address))
        }
        if (address != null) WatchMessageReceiver.lastDevice = address
        val registered = decision.status == WatchRegistration.REGISTERED && address != null
        if (registered) {
            val intent = command(context, pkg).putExtra(WatchPayload.EXTRA_DEVICE, address)
            runCatching { context.sendBroadcast(intent) }
        }
        val reason = if (registered || decision.status == WatchRegistration.OFF) null else "no_device"
        recordWatchDebug(
            context,
            if (registered) "info" else "warn",
            "register",
            WatchRegistration.detail(pkg, registered, reason)
        )
        return decision.status
    }

    suspend fun send(context: Context, prefs: WatchPreferences, outgoing: WatchOutgoing) {
        val pkg = normalize(prefs.gadgetbridgePackage)
        val type = typeOf(outgoing)
        val address = currentAddress(context, prefs)
        if (!prefs.enabled || !packageInstalled(context, pkg) || address == null) {
            val detail = WatchRegistration.detail(pkg, false, "no_device").toMutableMap()
            detail["type"] = type
            recordWatchDebug(context, "warn", "send", detail)
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
        val intent = command(context, pkg)
            .putExtra(WatchPayload.EXTRA_PAYLOAD, payload)
            .putExtra(WatchPayload.EXTRA_DEVICE, address)
        val sent = runCatching { context.sendBroadcast(intent) }
        val detail = linkedMapOf<String, Any?>(
            "type" to type,
            "bytes" to body.size,
            "appId" to WatchSettings.APP_ID,
            "package" to pkg,
            "registered" to true
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

    suspend fun rememberDevice(context: Context, raw: String?) {
        val address = WatchRegistration.canonical(raw) ?: return
        WatchMessageReceiver.lastDevice = address
        val store = WatchSettingsStore(context)
        val prefs = store.load()
        if (prefs.deviceAddress != address) store.save(prefs.copy(deviceAddress = address))
    }

    private fun decision(context: Context, prefs: WatchPreferences): Registration =
        WatchRegistration.decide(
            prefs.enabled,
            packageInstalled(context, normalize(prefs.gadgetbridgePackage)),
            prefs.deviceAddress,
            bondedDevices(context)
        )

    private suspend fun currentAddress(context: Context, prefs: WatchPreferences): String? {
        WatchRegistration.canonical(WatchMessageReceiver.lastDevice)?.let { return it }
        return WatchRegistration.decide(true, true, prefs.deviceAddress, bondedDevices(context)).deviceAddress
    }

    @SuppressLint("MissingPermission")
    private fun bondedDevices(context: Context): List<BondedDevice> {
        if (needsBluetoothPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty().map { device ->
                BondedDevice(device.address.orEmpty(), device.name.orEmpty())
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private val linkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.applicationContext
            val raw = when (intent.action) {
                GB_CONNECTED -> intent.getStringExtra(GB_ADDRESS)
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val name = try {
                        device?.name.orEmpty()
                    } catch (_: SecurityException) {
                        ""
                    }
                    if (device == null || !WatchRegistration.looksLikeWatch(name)) {
                        null
                    } else {
                        try {
                            device.address
                        } catch (_: SecurityException) {
                            null
                        }
                    }
                }
                else -> null
            }
            if (WatchRegistration.canonical(raw) == null) return
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    rememberDevice(app, raw)
                    val prefs = WatchSettingsStore(app).load()
                    if (prefs.enabled) registerListener(app, prefs)
                } finally {
                    pending.finish()
                }
            }
        }
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
