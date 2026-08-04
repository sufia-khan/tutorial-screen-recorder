package com.screenrecorder.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.screenrecorder.ui.player.toNormalized
import com.screenrecorder.ui.player.toViewPosition
import com.screenrecorder.ui.player.videoRect
import com.screenrecorder.zoom.ZoomSegment
import kotlin.math.roundToInt

private const val MARKER_SIZE_DP = 32

@Composable
fun ZoomFocusOverlay(
    player: Player,
    isPlaying: Boolean,
    selectedSegment: ZoomSegment?,
    onCenterChange: (centerX: Float, centerY: Float, persist: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedSegment == null || isPlaying) return

    var lastNormalized by remember(selectedSegment.id) {
        mutableStateOf(Offset(selectedSegment.centerX, selectedSegment.centerY))
    }

    val markerColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val viewWidthPx = constraints.maxWidth.toFloat()
        val viewHeightPx = constraints.maxHeight.toFloat()
        val videoSize = player.videoSize
        val rect = videoRect(viewWidthPx, viewHeightPx, videoSize.width, videoSize.height)
            ?: Rect(0f, 0f, viewWidthPx, viewHeightPx)

        val markerPosition = toViewPosition(Offset(selectedSegment.centerX, selectedSegment.centerY), rect)
        val markerRadiusPx = MARKER_SIZE_DP / 2f * density.density

        Canvas(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (markerPosition.x - markerRadiusPx).roundToInt(),
                        y = (markerPosition.y - markerRadiusPx).roundToInt()
                    )
                }
                .size(MARKER_SIZE_DP.dp)
        ) {
            drawCircle(
                color = markerColor.copy(alpha = 0.5f),
                radius = size.minDimension / 2f
            )
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 2f - 2.dp.toPx(),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedSegment.id, rect.left, rect.top, rect.width, rect.height) {
                    detectDragGestures(
                        onDragStart = { position ->
                            lastNormalized = toNormalized(position, rect)
                            onCenterChange(lastNormalized.x, lastNormalized.y, false)
                        },
                        onDragEnd = {
                            onCenterChange(lastNormalized.x, lastNormalized.y, true)
                        },
                        onDragCancel = {
                            onCenterChange(lastNormalized.x, lastNormalized.y, true)
                        }
                    ) { change, _ ->
                        change.consume()
                        lastNormalized = toNormalized(change.position, rect)
                        onCenterChange(lastNormalized.x, lastNormalized.y, false)
                    }
                }
        )
    }
}
