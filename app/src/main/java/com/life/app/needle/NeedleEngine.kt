package com.life.app.needle

import com.life.app.data.AppData
import java.time.LocalDate
import kotlinx.serialization.json.Json

/**
 * On-device Needle 2. Global C API — one mutex. When the .so is missing,
 * [interpret] uses [NeedleFallback] so capture still works.
 */
object NeedleEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val lock = Any()
    private var lastSystem: String? = null
    private var lastTools: String? = null

    val nativeAvailable: Boolean
        get() = NeedleJni.tryLoad()

    fun interpret(
        text: String,
        data: AppData,
        today: LocalDate = LocalDate.now()
    ): List<NeedleDraft> {
        val clauses = text.split(Regex("[;\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (clauses.isEmpty()) return emptyList()
        return clauses.flatMap { clause ->
            val response = complete(clause, data)
            NeedleDispatcher.drafts(clause, data, response, today)
        }
    }

    fun complete(text: String, data: AppData): NeedleResponse {
        val system = NeedleTools.systemPrompt(data)
        val tools = NeedleTools.JSON
        if (!nativeAvailable) return NeedleFallback.parse(text, data)
        synchronized(lock) {
            if (system != lastSystem || tools != lastTools) {
                val rc = NeedleJni.nativeInit(system, tools, null)
                if (rc < 0) {
                    lastSystem = null
                    lastTools = null
                    return NeedleFallback.parse(text, data)
                }
                lastSystem = system
                lastTools = tools
            }
            val raw = NeedleJni.nativeComplete(text, 128)
                ?: return NeedleFallback.parse(text, data)
            NeedleJni.nativeReset()
            return try {
                json.decodeFromString<NeedleResponse>(raw)
            } catch (_: Exception) {
                NeedleFallback.parse(text, data)
            }
        }
    }
}
