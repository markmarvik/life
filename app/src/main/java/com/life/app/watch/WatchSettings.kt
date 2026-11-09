package com.life.app.watch

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.life.app.data.lifeDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WatchSettings {
    const val DEFAULT_GB_PACKAGE = "nodomain.freeyourgadget.gadgetbridge.zepptest"
    const val APP_ID = "3266101"
}

@Serializable
data class WatchPreferences(
    val enabled: Boolean = true,
    val gadgetbridgePackage: String = WatchSettings.DEFAULT_GB_PACKAGE,
    val morningDigestEnabled: Boolean = true,
    val eveningDigestEnabled: Boolean = true,
    val morningHour: Int = 8,
    val eveningHour: Int = 20
)

class WatchSettingsStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val prefsKey = stringPreferencesKey("watch_prefs")
    private val debugKey = stringPreferencesKey("watch_debug_log")

    suspend fun load(): WatchPreferences {
        val raw = context.lifeDataStore.data.first()[prefsKey]
        if (raw.isNullOrBlank()) return WatchPreferences()
        return runCatching { json.decodeFromString<WatchPreferences>(raw) }.getOrDefault(WatchPreferences())
    }

    suspend fun save(prefs: WatchPreferences) {
        val encoded = json.encodeToString(prefs)
        context.lifeDataStore.edit { preferences ->
            preferences[prefsKey] = encoded
        }
    }

    suspend fun loadDebug(): String = context.lifeDataStore.data.first()[debugKey].orEmpty()

    suspend fun appendDebug(line: String) {
        val clean = line.replace("\n", " ").trim()
        if (clean.isEmpty()) return
        context.lifeDataStore.edit { preferences ->
            val prev = preferences[debugKey].orEmpty().lines().filter { it.isNotBlank() }
            preferences[debugKey] = (listOf(clean) + prev).take(WatchDebugLog.MAX).joinToString("\n")
        }
    }
}
