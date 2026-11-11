package com.apexludo.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Contextual haptic manager. Every impulse is short (10-90 ms) so it feels crisp
 * rather than buzzy, and it is fully switchable off.
 */
class Haptics(ctx: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (t: Throwable) {
        null
    }

    var enabled: Boolean = true

    private fun buzz(ms: Long, amp: Int) {
        if (!enabled) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (t: Throwable) {
            // Haptics are a nicety: never let them take the game down.
        }
    }

    fun tap() = buzz(12, 60)
    fun roll() = buzz(18, 110)
    fun land() = buzz(26, 150)
    fun move() = buzz(10, 45)
    fun capture() = buzz(40, 220)
    fun home() = buzz(30, 170)
    fun win() = buzz(90, 255)
}
