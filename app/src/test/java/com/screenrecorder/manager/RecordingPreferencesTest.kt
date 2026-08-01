package com.screenrecorder.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingPreferencesTest {

    @Test
    fun rawValueZero_returnsFalse() {
        assertFalse(RecordingPreferences.isShowTouchesEnabled(0))
    }

    @Test
    fun rawValueOne_returnsTrue() {
        assertTrue(RecordingPreferences.isShowTouchesEnabled(1))
    }

    @Test
    fun rawValueNegativeOne_returnsFalse() {
        assertFalse(RecordingPreferences.isShowTouchesEnabled(-1))
    }

    @Test
    fun rawValueUnexpected_returnsFalse() {
        assertFalse(RecordingPreferences.isShowTouchesEnabled(999))
    }

    @Test
    fun defaultOverlaySize_passesThrough() {
        assertEquals(40, RecordingPreferences.clampOverlaySize(40))
    }

    @Test
    fun overlaySizeBelowRange_clampsToMinimum() {
        assertEquals(30, RecordingPreferences.clampOverlaySize(10))
    }

    @Test
    fun overlaySizeAboveRange_clampsToMaximum() {
        assertEquals(60, RecordingPreferences.clampOverlaySize(100))
    }
}
