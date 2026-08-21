package com.life.app.util

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

object LifeHaptics {
    enum class Kind { TICK, SUCCESS }

    fun play(view: View, kind: Kind) {
        val vibrator = vibrator(view)
        if (vibrator != null && vibrator.hasVibrator()) {
            val effect = when (kind) {
                Kind.TICK -> VibrationEffect.createOneShot(12, 40)
                Kind.SUCCESS -> if (Build.VERSION.SDK_INT >= 29) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                } else {
                    VibrationEffect.createOneShot(24, 80)
                }
            }
            vibrator.vibrate(effect)
            return
        }
        val fallback = when (kind) {
            Kind.TICK -> HapticFeedbackConstants.KEYBOARD_TAP
            Kind.SUCCESS -> if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        }
        view.performHapticFeedback(fallback)
    }

    private fun vibrator(view: View): Vibrator? {
        val ctx = view.context
        return if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        }
    }
}

class HapticPerformer(private val view: View) {
    fun tick() = LifeHaptics.play(view, LifeHaptics.Kind.TICK)
    fun success() = LifeHaptics.play(view, LifeHaptics.Kind.SUCCESS)
}

@Composable
fun rememberHaptics(): HapticPerformer {
    val view = LocalView.current
    return remember(view) { HapticPerformer(view) }
}
