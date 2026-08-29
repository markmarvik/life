package com.life.app.stt

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * 16 kHz mono PCM16 — the buffer Cactus Whisper (and most on-device STT) wants.
 */
class MicRecorder {
    @Volatile private var recording = false
    private var worker: Thread? = null
    private val chunks = ArrayList<ByteArray>()

    val isRecording: Boolean get() = recording

    @SuppressLint("MissingPermission")
    fun start() {
        if (recording) return
        chunks.clear()
        recording = true
        worker = thread(name = "life-mic") {
            val sampleRate = 16_000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate / 5 * 2)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
            try {
                rec.startRecording()
                val buf = ByteArray(minBuf)
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) chunks.add(buf.copyOf(n))
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }
    }

    fun stop(): ByteArray {
        recording = false
        worker?.join(1_500)
        worker = null
        val total = chunks.sumOf { it.size }
        val out = ByteArray(total)
        var i = 0
        for (c in chunks) {
            System.arraycopy(c, 0, out, i, c.size)
            i += c.size
        }
        chunks.clear()
        return out
    }
}
