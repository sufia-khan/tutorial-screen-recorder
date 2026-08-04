package com.screenrecorder.session.model

import kotlinx.serialization.Serializable

@Serializable
data class MetadataFile(
    val videoMetadata: VideoMetadata = VideoMetadata(),
    val recordingMetadata: RecordingMetadata = RecordingMetadata()
)
