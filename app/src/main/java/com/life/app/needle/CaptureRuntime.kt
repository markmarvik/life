package com.life.app.needle

import android.content.Context
import com.cactus.CactusContextInitializer
import com.cactus.services.CactusConfig
import com.life.app.stt.MicRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-wide capture stack: PatternNeedle always, Cactus Needle + Whisper when installed.
 * Opening the composer never hits the network. Install is an explicit Settings action.
 */
object CaptureRuntime {
    private val mutex = Mutex()
    private var cactus: CactusNeedle? = null
    private var inited = false

    private val _status = MutableStateFlow("Typed capture ready (offline)")
    val status: StateFlow<String> = _status

    private val _needleReady = MutableStateFlow(false)
    val needleReady: StateFlow<Boolean> = _needleReady

    private val _sttReady = MutableStateFlow(false)
    val sttReady: StateFlow<Boolean> = _sttReady

    val mic = MicRecorder()

    fun initContext(context: Context) {
        if (inited) return
        runCatching {
            CactusConfig.isTelemetryEnabled = false
            CactusContextInitializer.initialize(context.applicationContext)
            inited = true
        }
    }

    suspend fun ensureModels(context: Context, install: Boolean) {
        mutex.withLock {
            initContext(context)
            val engine = cactus ?: CactusNeedle(context.applicationContext).also { cactus = it }
            if (install || !_needleReady.value) {
                val ok = engine.ensureLm({ _status.value = it }, install)
                _needleReady.value = ok
            }
            if (install || !_sttReady.value) {
                val ok = engine.ensureStt({ _status.value = it }, install)
                _sttReady.value = ok
            }
            _status.value = when {
                _needleReady.value && _sttReady.value ->
                    "Needle + Whisper ready (offline) · ${engine.lmSlug}"
                _needleReady.value ->
                    "Needle ready · voice pack missing"
                else ->
                    "Typed capture ready (offline). Voice pack not installed."
            }
        }
    }

    suspend fun dispatch(text: String, ctx: NeedleContext): NeedleDecision {
        val pattern = PatternNeedle.dispatch(text, ctx)
        if (pattern.canAutoCommit() && pattern.confidence >= 0.8) return pattern
        val cactusHit = cactus?.takeIf { it.lmReady }?.dispatch(text, ctx)
        if (cactusHit != null && cactusHit !is NeedleDecision.Unknown && cactusHit.confidence >= 0.5) {
            return cactusHit
        }
        return pattern
    }

    suspend fun transcribe(pcm: ByteArray, lang: String): String? {
        val padded = if (pcm.size < 32_000) pcm.copyOf(32_000) else pcm
        return cactus?.takeIf { it.sttReady }?.transcribe(padded, lang)
    }

    fun detectLang(text: String, sticky: String): String {
        val lower = text.lowercase()
        val srHints = listOf(
            "popio", "potrosio", "potrošio", "beleška", "beleska",
            "sutra", "danas", "želim", "zelim", "dinara"
        )
        if (srHints.any { it in lower } || lower.any { it in "čćšžđ" }) return "sr"
        if (text.isBlank()) return sticky
        return "en"
    }
}
