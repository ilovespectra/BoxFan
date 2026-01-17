package com.example.boxfan3.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for BoxFanViewModel
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BoxFanViewModelTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var viewModel: BoxFanViewModel
    
    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        viewModel = BoxFanViewModel(context)
    }
    
    @Test
    fun testInitialState() {
        val state = viewModel.uiState.value
        assertFalse(state.isPlaying)
        assertEquals(1f, state.volume, 0.01f)
        assertEquals(0, state.timerTotalSeconds)
        assertFalse(state.timerActive)
    }
    
    @Test
    fun testVolumeControl() {
        viewModel.setVolume(0.5f)
        assertEquals(0.5f, viewModel.uiState.value.volume, 0.01f)
        
        // Test clamping
        viewModel.setVolume(1.5f)
        assertEquals(1f, viewModel.uiState.value.volume, 0.01f)
        
        viewModel.setVolume(-0.5f)
        assertEquals(0f, viewModel.uiState.value.volume, 0.01f)
    }
    
    @Test
    fun testTimerDuration() {
        // Set 30 minutes
        viewModel.setTimerDuration(30 * 60)
        assertEquals(30, viewModel.uiState.value.timerMinutes)
        assertEquals(0, viewModel.uiState.value.timerSeconds)
        
        // Set 1 hour 15 minutes
        viewModel.setTimerDuration(75 * 60)
        assertEquals(1, viewModel.uiState.value.timerMinutes / 60)
        assertEquals(15, viewModel.uiState.value.timerMinutes % 60)
    }
    
    @Test
    fun testTimerDurationClamping() {
        // Test minimum (15 minutes)
        viewModel.setTimerDuration(5 * 60)
        assertEquals(15 * 60, viewModel.uiState.value.timerTotalSeconds)
        
        // Test maximum (10 hours)
        viewModel.setTimerDuration(20 * 60 * 60)
        assertEquals(10 * 60 * 60, viewModel.uiState.value.timerTotalSeconds)
    }
    
    @Test
    fun testTimerDisplay() {
        viewModel.setTimerDuration(125) // 2 minutes 5 seconds
        assertEquals("02:05", viewModel.getTimerDisplay())
        
        viewModel.setTimerDuration(3605) // 1 hour 5 seconds
        val display = viewModel.getTimerDisplay()
        assertTrue(display.contains("05"))
    }
}
