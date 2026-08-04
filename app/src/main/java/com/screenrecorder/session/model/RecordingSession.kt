package com.screenrecorder.session.model

import java.io.File
import java.util.UUID

data class RecordingSession(
    val sessionId: UUID,
    val recordingId: UUID,
    val directory: File,
    val videoPath: File,
    val thumbnailPath: File,
    val metadataPath: File,
    val interactionsPath: File,
    val exportsDirectory: File,
    val createdAtMs: Long,
    val status: SessionStatus
) {
    fun update(status: SessionStatus): RecordingSession = copy(status = status)
}
