package com.example.boxfan3.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.boxfan3.R
import com.example.boxfan3.audio.SeamlessAudioPlayer
import com.example.boxfan3.utils.HapticFeedback
import com.example.boxfan3.utils.ScreenWakeLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the BoxFan audio player
 */
data class BoxFanUiState(
    val isPlaying: Boolean = false,
    val volume: Float = 1f,
    val timerMinutes: Int = 0,
    val timerSeconds: Int = 0,
    val timerTotalSeconds: Int = 0,
    val timerActive: Boolean = false,
    val keepScreenOn: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for BoxFan audio player.
 * Manages audio playback, sleep timer, and state persistence.
 */
class BoxFanViewModel(application: Application) : AndroidViewModel(application) {
    
    private var audioPlayer: SeamlessAudioPlayer? = null
    
    private val _uiState = MutableStateFlow(BoxFanUiState())
    val uiState: StateFlow<BoxFanUiState> = _uiState.asStateFlow()
    
    // Timer management
    private var timerJob: kotlinx.coroutines.Job? = null
    private var timerRemainingMs: Long = 0
    
    companion object {
        private const val PREFS_NAME = "boxfan_prefs"
        private const val PREF_LAST_TIMER = "last_timer_seconds"
        private const val PREF_VOLUME = "last_volume"
        private const val PREF_KEEP_SCREEN_ON = "keep_screen_on"
        private const val PREF_VERSION = "prefs_version"
        private const val CURRENT_PREFS_VERSION = 2 // Bump when changing defaults
        
        private const val MIN_TIMER_SECONDS = 15 * 60 // 15 minutes
        private const val MAX_TIMER_SECONDS = 10 * 60 * 60 // 10 hours
    }
    
    init {
        initializeAudioPlayer()
        restoreState()
    }
    
    /**
     * Initialize the seamless audio player
     */
    private fun initializeAudioPlayer() {
        try {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            audioPlayer = SeamlessAudioPlayer(
                context = getApplication(),
                resourceId = R.raw.boxfan,
                crossfadeDurationMs = 4000 // 4-second crossfade
            )
            
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to initialize audio: ${e.message}"
            )
        }
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        try {
            audioPlayer?.let { player ->
                if (player.getIsPlaying()) {
                    player.pause()
                    HapticFeedback.lightTap(getApplication())
                    stopTimer()
                    if (_uiState.value.keepScreenOn) {
                        ScreenWakeLock.releaseWakeLock()
                    }
                } else {
                    player.play()
                    HapticFeedback.mediumFeedback(getApplication())
                    startTimerIfSet()
                    if (_uiState.value.keepScreenOn) {
                        ScreenWakeLock.acquireWakeLock(getApplication())
                    }
                }
                _uiState.value = _uiState.value.copy(isPlaying = player.getIsPlaying())
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Playback error: ${e.message}"
            )
        }
    }
    
    /**
     * Set master volume (0.0 - 1.0)
     */
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        audioPlayer?.setMasterVolume(clampedVolume)
        _uiState.value = _uiState.value.copy(volume = clampedVolume)
        
        HapticFeedback.lightTap(getApplication())
        saveState()
    }
    
    /**
     * Set sleep timer duration in seconds
     * Valid range: 0 (no timer) or 15 minutes (900s) to 10 hours (36000s)
     * If seconds is 0, timer is disabled. Otherwise minimum is 15 minutes.
     */
    fun setTimerDuration(seconds: Int) {
        val clampedSeconds = if (seconds == 0) {
            0 // Allow no timer
        } else {
            seconds.coerceIn(MIN_TIMER_SECONDS, MAX_TIMER_SECONDS)
        }
        
        val minutes = if (clampedSeconds > 0) clampedSeconds / 60 else 0
        val secs = if (clampedSeconds > 0) clampedSeconds % 60 else 0
        
        _uiState.value = _uiState.value.copy(
            timerMinutes = minutes,
            timerSeconds = secs,
            timerTotalSeconds = clampedSeconds
        )
        
        if (seconds != 0) {
            HapticFeedback.successPattern(getApplication())
        }
        saveState()
    }
    
    /**
     * Toggle keep-screen-on feature
     */
    fun toggleKeepScreenOn() {
        val newValue = !_uiState.value.keepScreenOn
        _uiState.value = _uiState.value.copy(keepScreenOn = newValue)
        
        if (newValue && _uiState.value.isPlaying) {
            ScreenWakeLock.acquireWakeLock(getApplication())
        } else {
            ScreenWakeLock.releaseWakeLock()
        }
        
        HapticFeedback.lightTap(getApplication())
        saveState()
    }
    
    /**
     * Start the sleep timer
     */
    fun startTimer() {
        if (_uiState.value.timerTotalSeconds <= 0) return
        if (_uiState.value.timerActive) return // Already running
        
        timerRemainingMs = _uiState.value.timerTotalSeconds.toLong() * 1000
        _uiState.value = _uiState.value.copy(timerActive = true)
        
        HapticFeedback.doubleTap(getApplication())
        
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (timerRemainingMs > 0 && _uiState.value.timerActive) {
                delay(1000) // Update every second
                timerRemainingMs -= 1000
                
                val remainingSeconds = (timerRemainingMs / 1000).toInt()
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                
                _uiState.value = _uiState.value.copy(
                    timerMinutes = minutes,
                    timerSeconds = seconds
                )
            }
            
            // Timer completed
            if (timerRemainingMs <= 0) {
                stopPlaybackForTimer()
            }
        }
    }
    
    /**
     * Stop the sleep timer
     */
    fun stopTimer() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(timerActive = false)
    }
    
    /**
     * Resume timer if it was running
     */
    private fun startTimerIfSet() {
        if (_uiState.value.timerTotalSeconds > 0 && !_uiState.value.timerActive) {
            startTimer()
        }
    }
    
    /**
     * Stop playback when timer completes
     */
    private fun stopPlaybackForTimer() {
        try {
            audioPlayer?.pause()
            HapticFeedback.successPattern(getApplication())
            ScreenWakeLock.releaseWakeLock()
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                timerActive = false,
                timerMinutes = 0,
                timerSeconds = 0
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Timer stop error: ${e.message}"
            )
        }
    }
    
    /**
     * Get formatted timer display (MM:SS or H:MM:SS)
     */
    fun getTimerDisplay(): String {
        val state = _uiState.value
        return String.format("%02d:%02d", state.timerMinutes, state.timerSeconds)
    }
    
    /**
     * Save state to preferences
     */
    private fun saveState() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            prefs.edit().apply {
                putInt(PREF_LAST_TIMER, _uiState.value.timerTotalSeconds)
                putFloat(PREF_VOLUME, _uiState.value.volume)
                putBoolean(PREF_KEEP_SCREEN_ON, _uiState.value.keepScreenOn)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Restore state from preferences
     * Includes migration logic for preference format changes
     */
    private fun restoreState() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            
            // Check if preferences need migration
            val prefsVersion = prefs.getInt(PREF_VERSION, 1)
            if (prefsVersion < CURRENT_PREFS_VERSION) {
                // Migration: clear old default timer (15 min) to enforce no timer default
                val savedTimer = prefs.getInt(PREF_LAST_TIMER, 0)
                // If timer was the old default, reset to 0
                if (savedTimer == 15 * 60) {
                    prefs.edit().putInt(PREF_LAST_TIMER, 0).apply()
                }
                // Update version
                prefs.edit().putInt(PREF_VERSION, CURRENT_PREFS_VERSION).apply()
            }
            
            val savedTimer = prefs.getInt(PREF_LAST_TIMER, 0) // Default: no timer set
            val savedVolume = prefs.getFloat(PREF_VOLUME, 1f)
            val savedKeepScreenOn = prefs.getBoolean(PREF_KEEP_SCREEN_ON, false)
            
            setTimerDuration(savedTimer)
            setVolume(savedVolume)
            _uiState.value = _uiState.value.copy(keepScreenOn = savedKeepScreenOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        audioPlayer?.release()
        ScreenWakeLock.releaseWakeLock()
    }
}
