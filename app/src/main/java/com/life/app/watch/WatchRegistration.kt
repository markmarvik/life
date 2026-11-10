package com.life.app.watch

data class BondedDevice(val address: String, val name: String)

data class Registration(
    val status: String,
    val deviceAddress: String?
) {
    val sendsDeviceExtra: Boolean get() = deviceAddress != null
}

object WatchRegistration {
    const val REGISTERED = "Registered"
    const val OFF = "Off"
    const val WAITING = "Waiting for the watch"

    private val mac = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    fun decide(
        enabled: Boolean,
        packageInstalled: Boolean,
        storedAddress: String?,
        bonded: List<BondedDevice>
    ): Registration {
        if (!enabled || !packageInstalled) return Registration(OFF, null)
        val address = pick(storedAddress, bonded) ?: return Registration(WAITING, null)
        return Registration(REGISTERED, address)
    }

    fun pick(storedAddress: String?, bonded: List<BondedDevice>): String? {
        canonical(storedAddress)?.let { return it }
        val watches = bonded.mapNotNull { device ->
            val address = canonical(device.address) ?: return@mapNotNull null
            if (!looksLikeWatch(device.name)) null else address to device.name
        }
        val bip = watches.filter { it.second.contains("bip", ignoreCase = true) }
        if (bip.size == 1) return bip.first().first
        if (watches.size == 1) return watches.first().first
        return null
    }

    fun looksLikeWatch(name: String): Boolean {
        val folded = name.lowercase()
        return folded.contains("bip") || folded.contains("amazfit") || folded.contains("zepp")
    }

    fun canonical(raw: String?): String? {
        val address = raw?.trim()?.uppercase() ?: return null
        if (!mac.matches(address)) return null
        return address
    }

    fun detail(packageName: String, registered: Boolean, reason: String? = null): Map<String, Any?> {
        val detail = linkedMapOf<String, Any?>(
            "package" to packageName,
            "registered" to registered,
            "appId" to WatchSettings.APP_ID
        )
        if (!reason.isNullOrBlank()) detail["reason"] = reason
        return detail
    }
}
