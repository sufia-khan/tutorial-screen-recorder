package com.screenrecorder.model

object RecordingSession {
    @Volatile
    var state: RecordingState = RecordingState.IDLE

    @Volatile
    var elapsedSeconds: Int = 0

    @Volatile
    var pausedByLock: Boolean = false

    @Volatile
    var lastSavedFilePath: String? = null

    @Volatile
    var lastRecordingSuccess: Boolean = false

    fun reset() {
        state = RecordingState.IDLE
        elapsedSeconds = 0
        pausedByLock = false
        lastSavedFilePath = null
        lastRecordingSuccess = false
    }
}