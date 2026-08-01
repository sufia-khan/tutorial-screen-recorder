package com.screenrecorder.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldShowOverlayTest {

    @Test
    fun `locked device never shows overlay while recording`() {
        assertFalse(shouldShowOverlay(RecordingState.RECORDING, pausedByLock = false, deviceLocked = true))
    }

    @Test
    fun `locked device never shows overlay while manually paused`() {
        assertFalse(shouldShowOverlay(RecordingState.PAUSED, pausedByLock = false, deviceLocked = true))
    }

    @Test
    fun `locked device never shows overlay while lock paused`() {
        assertFalse(shouldShowOverlay(RecordingState.PAUSED, pausedByLock = true, deviceLocked = true))
    }

    @Test
    fun `unlocked device shows overlay while recording`() {
        assertTrue(shouldShowOverlay(RecordingState.RECORDING, pausedByLock = false, deviceLocked = false))
    }

    @Test
    fun `unlocked device shows overlay while manually paused`() {
        assertTrue(shouldShowOverlay(RecordingState.PAUSED, pausedByLock = false, deviceLocked = false))
    }

    @Test
    fun `unlocked device hides overlay while lock paused`() {
        assertFalse(shouldShowOverlay(RecordingState.PAUSED, pausedByLock = true, deviceLocked = false))
    }

    @Test
    fun `idle never shows overlay`() {
        assertFalse(shouldShowOverlay(RecordingState.IDLE, pausedByLock = false, deviceLocked = false))
    }

    @Test
    fun `countdown never shows overlay`() {
        assertFalse(shouldShowOverlay(RecordingState.COUNTDOWN, pausedByLock = false, deviceLocked = false))
    }
}
