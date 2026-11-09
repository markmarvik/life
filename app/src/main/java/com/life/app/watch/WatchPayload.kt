package com.life.app.watch

import java.util.Base64

object WatchPayload {
    const val MAX_BODY_LENGTH = (1024 * 1024 / 2 / 4) * 3
    const val COMMAND_SEND = "nodomain.freeyourgadget.gadgetbridge.command.SEND_WATCH_APP_MESSAGE"
    const val ACTION_RECEIVE = "nodomain.freeyourgadget.gadgetbridge.action.WATCH_APP_MESSAGE"
    const val EXTRA_DEVICE = "device"
    const val EXTRA_APP_ID = "appId"
    const val EXTRA_PAYLOAD = "payload"
    const val EXTRA_REPLY_TO = "replyTo"

    fun canonicalAppId(raw: String?): String? {
        if (raw.isNullOrEmpty() || raw.length > 10) return null
        for (c in raw) {
            if (c !in '0'..'9') return null
        }
        val value = raw.toLongOrNull() ?: return null
        if (value > 0xffffffffL) return null
        return value.toString()
    }

    fun encode(body: ByteArray): String? {
        if (body.size > MAX_BODY_LENGTH) return null
        return Base64.getEncoder().encodeToString(body)
    }

    fun decode(base64: String?): ByteArray? {
        if (!isCanonicalBase64(base64)) return null
        return try {
            val body = Base64.getDecoder().decode(base64)
            if (body.size > MAX_BODY_LENGTH) null else body
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun isCanonicalBase64(value: String?): Boolean {
        if (value == null || (value.length and 3) != 0) return false
        var padding = 0
        for (c in value) {
            if (c == '=') {
                padding++
                if (padding > 2) return false
            } else if (padding > 0 || !isBase64Char(c)) {
                return false
            }
        }
        return true
    }

    private fun isBase64Char(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/'
}
