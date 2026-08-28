package com.life.app.needle

/**
 * JNI to Needle 2 (`liblife_needle.so` → `libneedle.a`).
 * Load is optional: unit tests and x86 builds run without the native engine.
 */
internal object NeedleJni {
    @Volatile
    var loaded: Boolean = false
        private set

    fun tryLoad(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("life_needle")
            loaded = true
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    external fun nativeInit(systemPrompt: String, toolsJson: String, toolIndexPath: String?): Int

    external fun nativeComplete(input: String, maxNewTokens: Int): String?

    external fun nativeReset()
}
