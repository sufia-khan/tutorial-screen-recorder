package com.screenrecorder

import androidx.lifecycle.ViewModel
import com.screenrecorder.model.RecorderRuntime
import com.screenrecorder.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Screen { HOME, EDIT, SETTINGS, TRASH }

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    init {
        val sessionState = RecorderRuntime.state
        if (sessionState == RecordingState.RECORDING || sessionState == RecordingState.PAUSED) {
            _state.value = sessionState
        }
    }

    fun navigateTo(screen: Screen) { _screen.value = screen }

    fun openSettings() { _screen.value = Screen.SETTINGS }
    fun closeSettings() { _screen.value = Screen.HOME }

    fun closeTrash() { _screen.value = Screen.HOME }

    fun onMediaProjectionGranted() {
        _state.value = RecordingState.COUNTDOWN
        RecorderRuntime.state = RecordingState.COUNTDOWN
        RecorderRuntime.deviceLocked = false
    }

    fun onCountdownCancelled() {
        if (_state.value != RecordingState.COUNTDOWN) return
        _state.value = RecordingState.IDLE
        RecorderRuntime.state = RecordingState.IDLE
    }

    fun onCountdownPreparing() {
        if (_state.value != RecordingState.COUNTDOWN) return
        _state.value = RecordingState.STARTING
        RecorderRuntime.state = RecordingState.STARTING
    }

    fun onStartFailed() {
        if (_state.value != RecordingState.STARTING) return
        _state.value = RecordingState.IDLE
        RecorderRuntime.state = RecordingState.IDLE
    }

    fun onMediaProjectionDenied() {
        _state.value = RecordingState.IDLE
        RecorderRuntime.state = RecordingState.IDLE
    }

    fun onRecordingStarted() {
        _state.value = RecordingState.RECORDING
        RecorderRuntime.state = RecordingState.RECORDING
    }

    fun onRecordingPaused() {
        _state.value = RecordingState.PAUSED
    }

    fun onRecordingResumed() {
        _state.value = RecordingState.RECORDING
    }

    fun checkRecordingJustStopped(): Boolean {
        val wasActive = _state.value == RecordingState.RECORDING ||
            _state.value == RecordingState.PAUSED
        if (wasActive && RecorderRuntime.state == RecordingState.IDLE) {
            _state.value = RecordingState.IDLE
            return true
        }
        return false
    }

    fun onRecordingStopped() {
        if (_state.value == RecordingState.RECORDING || _state.value == RecordingState.PAUSED) {
            _state.value = RecordingState.IDLE
            RecorderRuntime.state = RecordingState.IDLE
        }
    }
}
