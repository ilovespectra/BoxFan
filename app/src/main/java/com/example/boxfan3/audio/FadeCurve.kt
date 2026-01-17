package com.example.boxfan3.audio

/**
 * Mathematical fade curves for smooth audio transitions.
 * Provides various easing functions for professional crossfading.
 */
object FadeCurve {
    /**
     * Linear fade: simple proportional change (f(t) = t)
     */
    fun linear(progress: Float): Float {
        return progress.coerceIn(0f, 1f)
    }

    /**
     * Ease-in-out cubic: smooth acceleration/deceleration
     * Creates natural sounding fade curves
     */
    fun easeInOutCubic(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return if (t < 0.5f) {
            4 * t * t * t
        } else {
            val invT = -2 * t + 2
            1 - (invT * invT * invT / 2)
        }
    }

    /**
     * Ease-in quartic: accelerating fade in
     */
    fun easeInQuartic(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * t * t
    }

    /**
     * Ease-out quartic: decelerating fade out
     */
    fun easeOutQuartic(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        val invT = 1 - t
        return 1 - (invT * invT * invT * invT)
    }
}
