package com.pixelmagic.modules.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class PixelHapticEngine(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun click() {
        playPredefined(VibrationEffect.EFFECT_CLICK)
    }

    fun heavyClick() {
        playPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    fun tick() {
        playPredefined(VibrationEffect.EFFECT_TICK)
    }

    fun mechanicalRatchet() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null) {
            try {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.4f, 20)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 40)
                    .compose()
                vibrator.vibrate(effect)
                return
            } catch (_: Exception) {}
        }
        click()
    }

    private fun playPredefined(effectId: Int) {
        if (vibrator?.hasVibrator() == true) {
            try {
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            } catch (_: Exception) {}
        }
    }
}
