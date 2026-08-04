package com.screenrecorder.zoom

data class ZoomSegment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val scale: Float,
    val centerX: Float,
    val centerY: Float
)
