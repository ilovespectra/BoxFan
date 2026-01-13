package com.example.boxfan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

private const val TAG = "BoxFan-MainActivity"

class MainActivity : AppCompatActivity() {
    private var audioService: AudioService? = null
    private lateinit var logFile: File
    
    private fun writeLog(msg: String) {
        Log.d(TAG, msg)
        try {
            if (!::logFile.isInitialized) {
                // Try to write to external files dir first (more accessible)
                logFile = File(getExternalFilesDir(null), "boxfan_debug.log")
                if (logFile.parentFile?.exists() != true) {
                    logFile.parentFile?.mkdirs()
                }
            }
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            FileOutputStream(logFile, true).bufferedWriter().use {
                it.write("[$timestamp] $msg\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to log file", e)
        }
    }
    private var audioServiceBound = false
    private lateinit var playPauseButton: Button
    private lateinit var volumeSlider: SeekBar
    private lateinit var statusText: TextView
    private lateinit var timerDisplay: TextView
    private lateinit var countdownDisplay: TextView
    private lateinit var hourDisplay: TextView
    private lateinit var minuteDisplay: TextView
    private var isPlaying = false
    private var audioFilePath = ""
    private var timerHours = 0
    private var timerMinutes = 0
    private var audioReady = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                Log.d(TAG, "=== onServiceConnected() called ===")
                val binder = service as AudioService.LocalBinder
                audioService = binder.getService()
                audioServiceBound = true
                Log.d(TAG, "✓ AudioService bound successfully")

                // Set up callbacks
                Log.d(TAG, "Setting up callbacks...")
                audioService?.setCallbacks(
                    onPlaybackStateChanged = { playing ->
                        Log.d(TAG, "→ onPlaybackStateChanged callback: playing=$playing")
                        isPlaying = playing
                        playPauseButton.text = if (playing) "⏸" else "▶"
                        Log.d(TAG, "✓ Button updated to: ${playPauseButton.text}")
                    },
                    onStatusChanged = { status ->
                        Log.d(TAG, "→ onStatusChanged callback: $status")
                        statusText.text = status
                    },
                    onTimerCountdown = { remainingSeconds ->
                        updateCountdownDisplay(remainingSeconds)
                    }
                )
                Log.d(TAG, "✓ Callbacks set up")

                // Initialize audio with file path
                if (audioFilePath.isNotEmpty()) {
                    Log.d(TAG, "✓ audioFilePath is not empty: $audioFilePath")
                    statusText.text = "Preparing..."
                    playPauseButton.isEnabled = false
                    
                    Log.d(TAG, "Calling audioService.initializeAudio()...")
                    val success = audioService?.initializeAudio(audioFilePath) { ready ->
                        Log.d(TAG, "=== onAudioReady callback received ===")
                        Log.d(TAG, "  ready=$ready")
                        audioReady = ready
                        playPauseButton.isEnabled = ready
                        statusText.text = if (ready) "✓ Ready" else "❌ Audio failed to prepare"
                        Log.d(TAG, "  Audio ready state: $audioReady")
                        Log.d(TAG, "  Play button enabled: $ready")
                    }
                    Log.d(TAG, "initializeAudio returned: $success")
                    if (success != true) {
                        statusText.text = "❌ Audio init failed"
                        Log.e(TAG, "✗ Audio initialization returned false")
                    }
                } else {
                    statusText.text = "❌ No audio file path"
                    Log.e(TAG, "✗ Audio file path is empty!")
                    playPauseButton.isEnabled = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗✗✗ Exception in serviceConnection.onServiceConnected()", e)
                e.printStackTrace()
                statusText.text = "❌ Service error: ${e.message}"
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected() called")
            audioServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force night mode (dark theme)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        super.onCreate(savedInstanceState)
        Log.d(TAG, "==================== APP START ====================")
        
        try {
            setContentView(R.layout.activity_main)
            initializeViews()
            
            // Load audio asset
            Log.d(TAG, "Loading audio asset...")
            findAudioFile()
            Log.d(TAG, "Audio asset loaded")
            
            // Setup and start service
            setupListeners()
            startAudioService()
            Log.d(TAG, "Service started")
            
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL ERROR in onCreate", e)
            e.printStackTrace()
            throw e
        }
    }

    private fun startAudioService() {
        try {
            Log.d(TAG, "Starting audio service")
            val serviceIntent = Intent(this, AudioService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Audio service started and bound")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio service", e)
            statusText.text = "❌ Service Error: ${e.message}"
        }
    }

    private fun initializeViews() {
        playPauseButton = findViewById(R.id.playPauseButton)
        volumeSlider = findViewById(R.id.volumeSlider)
        statusText = findViewById(R.id.statusText)
        timerDisplay = findViewById(R.id.timerDisplay)
        countdownDisplay = findViewById(R.id.countdownDisplay)
        hourDisplay = findViewById(R.id.hourDisplay)
        minuteDisplay = findViewById(R.id.minuteDisplay)
    }

    private fun findAudioFile() {
        Log.d(TAG, "========== LOADING AUDIO ASSET ==========")
        try {
            // For now, we'll use the extracted file approach which was known to work
            // Create or update the extracted audio file
            val cacheFile = File(cacheDir, "boxfan.mp3")
            Log.d(TAG, "Cache file path: ${cacheFile.absolutePath}")
            Log.d(TAG, "Cache file exists before extraction: ${cacheFile.exists()}")
            
            Log.d(TAG, "Opening asset stream for 'boxfan.mp3'...")
            assets.open("boxfan.mp3").use { input ->
                Log.d(TAG, "✓ Asset stream opened, available bytes: ${input.available()}")
                java.io.FileOutputStream(cacheFile).use { output ->
                    val bytesWritten = input.copyTo(output)
                    Log.d(TAG, "✓ Copied $bytesWritten bytes to cache file")
                }
            }
            
            val finalSize = cacheFile.length()
            Log.d(TAG, "Cache file exists after extraction: ${cacheFile.exists()}")
            Log.d(TAG, "Cache file final size: $finalSize bytes")
            
            if (!cacheFile.exists() || finalSize == 0L) {
                throw Exception("Extraction failed - exists: ${cacheFile.exists()}, size: $finalSize")
            }
            
            Log.d(TAG, "✓ Audio extracted successfully: $finalSize bytes")
            audioFilePath = cacheFile.absolutePath
            Log.d(TAG, "✓ audioFilePath set to: $audioFilePath")
        } catch (e: Exception) {
            Log.e(TAG, "✗✗✗ CRITICAL: Asset loading failed: ${e.message}")
            e.printStackTrace()
            statusText.text = "❌ Asset error: ${e.message}"
        }
    }

    private fun setupListeners() {
        playPauseButton.setOnClickListener { togglePlayPause() }

        volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Update volume whenever slider changes (from user OR system)
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVolume = (maxVolume * progress / 100f).toInt().coerceIn(1, maxVolume)
                
                Log.d(TAG, "Volume slider changed: progress=$progress → targetVolume=$targetVolume/$maxVolume")
                
                // Set the system stream volume - this is the standard Android way
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                Log.d(TAG, "✓ System volume set to ${(progress)}%")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Timer controls
        findViewById<Button>(R.id.hourPlus).setOnClickListener { 
            timerHours = (timerHours + 1) % 24
            updateTimerDisplay()
        }
        findViewById<Button>(R.id.hourMinus).setOnClickListener { 
            timerHours = if (timerHours > 0) timerHours - 1 else 23
            updateTimerDisplay()
        }
        findViewById<Button>(R.id.minutePlus).setOnClickListener { 
            timerMinutes = (timerMinutes + 5) % 60
            updateTimerDisplay()
        }
        findViewById<Button>(R.id.minuteMinus).setOnClickListener { 
            timerMinutes = if (timerMinutes > 0) timerMinutes - 5 else 55
            updateTimerDisplay()
        }
        findViewById<Button>(R.id.timerSetButton).setOnClickListener { 
            setSleepTimer()
        }
    }

    private fun updateTimerDisplay() {
        hourDisplay.text = timerHours.toString()
        minuteDisplay.text = String.format("%02d", timerMinutes)
    }

    private fun updateCountdownDisplay(remainingSeconds: Int) {
        if (remainingSeconds <= 0) {
            countdownDisplay.visibility = android.view.View.GONE
            return
        }

        countdownDisplay.visibility = android.view.View.VISIBLE
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60

        countdownDisplay.text = when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun setSleepTimer() {
        try {
            if (timerHours == 0 && timerMinutes == 0) {
                timerDisplay.text = "Please set a time"
                Log.w(TAG, "⚠ Timer not set - time is 0")
                return
            }
            val totalMinutes = timerHours * 60 + timerMinutes
            Log.d(TAG, "Setting sleep timer: ${timerHours}h ${String.format("%02d", timerMinutes)}m ($totalMinutes total minutes)")
            audioService?.setSleepTimer(totalMinutes)
            timerDisplay.text = "⏱ Timer: ${timerHours}h ${String.format("%02d", timerMinutes)}m"
            Log.d(TAG, "✓ Sleep timer set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error in setSleepTimer: ${e.message}", e)
            timerDisplay.text = "❌ Timer error"
        }
    }

    private fun togglePlayPause() {
        try {
            Log.d(TAG, "togglePlayPause() called, isPlaying=$isPlaying, audioReady=$audioReady")
            
            if (isPlaying) {
                Log.d(TAG, "→ Pausing")
                audioService?.pause()
            } else {
                if (audioFilePath.isEmpty()) {
                    Log.e(TAG, "✗ No audio file!")
                    statusText.text = "❌ Audio not loaded"
                    return
                }
                
                if (!audioReady) {
                    Log.w(TAG, "→ Audio not ready yet, waiting...")
                    statusText.text = "⏳ Audio loading..."
                    // Try again after a short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "→ Retry play after audio ready check")
                        if (audioReady) {
                            audioService?.play()
                        } else {
                            Log.e(TAG, "✗ Audio still not ready after waiting")
                            statusText.text = "❌ Audio failed to load"
                        }
                    }, 500)
                    return
                }
                
                Log.d(TAG, "→ Playing from: $audioFilePath, audioReady=$audioReady")
                statusText.text = "⏳ Starting..."
                Log.d(TAG, "→ About to call audioService?.play()")
                // Call play directly on main thread (service manages background work internally)
                audioService?.play()
                Log.d(TAG, "→ play() returned from service")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ togglePlayPause exception: ${e.message}", e)
            statusText.text = "❌ ${e.message}"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle hardware volume buttons
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            Log.d(TAG, "Volume button pressed: keyCode=$keyCode")
            val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 10 else -10
            volumeSlider.progress = (volumeSlider.progress + delta).coerceIn(0, 100)
            return true
        }
        // Handle back button
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "Back button pressed")
            return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called")
        try {
            if (audioServiceBound) {
                unbindService(serviceConnection)
                audioServiceBound = false
                Log.d(TAG, "✓ Service unbound in onDestroy")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service in onDestroy", e)
        }
        super.onDestroy()
    }

    override fun onStop() {
        Log.d(TAG, "onStop() called - app going to background")
        // Don't stop audio service - let it continue playing in background
        super.onStop()
    }

    override fun onStart() {
        Log.d(TAG, "onStart() called - app coming to foreground")
        super.onStart()
    }

    override fun onResume() {
        Log.d(TAG, "onResume() called")
        super.onResume()
    }

    override fun onPause() {
        Log.d(TAG, "onPause() called - app pausing")
        super.onPause()
    }
}
