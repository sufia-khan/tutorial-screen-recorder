package com.screenrecorder.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.screenrecorder.zoom.ZoomEngine
import com.screenrecorder.zoom.ZoomSegment
import com.screenrecorder.zoom.ZoomSegmentStore
import java.util.UUID

class ZoomEditorState(private val store: ZoomSegmentStore) {

    var segments by mutableStateOf(store.load())
        private set

    var selectedSegmentId by mutableStateOf<String?>(null)
        private set

    fun addSegmentAt(positionMs: Long, videoDurationMs: Long) {
        addSegment(
            ZoomSegment(
                id = UUID.randomUUID().toString(),
                startMs = positionMs.coerceAtLeast(0L),
                endMs = endFor(positionMs, videoDurationMs),
                scale = DEFAULT_SCALE,
                centerX = 0.5f,
                centerY = 0.5f
            )
        )
    }

    fun addSegment(segment: ZoomSegment) {
        segments = segments + segment
        persist()
    }

    fun deleteSegment(id: String) {
        segments = segments.filterNot { it.id == id }
        if (selectedSegmentId == id) {
            selectedSegmentId = null
        }
        persist()
    }

    fun selectSegment(id: String) {
        selectedSegmentId = id
    }

    fun clearSelection() {
        selectedSegmentId = null
    }

    fun updateScale(id: String, scale: Float) {
        segments = segments.map {
            if (it.id == id) it.copy(scale = scale.coerceIn(ZoomEngine.MIN_SCALE, ZoomEngine.MAX_SCALE)) else it
        }
        persist()
    }

    fun updateSegmentTimes(
        id: String,
        startMs: Long,
        endMs: Long,
        videoDurationMs: Long,
        persist: Boolean = true
    ) {
        segments = segments.map { segment ->
            if (segment.id != id) {
                segment
            } else if (videoDurationMs > 0 && videoDurationMs <= MIN_SEGMENT_LENGTH_MS) {
                segment
            } else {
                val durationLimit = if (videoDurationMs > 0) {
                    videoDurationMs - MIN_SEGMENT_LENGTH_MS
                } else {
                    Long.MAX_VALUE
                }
                val endLimit = (endMs - MIN_SEGMENT_LENGTH_MS).coerceAtLeast(0L)
                val start = startMs.coerceIn(0L, minOf(durationLimit, endLimit))
                val end = if (videoDurationMs > 0) {
                    endMs.coerceIn(start + MIN_SEGMENT_LENGTH_MS, videoDurationMs)
                } else {
                    endMs.coerceAtLeast(start + MIN_SEGMENT_LENGTH_MS)
                }
                segment.copy(startMs = start, endMs = end)
            }
        }
        if (persist) {
            persist()
        }
    }

    fun updateCenter(id: String, centerX: Float, centerY: Float, persist: Boolean = true) {
        segments = segments.map {
            if (it.id == id) {
                it.copy(
                    centerX = centerX.coerceIn(0f, 1f),
                    centerY = centerY.coerceIn(0f, 1f)
                )
            } else {
                it
            }
        }
        if (persist) {
            persist()
        }
    }

    private fun persist() {
        store.save(segments)
    }

    private fun endFor(positionMs: Long, videoDurationMs: Long): Long {
        val start = positionMs.coerceAtLeast(0L)
        return if (videoDurationMs > start) {
            (start + DEFAULT_DURATION_MS).coerceAtMost(videoDurationMs)
        } else {
            start + DEFAULT_DURATION_MS
        }
    }

    companion object {
        const val DEFAULT_DURATION_MS = 3_000L
        const val DEFAULT_SCALE = 2f
        const val MIN_SEGMENT_LENGTH_MS = 100L
    }
}
