package com.example.boxfan3.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * SeamlessAudioPlayer: Professional audio looping with crossfading.
 *
 * Uses 2 MediaPlayer instances for zero-gap looping with fixed-duration timers.
 * Track duration is known in advance, so we schedule the crossfade to begin
 * N milliseconds before the track ends, allowing smooth overlap.
 *
 * Features:
 * - 4-second configurable crossfade duration
 * - Mathematical fade curves (ease-in-out cubic)
 * - Fixed-duration timer scheduling (no position polling)
 * - Volume synchronization during transitions
 * - Automatic resource cleanup
 */
class SeamlessAudioPlayer(
    private val context: Context,
    private val resourceId: Int,
    private val crossfadeDurationMs: Int = 4000 // 4 seconds default
) {
    private var player1: MediaPlayer? = null
    private var player2: MediaPlayer? = null
    private var activePlayer: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private var trackDurationMs = 0L // Cached track duration
    
    private var isPlaying = false
    private var isCrossfading = false
    private var currentVolume = 1f
    
    private val handler = Handler(Looper.getMainLooper())
    private val audioScope = CoroutineScope(Dispatchers.Main)
    
    // Timer-based scheduling
    private var crossfadeStartTime = 0L
    private var crossfadeRunnable: Runnable? = null
    private var loopSchedulerRunnable: Runnable? = null
    
    init {
        initializePlayers()
    }
    
    private fun initializePlayers() {
        try {
            player1 = createMediaPlayer()
            player2 = createMediaPlayer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun createMediaPlayer(): MediaPlayer {
        return MediaPlayer.create(context, resourceId).apply {
            // Cache duration when player is first created
            if (trackDurationMs == 0L) {
                trackDurationMs = duration.toLong()
            }
        }
    }
    
    /**
     * Start playback with fade-in effect
     */
    fun play() {
        if (isPlaying) return
        
        try {
            activePlayer = player1
            activePlayer?.let { player ->
                player.start()
                isPlaying = true
                animateFadeIn(player)
                scheduleNextCrossfade() // Schedule crossfade based on track duration
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Pause playback with fade-out effect
     */
    fun pause() {
        if (!isPlaying) return
        
        audioScope.launch {
            animateFadeOut(activePlayer) {
                try {
                    player1?.pause()
                    player2?.pause()
                    isPlaying = false
                    currentVolume = 1f
                    setPlayerVolume(player1, 1f)
                    setPlayerVolume(player2, 1f)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Stop playback and reset to beginning
     */
    fun stop() {
        isPlaying = false
        isCrossfading = false
        
        handler.removeCallbacks(crossfadeRunnable ?: Runnable {})
        handler.removeCallbacks(loopSchedulerRunnable ?: Runnable {})
        
        try {
            player1?.apply {
                pause()
                seekTo(0)
            }
            player2?.apply {
                pause()
                seekTo(0)
            }
            currentVolume = 1f
            setPlayerVolume(player1, 1f)
            setPlayerVolume(player2, 1f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Schedule the next crossfade to occur (trackDuration - crossfadeDuration) milliseconds from now.
     * This allows tracks to overlap smoothly without gaps.
     * 
     * We start a bit EARLIER (500ms before the math would suggest) to ensure the next track
     * is already playing when the crossfade begins, preventing any audio dropouts.
     */
    private fun scheduleNextCrossfade() {
        if (trackDurationMs <= 0L) return
        
        handler.removeCallbacks(loopSchedulerRunnable ?: Runnable {})
        
        // Calculate delay: start crossfade N ms before track ends
        // Add a 500ms buffer to ensure next player is already running
        val delayMs = trackDurationMs - crossfadeDurationMs.toLong() - 500
        
        loopSchedulerRunnable = Runnable {
            if (isPlaying && !isCrossfading) {
                initiateCrossfade()
            }
        }
        
        handler.postDelayed(loopSchedulerRunnable!!, delayMs)
    }
    
    /**
     * Schedule the next crossfade after a crossfade completes.
     * The active player has already been playing for ~crossfadeDurationMs,
     * so we need to account for that in the delay calculation.
     */
    private fun scheduleNextCrossfadeAfterComplete() {
        if (trackDurationMs <= 0L) return
        
        handler.removeCallbacks(loopSchedulerRunnable ?: Runnable {})
        
        // The active player is now at position ~crossfadeDurationMs
        // We want the next crossfade to start at position (trackDurationMs - crossfadeDurationMs)
        // So remaining delay is: (trackDurationMs - crossfadeDurationMs) - crossfadeDurationMs - buffer
        // Which simplifies to: trackDurationMs - 2*crossfadeDurationMs - buffer
        val delayMs = trackDurationMs - (2 * crossfadeDurationMs.toLong()) - 500
        
        loopSchedulerRunnable = Runnable {
            if (isPlaying && !isCrossfading) {
                initiateCrossfade()
            }
        }
        
        handler.postDelayed(loopSchedulerRunnable!!, delayMs)
    }
    
    /**
     * Initiate the crossfade sequence.
     * Starts the next player and begins fade animation.
     */
    private fun initiateCrossfade() {
        if (!isPlaying || isCrossfading) return
        
        nextPlayer = if (activePlayer == player1) player2 else player1
        
        try {
            // Reset the next player to the beginning for seamless looping
            nextPlayer?.apply {
                seekTo(0)
                start()
            }
            
            isCrossfading = true
            startCrossfade()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Performs mathematical crossfade between active and next player.
     * Uses equal-power crossfading to maintain consistent volume throughout the fade.
     */
    private fun startCrossfade() {
        crossfadeStartTime = System.currentTimeMillis()
        
        crossfadeRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - crossfadeStartTime
                val progress = (elapsed.toFloat() / crossfadeDurationMs).coerceIn(0f, 1f)
                
                // Apply easing function for natural-sounding fade
                val easeProgress = FadeCurve.easeInOutCubic(progress)
                
                try {
                    // Equal-power crossfade: uses sqrt curves to maintain perceived volume
                    // This prevents the volume dip that occurs in linear crossfades
                    val outGain = kotlin.math.sqrt(1f - easeProgress)
                    val inGain = kotlin.math.sqrt(easeProgress)
                    
                    // Fade out current player
                    activePlayer?.let { 
                        val outVolume = currentVolume * outGain
                        setPlayerVolume(it, outVolume)
                    }
                    
                    // Fade in next player
                    nextPlayer?.let { 
                        val inVolume = currentVolume * inGain
                        setPlayerVolume(it, inVolume)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                if (progress < 1f) {
                    handler.postDelayed(this, 16) // ~60fps update
                } else {
                    onCrossfadeComplete()
                }
            }
        }
        
        handler.post(crossfadeRunnable!!)
    }
    
    /**
     * Called when crossfade animation completes.
     * Swaps active and next player pointers and schedules the next crossfade.
     */
    private fun onCrossfadeComplete() {
        try {
            // Stop the previous active player
            activePlayer?.pause()
            activePlayer?.seekTo(0)
            
            // Make the next player the new active player
            activePlayer = nextPlayer
            activePlayer?.let { setPlayerVolume(it, currentVolume) }
            
            isCrossfading = false
            nextPlayer = null
            
            // Schedule the next crossfade cycle
            // This must account for the fact that the new active player
            // has already been playing for ~crossfadeDurationMs
            if (isPlaying) {
                scheduleNextCrossfadeAfterComplete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Animate fade-in effect when starting playback
     */
    private fun animateFadeIn(player: MediaPlayer) {
        val startTime = System.currentTimeMillis()
        val fadeInDurationMs = 1500 // 1.5 seconds fade in
        
        val fadeRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / fadeInDurationMs).coerceIn(0f, 1f)
                val easeProgress = FadeCurve.easeInQuartic(progress)
                
                try {
                    currentVolume = easeProgress
                    setPlayerVolume(player, easeProgress)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                if (progress < 1f) {
                    handler.postDelayed(this, 16)
                } else {
                    currentVolume = 1f
                    setPlayerVolume(player, 1f)
                }
            }
        }
        
        handler.post(fadeRunnable)
    }
    
    /**
     * Animate fade-out effect with completion callback
     */
    private fun animateFadeOut(player: MediaPlayer?, onComplete: () -> Unit) {
        if (player == null) {
            onComplete()
            return
        }
        
        val startTime = System.currentTimeMillis()
        val fadeOutDurationMs = 1500 // 1.5 seconds fade out
        
        val fadeRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / fadeOutDurationMs).coerceIn(0f, 1f)
                val easeProgress = FadeCurve.easeOutQuartic(progress)
                
                try {
                    currentVolume = 1f - easeProgress
                    setPlayerVolume(player, currentVolume)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                if (progress < 1f) {
                    handler.postDelayed(this, 16)
                } else {
                    onComplete()
                }
            }
        }
        
        handler.post(fadeRunnable)
    }
    
    /**
     * Set volume for a specific player
     */
    private fun setPlayerVolume(player: MediaPlayer?, volume: Float) {
        if (player == null) return
        try {
            val clampedVolume = volume.coerceIn(0f, 1f)
            player.setVolume(clampedVolume, clampedVolume)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Set master volume for all playback
     */
    fun setMasterVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            if (!isCrossfading) {
                setPlayerVolume(activePlayer, currentVolume)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Get current playback state
     */
    fun getIsPlaying(): Boolean = isPlaying
    
    /**
     * Get current volume level (0.0 - 1.0)
     */
    fun getCurrentVolume(): Float = currentVolume
    
    /**
     * Release all resources
     */
    fun release() {
        handler.removeCallbacks(crossfadeRunnable ?: Runnable {})
        handler.removeCallbacks(loopSchedulerRunnable ?: Runnable {})
        
        try {
            player1?.release()
            player2?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        player1 = null
        player2 = null
        activePlayer = null
        nextPlayer = null
        isPlaying = false
    }
}
