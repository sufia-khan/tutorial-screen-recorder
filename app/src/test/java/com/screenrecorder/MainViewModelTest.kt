package com.screenrecorder

import com.screenrecorder.model.RecordingSession
import com.screenrecorder.model.RecordingState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {

    @After
    fun tearDown() {
        RecordingSession.reset()
    }

    @Test
    fun `fresh viewmodel with idle session starts idle`() {
        val vm = MainViewModel()
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `fresh viewmodel with active session restores recording`() {
        RecordingSession.state = RecordingState.RECORDING
        val vm = MainViewModel()
        assertEquals(RecordingState.RECORDING, vm.state.value)
    }

    @Test
    fun `fresh viewmodel with paused session restores paused`() {
        RecordingSession.state = RecordingState.PAUSED
        val vm = MainViewModel()
        assertEquals(RecordingState.PAUSED, vm.state.value)
    }

    @Test
    fun `fresh viewmodel with countdown session starts idle`() {
        RecordingSession.state = RecordingState.COUNTDOWN
        val vm = MainViewModel()
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `checkRecordingJustStopped resets when session ends`() {
        val vm = MainViewModel()
        vm.onRecordingStarted()
        RecordingSession.state = RecordingState.IDLE
        assertTrue(vm.checkRecordingJustStopped())
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `checkRecordingJustStopped no-op while session active`() {
        val vm = MainViewModel()
        vm.onRecordingStarted()
        assertFalse(vm.checkRecordingJustStopped())
        assertEquals(RecordingState.RECORDING, vm.state.value)
    }

    @Test
    fun `checkRecordingJustStopped no-op when already idle`() {
        val vm = MainViewModel()
        assertFalse(vm.checkRecordingJustStopped())
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `checkRecordingJustStopped resets after reopen and stop`() {
        RecordingSession.state = RecordingState.PAUSED
        val vm = MainViewModel()
        RecordingSession.state = RecordingState.IDLE
        assertTrue(vm.checkRecordingJustStopped())
        assertEquals(RecordingState.IDLE, vm.state.value)
    }
}
