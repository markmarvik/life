package com.life.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * STT layer that feeds Needle. Prefers the on-device recognizer (API 31+);
 * otherwise the system one with EXTRA_PREFER_OFFLINE. Not a cloud LLM.
 *
 * Hold-to-talk: [start] on press, [stop] on release. [stop] only ends the
 * utterance so [onFinal] still fires; [destroy] is for teardown.
 */
class VoiceCapture(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onState: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var lastLocale: Locale = Locale.getDefault()
    private var delivered = false

    fun start(locale: Locale = lastLocale) {
        lastLocale = locale
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Speech not available")
            return
        }
        destroy()
        delivered = false
        val sr = createRecognizer() ?: run {
            onError("Speech not available")
            return
        }
        recognizer = sr
        sr.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        sr.startListening(intent)
    }

    /** End the utterance and wait for [onFinal]. */
    fun stop() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    fun destroy() {
        val sr = recognizer
        recognizer = null
        if (sr != null) {
            try {
                sr.destroy()
            } catch (_: Exception) {
            }
        }
        onState(false)
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } catch (_: Exception) {
                // Offline pack missing — fall through to the system service.
            }
        }
        return try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (_: Exception) {
            null
        }
    }

    private fun finish(text: String) {
        if (delivered) return
        delivered = true
        onState(false)
        if (text.isNotEmpty()) onFinal(text)
        main.post { destroy() }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onState(true)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                finish("")
                return
            }
            onState(false)
            onError("Mic error")
            main.post { destroy() }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            finish(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
