package com.screenrecorder.zoom

import java.util.UUID

object AutoZoomGenerator {

    const val DEFAULT_DURATION_MS = 3_000L
    const val DEFAULT_SCALE = 2f
    const val MIN_GAP_MS = 800L
    const val MAX_SEGMENTS = 60
    const val MIN_TAIL_MS = 1_000L

    data class Tap(
        val videoPositionMs: Long,
        val centerX: Float,
        val centerY: Float
    )

    fun generate(
        taps: List<Tap>,
        videoDurationMs: Long,
        durationMs: Long = DEFAULT_DURATION_MS,
        scale: Float = DEFAULT_SCALE,
        minGapMs: Long = MIN_GAP_MS,
        maxSegments: Int = MAX_SEGMENTS
    ): List<ZoomSegment> {
        if (maxSegments <= 0) return emptyList()
        val sorted = taps.sortedBy { it.videoPositionMs }
        val segments = mutableListOf<ZoomSegment>()
        var lastKeptMs = Long.MIN_VALUE
        for (tap in sorted) {
            val start = tap.videoPositionMs.coerceAtLeast(0L)
            if (videoDurationMs > 0 && start >= videoDurationMs - MIN_TAIL_MS) continue
            if (start - lastKeptMs < minGapMs) continue
            val end = if (videoDurationMs > start) {
                (start + durationMs).coerceAtMost(videoDurationMs)
            } else {
                start + durationMs
            }
            segments += ZoomSegment(
                id = UUID.randomUUID().toString(),
                startMs = start,
                endMs = end,
                scale = scale.coerceIn(ZoomEngine.MIN_SCALE, ZoomEngine.MAX_SCALE),
                centerX = tap.centerX.coerceIn(0f, 1f),
                centerY = tap.centerY.coerceIn(0f, 1f)
            )
            lastKeptMs = start
            if (segments.size >= maxSegments) break
        }
        return segments
    }
}
