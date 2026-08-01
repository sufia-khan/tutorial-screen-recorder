package com.screenrecorder.model

import android.os.SystemClock

object RecordingSession {
    @Volatile
    var state: RecordingState = RecordingState.IDLE

    @Volatile
    var recordingStartedAtMs: Long = 0

    @Volatile
    var pausedAccumulatedMs: Long = 0

    @Volatile
    var pauseStartedAtMs: Long = 0

    @Volatile
    var pausedByLock: Boolean = false

    @Volatile
    var deviceLocked: Boolean = false

    @Volatile
    var lastSavedFilePath: String? = null

    @Volatile
    var lastRecordingSuccess: Boolean = false

    fun snapshot(): RecordingSnapshot = RecordingSnapshot(
        state = state,
        elapsedSeconds = elapsedSeconds()
    )

    fun elapsedSeconds(): Int = computeElapsedSeconds(
        nowMs = SystemClock.elapsedRealtime(),
        startedAtMs = recordingStartedAtMs,
        pausedAccumulatedMs = pausedAccumulatedMs,
        pauseStartedAtMs = pauseStartedAtMs
    )

    fun formatElapsed(seconds: Int): String =
        String.format("%02d:%02d", seconds / 60, seconds % 60)

    fun reset() {
        state = RecordingState.IDLE
        recordingStartedAtMs = 0
        pausedAccumulatedMs = 0
        pauseStartedAtMs = 0
        pausedByLock = false
        deviceLocked = false
        lastSavedFilePath = null
        lastRecordingSuccess = false
    }
}

data class RecordingSnapshot(
    val state: RecordingState,
    val elapsedSeconds: Int
)

internal fun computeElapsedSeconds(
    nowMs: Long,
    startedAtMs: Long,
    pausedAccumulatedMs: Long,
    pauseStartedAtMs: Long
): Int {
    val currentPauseMs = if (pauseStartedAtMs > 0) nowMs - pauseStartedAtMs else 0L
    val elapsedMs = nowMs - startedAtMs - pausedAccumulatedMs - currentPauseMs
    return (elapsedMs / 1000).toInt().coerceAtLeast(0)
}

internal fun shouldShowOverlay(
    state: RecordingState,
    pausedByLock: Boolean,
    deviceLocked: Boolean
): Boolean {
    if (deviceLocked) return false
    return when (state) {
        RecordingState.RECORDING -> true
        RecordingState.PAUSED -> !pausedByLock
        else -> false
    }
}
