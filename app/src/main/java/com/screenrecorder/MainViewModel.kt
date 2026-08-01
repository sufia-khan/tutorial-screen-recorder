package com.screenrecorder

import androidx.lifecycle.ViewModel
import com.screenrecorder.model.RecordingSession
import com.screenrecorder.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Screen { HOME, SETTINGS }

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    init {
        val sessionState = RecordingSession.state
        if (sessionState == RecordingState.RECORDING || sessionState == RecordingState.PAUSED) {
            _state.value = sessionState
        }
    }

    fun openSettings() { _screen.value = Screen.SETTINGS }
    fun closeSettings() { _screen.value = Screen.HOME }

    fun onMediaProjectionGranted() {
        _state.value = RecordingState.COUNTDOWN
        RecordingSession.state = RecordingState.COUNTDOWN
    }

    fun onMediaProjectionDenied() {
        _state.value = RecordingState.IDLE
        RecordingSession.state = RecordingState.IDLE
    }

    fun onRecordingStarted() {
        _state.value = RecordingState.RECORDING
        RecordingSession.state = RecordingState.RECORDING
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
        if (wasActive && RecordingSession.state == RecordingState.IDLE) {
            _state.value = RecordingState.IDLE
            return true
        }
        return false
    }
}
