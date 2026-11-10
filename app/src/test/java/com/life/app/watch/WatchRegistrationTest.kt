package com.life.app.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchRegistrationTest {
    private val stored = "AA:BB:CC:DD:EE:FF"

    @Test
    fun registersFromStoredAddressWithoutAnIncomingMessage() {
        val decision = WatchRegistration.decide(
            enabled = true,
            packageInstalled = true,
            storedAddress = stored.lowercase(),
            bonded = emptyList()
        )
        assertEquals(WatchRegistration.REGISTERED, decision.status)
        assertEquals(stored, decision.deviceAddress)
        assertTrue(decision.sendsDeviceExtra)
        val rendered = render(WatchRegistration.detail(WatchSettings.DEFAULT_GB_PACKAGE, true))
        assertFalse(rendered.contains(stored))
        assertTrue(rendered.contains("registered=true"))
    }

    @Test
    fun doesNotClaimRegisteredWhenDeviceExtraIsMissing() {
        val decision = WatchRegistration.decide(
            enabled = true,
            packageInstalled = true,
            storedAddress = null,
            bonded = listOf(BondedDevice("11:22:33:44:55:66", "Pixel Buds"))
        )
        assertEquals(WatchRegistration.WAITING, decision.status)
        assertNull(decision.deviceAddress)
        assertFalse(decision.sendsDeviceExtra)
        val rendered = render(WatchRegistration.detail(WatchSettings.DEFAULT_GB_PACKAGE, false, "no_device"))
        assertFalse(rendered.contains("registered=true"))
        assertTrue(rendered.contains("reason=no_device"))
        assertFalse(rendered.contains(":"))
    }

    @Test
    fun coldStartUsesTheOnlyBondedBip() {
        val decision = WatchRegistration.decide(
            enabled = true,
            packageInstalled = true,
            storedAddress = "not-a-mac",
            bonded = listOf(
                BondedDevice("11:22:33:44:55:66", "Pixel Buds"),
                BondedDevice("aa:bb:cc:dd:ee:10", "Amazfit Bip Max"),
                BondedDevice("aa:bb:cc:dd:ee:11", "Amazfit GTR")
            )
        )
        assertEquals(WatchRegistration.REGISTERED, decision.status)
        assertEquals("AA:BB:CC:DD:EE:10", decision.deviceAddress)
    }

    @Test
    fun doesNotGuessBetweenTwoBips() {
        val decision = WatchRegistration.decide(
            enabled = true,
            packageInstalled = true,
            storedAddress = null,
            bonded = listOf(
                BondedDevice("AA:BB:CC:DD:EE:10", "Amazfit Bip Max"),
                BondedDevice("AA:BB:CC:DD:EE:12", "Amazfit Bip")
            )
        )
        assertEquals(WatchRegistration.WAITING, decision.status)
        assertNull(decision.deviceAddress)
    }

    @Test
    fun disabledOrMissingPackageIsOff() {
        assertEquals(
            WatchRegistration.OFF,
            WatchRegistration.decide(false, true, stored, emptyList()).status
        )
        assertEquals(
            WatchRegistration.OFF,
            WatchRegistration.decide(true, false, stored, emptyList()).status
        )
    }

    private fun render(detail: Map<String, Any?>): String =
        detail.entries.joinToString(" ") { "${it.key}=${it.value}" }
}
