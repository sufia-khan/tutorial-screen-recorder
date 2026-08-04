package com.screenrecorder.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.screenrecorder.zoom.ZoomSegment

private const val HANDLE_WIDTH_DP = 14
private const val PLAYHEAD_WIDTH_DP = 2

@Composable
fun ZoomTimeline(
    segments: List<ZoomSegment>,
    durationMs: Long,
    positionMs: Long,
    selectedSegmentId: String?,
    onSegmentTap: (String) -> Unit,
    onResize: (id: String, startMs: Long, endMs: Long, persist: Boolean) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekTo((fraction * durationMs).toLong())
                    }
                }
            }
    ) {
        if (durationMs <= 0) return@BoxWithConstraints

        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()

        segments.forEach { segment ->
            val leftPx = segment.startMs / durationMs.toFloat() * widthPx
            val rightPx = segment.endMs / durationMs.toFloat() * widthPx
            val blockWidthPx = (rightPx - leftPx).coerceAtLeast(4f)
            val isSelected = segment.id == selectedSegmentId

            Box(
                modifier = Modifier
                    .offset(x = with(density) { leftPx.toDp() })
                    .width(with(density) { blockWidthPx.toDp() })
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    .pointerInput(segment.id) {
                        detectTapGestures {
                            onSegmentTap(segment.id)
                        }
                    }
            ) {
                ResizeHandle(
                    side = HandleSide.START,
                    segment = segment,
                    widthPx = widthPx,
                    durationMs = durationMs,
                    onResize = onResize,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                ResizeHandle(
                    side = HandleSide.END,
                    segment = segment,
                    widthPx = widthPx,
                    durationMs = durationMs,
                    onResize = onResize,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        val playheadFraction = (positionMs / durationMs.toFloat()).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .offset(x = with(density) { (playheadFraction * widthPx).toDp() })
                .width(PLAYHEAD_WIDTH_DP.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.onSurface)
                .zIndex(1f)
        )
    }
}

private enum class HandleSide { START, END }

@Composable
private fun ResizeHandle(
    side: HandleSide,
    segment: ZoomSegment,
    widthPx: Float,
    durationMs: Long,
    onResize: (id: String, startMs: Long, endMs: Long, persist: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedMs by remember(segment.id, side) { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .width(HANDLE_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(Color.Transparent)
            .pointerInput(segment.id, side, durationMs) {
                detectDragGestures(
                    onDragStart = {
                        draggedMs = if (side == HandleSide.START) segment.startMs else segment.endMs
                    },
                    onDragEnd = {
                        onResize(
                            segment.id,
                            if (side == HandleSide.START) draggedMs else segment.startMs,
                            if (side == HandleSide.END) draggedMs else segment.endMs,
                            true
                        )
                    },
                    onDragCancel = {
                        onResize(
                            segment.id,
                            if (side == HandleSide.START) draggedMs else segment.startMs,
                            if (side == HandleSide.END) draggedMs else segment.endMs,
                            true
                        )
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val msDelta = (dragAmount.x / widthPx * durationMs).toLong()
                    val newValue = (if (side == HandleSide.START) segment.startMs else segment.endMs) + msDelta
                    draggedMs = if (side == HandleSide.START) {
                        newValue.coerceIn(0L, (segment.endMs - ZoomEditorState.MIN_SEGMENT_LENGTH_MS).coerceAtLeast(0L))
                    } else {
                        newValue.coerceAtLeast(segment.startMs + ZoomEditorState.MIN_SEGMENT_LENGTH_MS)
                    }
                    onResize(
                        segment.id,
                        if (side == HandleSide.START) draggedMs else segment.startMs,
                        if (side == HandleSide.END) draggedMs else segment.endMs,
                        false
                    )
                }
            }
    )
}
