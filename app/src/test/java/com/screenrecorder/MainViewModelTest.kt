package com.screenrecorder

import com.screenrecorder.model.RecorderRuntime
import com.screenrecorder.model.RecordingState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {

    @After
    fun tearDown() {
        RecorderRuntime.reset()
    }

    @Test
    fun `fresh viewmodel with idle session starts idle`() {
        val vm = MainViewModel()
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `fresh viewmodel with active session restores recording`() {
        RecorderRuntime.state = RecordingState.RECORDING
        val vm = MainViewModel()
        assertEquals(RecordingState.RECORDING, vm.state.value)
    }

    @Test
    fun `fresh viewmodel with paused session restores paused`() {
        RecorderRuntime.state = RecordingState.PAUSED
        val vm = MainViewModel()
        assertEquals(RecordingState.PAUSED, vm.state.value)
    }

    @Test
    fun `fresh viewmodel with countdown session starts idle`() {
        RecorderRuntime.state = RecordingState.COUNTDOWN
        val vm = MainViewModel()
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `fresh viewmodel with starting session starts idle`() {
        RecorderRuntime.state = RecordingState.STARTING
        val vm = MainViewModel()
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `onCountdownPreparing moves countdown to starting`() {
        val vm = MainViewModel()
        vm.onMediaProjectionGranted()
        vm.onCountdownPreparing()
        assertEquals(RecordingState.STARTING, vm.state.value)
        assertEquals(RecordingState.STARTING, RecorderRuntime.state)
    }

    @Test
    fun `onCountdownPreparing ignored when not in countdown`() {
        val vm = MainViewModel()
        vm.onCountdownPreparing()
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `onRecordingStarted from starting moves to recording`() {
        val vm = MainViewModel()
        vm.onMediaProjectionGranted()
        vm.onCountdownPreparing()
        vm.onRecordingStarted()
        assertEquals(RecordingState.RECORDING, vm.state.value)
        assertEquals(RecordingState.STARTING, RecorderRuntime.state)
    }

    @Test
    fun `onStartFailed from starting resets to idle`() {
        val vm = MainViewModel()
        vm.onMediaProjectionGranted()
        vm.onCountdownPreparing()
        vm.onStartFailed()
        assertEquals(RecordingState.IDLE, vm.state.value)
        assertEquals(RecordingState.IDLE, RecorderRuntime.state)
    }

    @Test
    fun `onStartFailed ignored when not starting`() {
        val vm = MainViewModel()
        vm.onStartFailed()
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `checkRecordingJustStopped resets when session ends`() {
        val vm = MainViewModel()
        vm.onRecordingStarted()
        RecorderRuntime.state = RecordingState.IDLE
        assertTrue(vm.checkRecordingJustStopped())
        assertEquals(RecordingState.IDLE, vm.state.value)
    }

    @Test
    fun `checkRecordingJustStopped no-op while session active`() {
        val vm = MainViewModel()
        vm.onRecordingStarted()
        RecorderRuntime.state = RecordingState.RECORDING
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
        RecorderRuntime.state = RecordingState.PAUSED
        val vm = MainViewModel()
        RecorderRuntime.state = RecordingState.IDLE
        assertTrue(vm.checkRecordingJustStopped())
        assertEquals(RecordingState.IDLE, vm.state.value)
    }
}
