package com.example.boxfan3.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback utilities for user interactions
 * Provides subtle vibrations for touch feedback
 */
object HapticFeedback {
    
    /**
     * Light tap feedback (10ms)
     */
    fun lightTap(context: Context) {
        vibrate(context, 10)
    }
    
    /**
     * Medium feedback (20ms)
     */
    fun mediumFeedback(context: Context) {
        vibrate(context, 20)
    }
    
    /**
     * Strong feedback (30ms)
     */
    fun strongFeedback(context: Context) {
        vibrate(context, 30)
    }
    
    /**
     * Double tap pattern
     */
    fun doubleTap(context: Context) {
        vibratePattern(context, longArrayOf(0, 10, 50, 10))
    }
    
    /**
     * Success pattern (ascending)
     */
    fun successPattern(context: Context) {
        vibratePattern(context, longArrayOf(0, 15, 30, 15, 30, 15))
    }
    
    /**
     * Error pattern (three short taps)
     */
    fun errorPattern(context: Context) {
        vibratePattern(context, longArrayOf(0, 20, 30, 20, 30, 20))
    }
    
    private fun vibrate(context: Context, durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun vibratePattern(context: Context, pattern: LongArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        pattern,
                        intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
