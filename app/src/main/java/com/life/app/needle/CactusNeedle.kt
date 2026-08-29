package com.life.app.needle

import android.content.Context
import com.cactus.CactusCompletionParams
import com.cactus.CactusInitParams
import com.cactus.CactusLM
import com.cactus.CactusModelManager
import com.cactus.CactusSTT
import com.cactus.CactusTranscriptionParams
import com.cactus.ChatMessage
import com.cactus.InferenceMode
import com.cactus.TranscriptionMode
import com.cactus.models.ToolParameter
import com.cactus.models.createTool
import com.cactus.services.CactusConfig
import com.life.app.data.Money
import com.life.app.data.MoneyDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional on-device Cactus Needle (tool-calling LLM) + Whisper STT.
 * Local inference only. Models live on disk after an explicit one-time install.
 * Never call [ensureLm]/[ensureStt] with install=true unless the user asked —
 * [CactusLM.initializeModel] will otherwise auto-download.
 */
class CactusNeedle(
    private val appContext: Context
) {
    private var lm: CactusLM? = null
    private var stt: CactusSTT? = null
    var lmSlug: String? = null
        private set
    var sttSlug: String? = null
        private set

    val lmReady: Boolean get() = lm?.isLoaded() == true
    val sttReady: Boolean get() = stt?.isReady() == true

    suspend fun ensureLm(status: (String) -> Unit, install: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (lmReady) return@withContext true
            runCatching {
                CactusConfig.isTelemetryEnabled = false
                val cactus = CactusLM(enableToolFiltering = false)
                val local = PREFERRED_LM.firstOrNull { CactusModelManager.isModelDownloaded(it) }
                val slug = when {
                    local != null -> local
                    !install -> return@withContext false
                    else -> pickRemoteSlug(cactus, status)
                }
                if (!CactusModelManager.isModelDownloaded(slug)) {
                    if (!install) return@withContext false
                    status("Installing $slug (one-time, then offline)…")
                    cactus.downloadModel(slug)
                }
                status("Loading $slug…")
                cactus.initializeModel(CactusInitParams(model = slug, contextSize = 512))
                if (!cactus.isLoaded()) return@withContext false
                lm = cactus
                lmSlug = slug
                true
            }.getOrElse { false }
        }

    suspend fun ensureStt(status: (String) -> Unit, install: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (sttReady) return@withContext true
            runCatching {
                CactusConfig.isTelemetryEnabled = false
                val engine = CactusSTT()
                val slug = "whisper-tiny"
                val have = runCatching { engine.isModelDownloaded(slug) }.getOrDefault(
                    CactusModelManager.isModelDownloaded(slug)
                )
                if (!have) {
                    if (!install) return@withContext false
                    status("Installing Whisper tiny (one-time, then offline)…")
                    engine.downloadModel(slug)
                }
                engine.initializeModel(CactusInitParams(model = slug))
                if (!engine.isReady()) return@withContext false
                stt = engine
                sttSlug = slug
                true
            }.getOrElse { false }
        }

    suspend fun dispatch(text: String, ctx: NeedleContext): NeedleDecision? =
        withContext(Dispatchers.Default) {
            val cactus = lm ?: return@withContext null
            if (!cactus.isLoaded()) return@withContext null
            runCatching {
                val result = cactus.generateCompletion(
                    messages = listOf(
                        ChatMessage(content = SYSTEM, role = "system"),
                        ChatMessage(content = text.trim(), role = "user")
                    ),
                    params = CactusCompletionParams(
                        maxTokens = 96,
                        tools = TOOLS,
                        forceTools = true,
                        mode = InferenceMode.LOCAL
                    )
                )
                val call = result?.toolCalls?.firstOrNull()
                if (call != null) {
                    fromTool(call.name, call.arguments, ctx)
                } else {
                    parseLoose(result?.response, ctx)
                }
            }.getOrNull()
        }

    suspend fun transcribe(pcm16le: ByteArray, lang: String): String? =
        withContext(Dispatchers.Default) {
            val engine = stt ?: return@withContext null
            if (pcm16le.size < 32_000) return@withContext null
            val code = if (lang == "sr") "sr" else "en"
            val prompt = "<|startoftranscript|><|$code|><|transcribe|><|notimestamps|>"
            runCatching {
                engine.transcribe(
                    filePath = null,
                    prompt = prompt,
                    params = CactusTranscriptionParams(maxTokens = 128),
                    mode = TranscriptionMode.LOCAL,
                    audioBuffer = pcm16le
                )?.text?.trim()?.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }

    fun unload() {
        runCatching { lm?.unload() }
        lm = null
        stt = null
    }

    private suspend fun pickRemoteSlug(cactus: CactusLM, status: (String) -> Unit): String {
        status("Looking for a tiny tool-calling model…")
        val models = runCatching { cactus.getModels() }.getOrNull().orEmpty()
        val bySlug = models.associateBy { it.slug.lowercase() }
        for (p in PREFERRED_LM) {
            bySlug[p]?.let { return it.slug }
        }
        models.filter { it.supports_tool_calling }
            .minByOrNull { it.size_mb }
            ?.let { return it.slug }
        return "gemma3-270m"
    }

    private fun fromTool(
        name: String,
        args: Map<String, String>,
        ctx: NeedleContext
    ): NeedleDecision {
        fun arg(k: String) = args.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value.orEmpty().trim()
        return when (name.lowercase()) {
            "log_habit" -> NeedleDecision.Habit(
                name = arg("name").ifBlank { arg("habit") },
                confidence = 0.86,
                engine = NeedleEngine.CACTUS
            )
            "add_note" -> {
                val want = arg("want_date").ifBlank { null }
                NeedleDecision.Note(
                    body = arg("body"),
                    tags = arg("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    wantDate = want,
                    confidence = 0.86,
                    engine = NeedleEngine.CACTUS
                )
            }
            "log_finance" -> {
                val sign = arg("sign").lowercase()
                val amount = Money.parseToMinor(arg("amount"), ctx.currencyCode)
                NeedleDecision.Finance(
                    direction = if (sign == "in" || sign == "+") MoneyDirection.IN else MoneyDirection.OUT,
                    amountMinor = amount,
                    payee = arg("payee"),
                    category = arg("category"),
                    memo = "",
                    confidence = 0.86,
                    engine = NeedleEngine.CACTUS
                )
            }
            "complete_want" -> NeedleDecision.CompleteWant(
                text = arg("text").ifBlank { arg("id_or_text") },
                confidence = 0.8,
                engine = NeedleEngine.CACTUS
            )
            "query_balance" -> NeedleDecision.Balance(0.9, NeedleEngine.CACTUS)
            else -> NeedleDecision.Unknown(name, engine = NeedleEngine.CACTUS)
        }
    }

    private fun parseLoose(response: String?, ctx: NeedleContext): NeedleDecision? {
        val t = response?.trim().orEmpty()
        if (t.isEmpty()) return null
        return PatternNeedle.dispatch(t, ctx).let {
            if (it is NeedleDecision.Unknown) null else it
        }
    }

    companion object {
        private val PREFERRED_LM = listOf("needle", "needle2", "needle-2", "gemma3-270m")

        private const val SYSTEM =
            "You log Life events. Call exactly one tool. Understand English and Serbian (Latin). " +
                "Habits are things the user just did. Money: sign=out for spending, in for income. " +
                "Amounts are numbers only."

        val TOOLS = listOf(
            createTool(
                name = "log_habit",
                description = "Log a habit the user just did.",
                parameters = mapOf(
                    "name" to ToolParameter(type = "string", description = "Habit name", required = true)
                )
            ),
            createTool(
                name = "add_note",
                description = "Save a note. Set want_date if it is a commitment.",
                parameters = mapOf(
                    "body" to ToolParameter(type = "string", description = "Note text", required = true),
                    "tags" to ToolParameter(type = "string", description = "Comma tags", required = false),
                    "want_date" to ToolParameter(
                        type = "string",
                        description = "yyyy-MM-dd or today/tomorrow",
                        required = false
                    )
                )
            ),
            createTool(
                name = "log_finance",
                description = "Log income or expense.",
                parameters = mapOf(
                    "sign" to ToolParameter(type = "string", description = "in or out", required = true),
                    "amount" to ToolParameter(type = "string", description = "Numeric amount", required = true),
                    "payee" to ToolParameter(type = "string", description = "Who", required = false),
                    "category" to ToolParameter(type = "string", description = "Category", required = false)
                )
            ),
            createTool(
                name = "complete_want",
                description = "Mark an upcoming Want done.",
                parameters = mapOf(
                    "text" to ToolParameter(type = "string", description = "Want text", required = true)
                )
            ),
            createTool(
                name = "query_balance",
                description = "Read current money balance.",
                parameters = emptyMap()
            )
        )
    }
}
