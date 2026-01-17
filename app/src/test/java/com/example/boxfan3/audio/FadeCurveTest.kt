package com.example.boxfan3.audio

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * Unit tests for FadeCurve mathematical functions
 */
class FadeCurveTest {
    
    @Test
    fun testLinearFade() {
        // Linear should be simple proportional
        assertEquals(0f, FadeCurve.linear(0f), 0.001f)
        assertEquals(0.5f, FadeCurve.linear(0.5f), 0.001f)
        assertEquals(1f, FadeCurve.linear(1f), 0.001f)
    }
    
    @Test
    fun testLinearClampingBehavior() {
        // Values outside 0-1 range should be clamped
        assertEquals(0f, FadeCurve.linear(-0.5f), 0.001f)
        assertEquals(1f, FadeCurve.linear(1.5f), 0.001f)
    }
    
    @Test
    fun testEaseInOutCubic() {
        // Should start at 0 and end at 1
        assertEquals(0f, FadeCurve.easeInOutCubic(0f), 0.001f)
        assertEquals(1f, FadeCurve.easeInOutCubic(1f), 0.001f)
        
        // Should be symmetric around 0.5
        val progress25 = FadeCurve.easeInOutCubic(0.25f)
        val progress75 = FadeCurve.easeInOutCubic(0.75f)
        assertEquals(progress25, 1f - progress75, 0.001f)
    }
    
    @Test
    fun testEaseInOutCubicMiddlePoint() {
        // At 0.5, easeInOutCubic should be close to 0.5
        val midpoint = FadeCurve.easeInOutCubic(0.5f)
        assertEquals(0.5f, midpoint, 0.01f)
    }
    
    @Test
    fun testEaseInQuartic() {
        // Quartic ease-in: starts slow, accelerates
        assertEquals(0f, FadeCurve.easeInQuartic(0f), 0.001f)
        assertEquals(1f, FadeCurve.easeInQuartic(1f), 0.001f)
        
        // Should be less than linear at 0.5
        val easeValue = FadeCurve.easeInQuartic(0.5f)
        assertTrue(easeValue < 0.5f)
    }
    
    @Test
    fun testEaseOutQuartic() {
        // Quartic ease-out: starts fast, decelerates
        assertEquals(0f, FadeCurve.easeOutQuartic(0f), 0.001f)
        assertEquals(1f, FadeCurve.easeOutQuartic(1f), 0.001f)
        
        // Should be more than linear at 0.5
        val easeValue = FadeCurve.easeOutQuartic(0.5f)
        assertTrue(easeValue > 0.5f)
    }
    
    @Test
    fun testCrossfadeSymmetry() {
        // When mixing easeIn and easeOut, they should create symmetric fade
        val easeInValue = FadeCurve.easeInQuartic(0.3f)
        val easeOutValue = FadeCurve.easeOutQuartic(0.7f)
        
        // easeIn(0.3) should approximate easeOut(0.7)
        assertEquals(easeInValue, easeOutValue, 0.01f)
    }
}
