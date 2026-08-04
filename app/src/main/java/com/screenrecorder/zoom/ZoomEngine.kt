package com.screenrecorder.zoom

object ZoomEngine {

    const val MIN_SCALE = 1.0f
    const val MAX_SCALE = 3.0f

    private const val ZOOM_IN_FRACTION = 0.25f
    private const val ZOOM_OUT_FRACTION = 0.25f

    val IDENTITY = ZoomTransform(MIN_SCALE, 0.5f, 0.5f)

    fun transformAt(segments: List<ZoomSegment>, timeMs: Long): ZoomTransform {
        val segment = activeSegmentAt(segments, timeMs) ?: return IDENTITY
        val scale = clampScale(zoomScaleAt(segment, timeMs))
        return ZoomTransform(
            scale = scale,
            centerX = clampCenter(segment.centerX, scale),
            centerY = clampCenter(segment.centerY, scale)
        )
    }

    fun zoomScaleAt(segment: ZoomSegment, timeMs: Long): Float {
        val duration = segment.endMs - segment.startMs
        if (duration <= 0) return MIN_SCALE
        val t = ((timeMs - segment.startMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        return zoomScaleAt(segment.scale, t)
    }

    fun zoomScaleAt(scale: Float, t: Float): Float {
        val clampedT = t.coerceIn(0f, 1f)
        return when {
            clampedT < ZOOM_IN_FRACTION ->
                1f + (scale - 1f) * smoothStep(clampedT / ZOOM_IN_FRACTION)
            clampedT <= 1f - ZOOM_OUT_FRACTION -> scale
            else ->
                scale + (1f - scale) * smoothStep((clampedT - (1f - ZOOM_OUT_FRACTION)) / ZOOM_OUT_FRACTION)
        }
    }

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    fun clampCenter(center: Float, scale: Float): Float {
        val clampedScale = clampScale(scale)
        val halfCrop = 1f / (2f * clampedScale)
        return center.coerceIn(halfCrop, 1f - halfCrop)
    }

    private fun activeSegmentAt(segments: List<ZoomSegment>, timeMs: Long): ZoomSegment? =
        segments
            .filter { it.endMs > it.startMs && it.startMs <= timeMs && timeMs <= it.endMs }
            .maxWithOrNull(compareBy(ZoomSegment::startMs, ZoomSegment::endMs))

    private fun smoothStep(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)
    }
}
