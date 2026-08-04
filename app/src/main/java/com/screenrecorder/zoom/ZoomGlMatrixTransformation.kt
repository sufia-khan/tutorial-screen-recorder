package com.screenrecorder.zoom

import androidx.media3.effect.GlMatrixTransformation

/**
 * Maps each input frame through the same zoom curve the live preview shows: the crop region
 * centered at the engine's clamped focus point, with size 1/scale of the frame, is scaled up to
 * fill the whole output frame. The matrix operates on normalized device coordinates (-1..1).
 */
class ZoomGlMatrixTransformation(
    private val segments: List<ZoomSegment>
) : GlMatrixTransformation {

    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray {
        val transform = ZoomEngine.transformAt(segments, presentationTimeUs / 1000L)
        val scale = transform.scale
        val centerNdcX = 2f * transform.centerX - 1f
        val centerNdcY = 1f - 2f * transform.centerY
        return floatArrayOf(
            scale, 0f, 0f, 0f,
            0f, scale, 0f, 0f,
            0f, 0f, 1f, 0f,
            -scale * centerNdcX, -scale * centerNdcY, 0f, 1f
        )
    }
}
