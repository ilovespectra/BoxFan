package com.example.boxfan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

private const val TAG = "BoxFan-AudioService"

class AudioService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var sleepTimerThread: Thread? = null
    private var isMediaPlayerPrepared = false
    private var audioFilePath: String = ""  // Store for recreating MediaPlayer if needed
    private var hasSetDefaultVolume = false  // Track if we've set initial volume
    private var isTimerRunning = false  // Track if sleep timer is currently running
    private var timerPausedRemaining: Long = 0  // Store remaining time when timer is paused
    private var timerStartTime: Long = 0  // Track when timer started
    private var timerTotalMillis: Long = 0  // Total duration of current timer

    private var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    private var onStatusChanged: ((String) -> Unit)? = null
    private var onTimerCountdown: ((Int) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AudioService onCreate()")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        
        // Diagnose audio output
        Log.d(TAG, "Audio system diagnostics:")
        Log.d(TAG, "  Music volume: ${audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)}")
        Log.d(TAG, "  Music max volume: ${audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}")
        Log.d(TAG, "  Ringer mode: ${audioManager?.ringerMode}")
        Log.d(TAG, "  Speakerphone: ${audioManager?.isSpeakerphoneOn}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private var onAudioReady: ((Boolean) -> Unit)? = null

    fun setCallbacks(
        onPlaybackStateChanged: (Boolean) -> Unit,
        onStatusChanged: (String) -> Unit,
        onTimerCountdown: (Int) -> Unit = { }
    ) {
        this.onPlaybackStateChanged = onPlaybackStateChanged
        this.onStatusChanged = onStatusChanged
        this.onTimerCountdown = onTimerCountdown
    }

    fun initializeAudio(audioPath: String, onReady: ((Boolean) -> Unit)? = null): Boolean {
        Log.d(TAG, "initializeAudio() with path: $audioPath")
        audioFilePath = audioPath  // Store for potential recreation
        onAudioReady = onReady
        isMediaPlayerPrepared = false
        
        try {
            val audioFile = java.io.File(audioPath)
            if (!audioFile.exists()) {
                Log.e(TAG, "✗ File not found: $audioPath")
                onAudioReady?.invoke(false)
                return false
            }
            
            releaseMediaPlayers()
            Log.d(TAG, "Creating MediaPlayer...")
            
            // RUN ALL MEDIAPLAYER SETUP ON BACKGROUND THREAD TO AVOID ANR
            Thread {
                try {
                    mediaPlayer = MediaPlayer()
                    
                    // Set audio attributes for proper volume control
                    mediaPlayer?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    Log.d(TAG, "✓ Audio attributes set")
                    
                    Log.d(TAG, "Calling setDataSource()...")
                    mediaPlayer?.setDataSource(audioPath)
                    Log.d(TAG, "✓ setDataSource() completed")
                    Log.d(TAG, "Audio file size: ${java.io.File(audioPath).length()} bytes")
                    Log.d(TAG, "Audio file name: ${java.io.File(audioPath).name}")
                    Log.d(TAG, "Audio file absolute path: $audioPath")
                    
                    mediaPlayer?.setOnPreparedListener { mp ->
                        Log.d(TAG, "=== ✓ onPreparedListener CALLED ===")
                        Log.d(TAG, "  Duration: ${mp.duration}ms (${mp.duration / 1000}s)")
                        Log.d(TAG, "  isPlaying before setup: ${mp.isPlaying}")
                        
                        // Mark as prepared BEFORE invoking callbacks
                        isMediaPlayerPrepared = true
                        Log.d(TAG, "  isMediaPlayerPrepared set to TRUE")
                        
                        // Setup looping
                        mediaPlayer?.setOnCompletionListener { completionMp ->
                            Log.d(TAG, "=== onCompletionListener triggered ===")
                            if (isPlaying) {
                                Log.d(TAG, "Loop: restarting audio")
                                mediaPlayer?.seekTo(0)
                                mediaPlayer?.start()
                            }
                        }
                        
                        Log.d(TAG, "About to invoke onAudioReady callback...")
                        onAudioReady?.invoke(true)
                        onStatusChanged?.invoke("✓ Ready")
                        Log.d(TAG, "✓ Audio ready for playback - prepared state confirmed")
                    }
                    
                    mediaPlayer?.setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "✗ MediaPlayer error: what=$what extra=$extra")
                        Log.e(TAG, "Error details: $what (extra=$extra)")
                        isMediaPlayerPrepared = false
                        onStatusChanged?.invoke("❌ Error $what")
                        true
                    }
                    
                    Log.d(TAG, "About to call prepareAsync()...")
                    mediaPlayer?.prepareAsync()
                    Log.d(TAG, "✓ prepareAsync() called successfully")
                    Log.d(TAG, "MediaPlayer state: ${mediaPlayer?.let { "created" }}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Exception in init thread: ${e.message}", e)
                    e.printStackTrace()
                    isMediaPlayerPrepared = false
                    onAudioReady?.invoke(false)
                }
            }.start()
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "✗ Init exception: ${e.message}", e)
            isMediaPlayerPrepared = false
            onAudioReady?.invoke(false)
            return false
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
        }
    }

    fun play() {
        Log.d(TAG, "=== play() called ===")
        try {
            if (isPlaying) {
                Log.d(TAG, "✓ Already playing, ignoring")
                return
            }

            Log.d(TAG, "mediaPlayer is null: ${mediaPlayer == null}")
            Log.d(TAG, "isMediaPlayerPrepared: $isMediaPlayerPrepared")
            
            if (mediaPlayer == null) {
                Log.e(TAG, "✗ FAIL: MediaPlayer is null")
                onStatusChanged?.invoke("❌ Audio not ready")
                return
            }
            
            if (!isMediaPlayerPrepared) {
                Log.e(TAG, "✗ FAIL: MediaPlayer not prepared - prepared=$isMediaPlayerPrepared")
                onStatusChanged?.invoke("❌ Audio still loading...")
                return
            }
            
            // Resume timer if it was paused
            if (!isTimerRunning && timerPausedRemaining > 0) {
                Log.d(TAG, "→ Resuming paused timer")
                resumeTimer()
            }
            
            Log.d(TAG, "✓ Checks passed, proceeding with playback...")
            
            // Check audio manager state
            Log.d(TAG, "Audio Manager state:")
            Log.d(TAG, "  Volume music: ${audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)}")
            Log.d(TAG, "  Max volume music: ${audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}")
            Log.d(TAG, "  Ringer mode: ${audioManager?.ringerMode}")
            
            // Check speaker phone mode
            Log.d(TAG, "  Speaker phone on: ${audioManager?.isSpeakerphoneOn}")
            
            // Force speaker output (NOT headset)
            audioManager?.isSpeakerphoneOn = true
            Log.d(TAG, "✓ Forced speaker phone mode ON")
            
            // MUST call startForeground() synchronously first, before any async operations
            // Android requires this to be called within ~5 seconds of startForegroundService()
            try {
                startForeground()
                Log.d(TAG, "✓ startForeground() called synchronously")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error in startForeground: ${e.message}", e)
            }
            
            // Request audio focus (fresh request each time to avoid error -38)
            val focusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
                // Create a new audio focus request (don't reuse old one)
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus change: $focusChange")
                        handleAudioFocusChange(focusChange)
                    }
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                Log.d(TAG, "Requesting audio focus...")
                val result = audioManager?.requestAudioFocus(audioFocusRequest!!) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
                Log.d(TAG, "Audio focus request returned: $result")
                result
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }
            
            Log.d(TAG, "Audio focus result: $focusResult")
            if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.e(TAG, "✗ CRITICAL: Audio focus not granted (result=$focusResult)")
                onStatusChanged?.invoke("❌ Audio focus denied")
                return
            }
            Log.d(TAG, "✓ Audio focus granted")

            // Acquire WakeLock
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BoxFan::audio")
                Log.d(TAG, "✓ WakeLock created")
            }
            wakeLock?.acquire(10 * 60 * 60 * 1000L)
            Log.d(TAG, "✓ WakeLock acquired")

            // Post start to main thread to avoid MediaPlayer thread issues
            handler.post {
                try {
                    Log.d(TAG, "=== In handler.post: Starting playback ===")
                    Log.d(TAG, "Before start - mediaPlayer state: isPlaying=${mediaPlayer?.isPlaying}")
                    Log.d(TAG, "Before start - mediaPlayer duration: ${mediaPlayer?.duration}ms")
                    
                    // Check if mediaPlayer is null or in bad state - if so, reinitialize
                    if (mediaPlayer == null && audioFilePath.isNotEmpty()) {
                        Log.w(TAG, "⚠ MediaPlayer is null - reinitializing from audioPath: $audioFilePath")
                        try {
                            val attrs = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                            mediaPlayer = MediaPlayer().apply {
                                setAudioAttributes(attrs)
                                setDataSource(audioFilePath)
                                setOnPreparedListener {
                                    Log.d(TAG, "✓ Recreated MediaPlayer prepared")
                                    mediaPlayer?.start()
                                }
                                setOnErrorListener { _, what, extra ->
                                    Log.e(TAG, "✗ MediaPlayer error: what=$what, extra=$extra")
                                    false
                                }
                                prepareAsync()
                            }
                            Log.d(TAG, "✓ MediaPlayer recreated and preparing...")
                        } catch (e: Exception) {
                            Log.e(TAG, "✗ Failed to recreate MediaPlayer: ${e.message}", e)
                            onStatusChanged?.invoke("❌ Failed to recreate audio")
                            return@post
                        }
                    } else if (mediaPlayer == null) {
                        Log.e(TAG, "✗ MediaPlayer is null and audioFilePath is empty")
                        onStatusChanged?.invoke("❌ Audio file path lost")
                        return@post
                    }
                    
                    // Control volume via device stream (standard Android approach)
                    // Only set default volume on FIRST play, not on replay
                    if (!hasSetDefaultVolume) {
                        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                        val targetVolume = (maxVolume * 0.5f).toInt().coerceIn(1, maxVolume)
                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                        Log.d(TAG, "✓ Initial volume set to 50% ($targetVolume/$maxVolume)")
                        hasSetDefaultVolume = true
                    } else {
                        Log.d(TAG, "✓ Using current user volume setting for replay")
                    }
                    
                    mediaPlayer?.start()
                    Log.d(TAG, "✓ mediaPlayer.start() method executed")
                    Log.d(TAG, "After start - mediaPlayer.isPlaying: ${mediaPlayer?.isPlaying}")
                    
                    isPlaying = true
                    onPlaybackStateChanged?.invoke(true)
                    onStatusChanged?.invoke("▶ Playing")
                    Log.d(TAG, "✓✓✓ PLAYBACK CONFIRMED STARTED ✓✓✓")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Exception in handler.post start(): ${e.message}", e)
                    e.printStackTrace()
                    onStatusChanged?.invoke("❌ Start error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ play() outer exception: ${e.message}", e)
            e.printStackTrace()
            onStatusChanged?.invoke("❌ Play error: ${e.message}")
        }
    }

    fun pause() {
        Log.d(TAG, "pause() called")
        try {
            mediaPlayer?.pause()
            isPlaying = false
            
            // Pause the timer if it's running (but don't try to interrupt ourselves if we're in the timer thread)
            if (isTimerRunning && sleepTimerThread != Thread.currentThread()) {
                pauseTimer()
            } else if (isTimerRunning) {
                // If we're being called from the timer thread, just mark timer as not running
                isTimerRunning = false
            }
            
            // Release audio focus to reset state for next play (fixes error -38)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
            Log.d(TAG, "✓ Audio focus abandoned")
            
            wakeLock?.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            onPlaybackStateChanged?.invoke(false)
            onStatusChanged?.invoke("⏸ Paused")
            Log.d(TAG, "✓ Paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error in pause(): ${e.message}", e)
        }
    }

    fun setVolume(volume: Float) {
        try {
            val v = volume.coerceIn(0f, 1f)
            val percentage = (v * 100).toInt()
            Log.d(TAG, "setVolume() called with: $percentage%")
            
            // Set MediaPlayer volume directly
            mediaPlayer?.setVolume(v, v)
            Log.d(TAG, "✓ MediaPlayer volume set to $percentage%")
            
            // Also control device stream volume
            try {
                val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                val targetVolume = (maxVolume * v).toInt().coerceIn(1, maxVolume)
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                Log.d(TAG, "✓ Device stream volume set to $percentage% ($targetVolume/$maxVolume)")
            } catch (e: Exception) {
                Log.w(TAG, "⚠ Failed to set device stream volume: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error in setVolume(): ${e.message}")
        }
    }

    fun setSleepTimer(minutes: Int) {
        Log.d(TAG, "setSleepTimer() called with $minutes minutes")
        try {
            if (minutes <= 0) {
                Log.w(TAG, "⚠ Invalid timer minutes: $minutes")
                return
            }
            
            // Start audio playback if not already playing
            if (!isPlaying) {
                Log.d(TAG, "→ Starting audio before timer")
                play()
            }
            
            sleepTimerThread?.interrupt()
            val totalMillis = (minutes * 60 * 1000).toLong()
            isTimerRunning = true
            timerPausedRemaining = 0
            timerTotalMillis = totalMillis
            timerStartTime = System.currentTimeMillis()
            
            sleepTimerThread = Thread {
                try {
                    while (isTimerRunning) {
                        val elapsed = System.currentTimeMillis() - timerStartTime
                        val remainingMillis = timerTotalMillis - elapsed
                        
                        if (remainingMillis <= 0) {
                            handler.post {
                                isTimerRunning = false
                                pause()
                                onStatusChanged?.invoke("⏰ Timer done!")
                                onTimerCountdown?.invoke(0)
                                Log.d(TAG, "✓ Sleep timer completed and audio paused")
                            }
                            break
                        }
                        
                        val remainingSeconds = (remainingMillis / 1000).toInt()
                        handler.post {
                            onTimerCountdown?.invoke(remainingSeconds)
                        }
                        
                        Thread.sleep(1000) // Update every second
                    }
                } catch (e: InterruptedException) {
                    // Timer was cancelled/paused - save remaining time
                    val elapsed = System.currentTimeMillis() - timerStartTime
                    timerPausedRemaining = timerTotalMillis - elapsed
                    Log.d(TAG, "Sleep timer paused with ${timerPausedRemaining / 1000}s remaining")
                    handler.post {
                        onTimerCountdown?.invoke((timerPausedRemaining / 1000).toInt())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in sleep timer thread: ${e.message}", e)
                    isTimerRunning = false
                }
            }.apply {
                isDaemon = true
            }
            sleepTimerThread?.start()
            Log.d(TAG, "✓ Sleep timer started: $minutes minutes")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error in setSleepTimer: ${e.message}", e)
        }
    }

    fun pauseTimer() {
        Log.d(TAG, "pauseTimer() called")
        try {
            if (isTimerRunning) {
                val elapsed = System.currentTimeMillis() - timerStartTime
                timerPausedRemaining = timerTotalMillis - elapsed
                sleepTimerThread?.interrupt()
                isTimerRunning = false
                Log.d(TAG, "✓ Timer paused with ${timerPausedRemaining / 1000}s remaining")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing timer: ${e.message}", e)
        }
    }

    fun resumeTimer() {
        Log.d(TAG, "resumeTimer() called")
        try {
            if (!isTimerRunning && timerPausedRemaining > 0) {
                // Resume the remaining time - use internal timer setup to avoid recursively calling play()
                val minutes = ((timerPausedRemaining + 999) / 1000 / 60).toInt()  // Round up
                Log.d(TAG, "→ Resuming timer with $minutes minutes remaining")
                
                sleepTimerThread?.interrupt()
                val totalMillis = (minutes * 60 * 1000).toLong()
                isTimerRunning = true
                timerPausedRemaining = 0
                timerTotalMillis = totalMillis
                timerStartTime = System.currentTimeMillis()
                
                sleepTimerThread = Thread {
                    try {
                        while (isTimerRunning) {
                            val elapsed = System.currentTimeMillis() - timerStartTime
                            val remainingMillis = timerTotalMillis - elapsed
                            
                            if (remainingMillis <= 0) {
                                handler.post {
                                    isTimerRunning = false
                                    pause()
                                    onStatusChanged?.invoke("⏰ Timer done!")
                                    onTimerCountdown?.invoke(0)
                                    Log.d(TAG, "✓ Sleep timer completed and audio paused")
                                }
                                break
                            }
                            
                            val remainingSeconds = (remainingMillis / 1000).toInt()
                            handler.post {
                                onTimerCountdown?.invoke(remainingSeconds)
                            }
                            
                            Thread.sleep(1000) // Update every second
                        }
                    } catch (e: InterruptedException) {
                        val elapsed = System.currentTimeMillis() - timerStartTime
                        timerPausedRemaining = timerTotalMillis - elapsed
                        Log.d(TAG, "Sleep timer paused with ${timerPausedRemaining / 1000}s remaining")
                        handler.post {
                            onTimerCountdown?.invoke((timerPausedRemaining / 1000).toInt())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in sleep timer thread: ${e.message}", e)
                        isTimerRunning = false
                    }
                }.apply {
                    isDaemon = true
                }
                sleepTimerThread?.start()
                Log.d(TAG, "✓ Sleep timer resumed: $minutes minutes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming timer: ${e.message}", e)
        }
    }

    fun cancelSleepTimer() {
        sleepTimerThread?.interrupt()
        sleepTimerThread = null
    }

    private fun startForeground() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BoxFan - White Noise")
            .setContentText("Playing sleep sounds...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BoxFan Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Notifications for BoxFan audio playback"
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun releaseMediaPlayers() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            isMediaPlayerPrepared = false
        } catch (e: Exception) {
            Log.e(TAG, "Release error", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called")
        try {
            sleepTimerThread?.interrupt()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
            
            wakeLock?.release()
            releaseMediaPlayers()
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "onDestroy() completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "boxfan_audio_channel"
        private const val NOTIFICATION_ID = 1
    }
}
