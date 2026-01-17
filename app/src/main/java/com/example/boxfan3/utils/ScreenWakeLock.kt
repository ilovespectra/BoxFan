package com.example.boxfan3.utils

import android.content.Context
import android.os.PowerManager

/**
 * Screen wake lock management for long-running playback
 * Keeps screen on during sleep timer if desired
 */
object ScreenWakeLock {
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    /**
     * Acquire wake lock to keep screen on
     * @param context Android context
     * @param tag Debug tag for wake lock
     * @param timeoutMs Timeout in milliseconds (0 = manual release)
     */
    fun acquireWakeLock(
        context: Context,
        tag: String = "BoxFan::WakeLock",
        timeoutMs: Long = 0
    ) {
        try {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                    tag
                )
                wakeLock?.setReferenceCounted(false)
            }
            
            if (wakeLock?.isHeld == false) {
                if (timeoutMs > 0) {
                    wakeLock?.acquire(timeoutMs)
                } else {
                    wakeLock?.acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Release wake lock to allow screen off
     */
    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Check if wake lock is currently held
     */
    fun isHeld(): Boolean {
        return try {
            wakeLock?.isHeld ?: false
        } catch (e: Exception) {
            false
        }
    }
}
