package com.screenrecorder.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

fun videoRect(
    viewWidthPx: Float,
    viewHeightPx: Float,
    videoWidthPx: Int,
    videoHeightPx: Int
): Rect? {
    if (viewWidthPx <= 0f || viewHeightPx <= 0f || videoWidthPx <= 0 || videoHeightPx <= 0) {
        return null
    }
    val scale = minOf(viewWidthPx / videoWidthPx, viewHeightPx / videoHeightPx)
    val width = videoWidthPx * scale
    val height = videoHeightPx * scale
    val left = (viewWidthPx - width) / 2f
    val top = (viewHeightPx - height) / 2f
    return Rect(left, top, left + width, top + height)
}

fun toNormalized(tapPosition: Offset, rect: Rect): Offset {
    val x = ((tapPosition.x - rect.left) / rect.width).coerceIn(0f, 1f)
    val y = ((tapPosition.y - rect.top) / rect.height).coerceIn(0f, 1f)
    return Offset(x, y)
}

fun toViewPosition(normalized: Offset, rect: Rect): Offset {
    return Offset(
        x = rect.left + normalized.x * rect.width,
        y = rect.top + normalized.y * rect.height
    )
}
