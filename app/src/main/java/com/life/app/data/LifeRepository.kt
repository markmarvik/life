package com.life.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

val Context.lifeDataStore: DataStore<Preferences> by preferencesDataStore(name = "life_prefs")

object DataLoadDiagnostics {
    const val CORRUPT_FILE = "corrupt_app_data_backup.json"

    @Volatile
    var lastErrorMessage: String? = null
        private set

    @Volatile
    var corruptBackupPath: String? = null
        private set

    @Volatile
    var userDismissed: Boolean = false

    fun recordCorruptLoad(context: Context, jsonString: String, error: Throwable) {
        lastErrorMessage = error.message ?: error.javaClass.simpleName
        userDismissed = false
        try {
            context.openFileOutput(CORRUPT_FILE, Context.MODE_PRIVATE).use { out ->
                out.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            corruptBackupPath = context.getFileStreamPath(CORRUPT_FILE)?.absolutePath
        } catch (_: Exception) {
            corruptBackupPath = null
        }
    }

    fun clearIfRecovered() {
        lastErrorMessage = null
    }

    fun dismiss() {
        userDismissed = true
    }

    fun shouldShowBanner(): Boolean =
        !userDismissed && !lastErrorMessage.isNullOrBlank()
}

interface LifeRepository {
    val appDataFlow: Flow<AppData>
    suspend fun saveData(data: AppData)
    fun encodeBackupJson(data: AppData, pretty: Boolean = true): String
    fun decodeBackupJson(jsonString: String): AppData
}

class AndroidLifeRepository(private val context: Context) : LifeRepository {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dataKey = stringPreferencesKey("app_data")

    private var lastJson: String? = null
    private var cachedParsed: AppData? = null

    override val appDataFlow: Flow<AppData> = context.lifeDataStore.data
        .map { preferences ->
            val jsonString = preferences[dataKey] ?: ""
            if (jsonString.isBlank()) {
                defaultData()
            } else if (jsonString == lastJson && cachedParsed != null) {
                cachedParsed!!
            } else {
                try {
                    val parsed = json.decodeFromString<AppData>(jsonString)
                    val migrated = migrateIfNeeded(parsed)
                    lastJson = jsonString
                    cachedParsed = migrated
                    DataLoadDiagnostics.clearIfRecovered()
                    migrated
                } catch (e: Exception) {
                    DataLoadDiagnostics.recordCorruptLoad(context, jsonString, e)
                    defaultData()
                }
            }
        }

    override suspend fun saveData(data: AppData) {
        val encoded = json.encodeToString(data)
        lastJson = encoded
        cachedParsed = data
        context.lifeDataStore.edit { preferences ->
            preferences[dataKey] = encoded
        }
    }

    override fun decodeBackupJson(jsonString: String): AppData {
        val parsed = json.decodeFromString<AppData>(jsonString)
        return migrateIfNeeded(parsed)
    }

    override fun encodeBackupJson(data: AppData, pretty: Boolean): String {
        val encoder = if (pretty) {
            Json {
                prettyPrint = true
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
        } else {
            json
        }
        return encoder.encodeToString(data)
    }

    private fun migrateIfNeeded(data: AppData): AppData {
        var next = data
        if (next.categories.isEmpty()) {
            next = next.copy(categories = Seed.FINANCE_CATEGORIES)
        }
        if (next.schemaVersion < AppData.SCHEMA_VERSION) {
            next = next.copy(schemaVersion = AppData.SCHEMA_VERSION)
        }
        return next
    }

    companion object {
        fun defaultData(locale: Locale = Locale.getDefault()): AppData = AppData(
            currencyCode = Money.defaultCurrencyCode(locale),
            categories = Seed.FINANCE_CATEGORIES
        )
    }

    private fun defaultData(): AppData = Companion.defaultData()
}
