package com.screenrecorder.session.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoMetadata(
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Int? = null,
    val bitrate: Int? = null,
    val codec: String? = null,
    val mimeType: String? = null,
    val fileSizeBytes: Long = 0,
    val orientationDegrees: Int = 0,
    val videoFilePath: String = ""
)
