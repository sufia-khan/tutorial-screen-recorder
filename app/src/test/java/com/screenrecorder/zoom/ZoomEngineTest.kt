package com.screenrecorder.zoom

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomEngineTest {

    private val segment = ZoomSegment(
        id = "s1",
        startMs = 2_500,
        endMs = 5_500,
        scale = 2f,
        centerX = 0.5f,
        centerY = 0.5f
    )

    private fun segment(
        id: String,
        startMs: Long,
        endMs: Long,
        scale: Float,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f
    ) = ZoomSegment(id, startMs, endMs, scale, centerX, centerY)

    @Test
    fun `no segments means no zoom at any time`() {
        assertEquals(ZoomEngine.IDENTITY, ZoomEngine.transformAt(emptyList(), 0L))
        assertEquals(ZoomEngine.IDENTITY, ZoomEngine.transformAt(emptyList(), 3_000L))
    }

    @Test
    fun `outside all segments means no zoom`() {
        assertEquals(1f, ZoomEngine.transformAt(listOf(segment), 2_499).scale, 0f)
        assertEquals(1f, ZoomEngine.transformAt(listOf(segment), 5_501).scale, 0f)
    }

    @Test
    fun `segment starts and ends at scale one`() {
        assertEquals(1f, ZoomEngine.transformAt(listOf(segment), 2_500).scale, 0f)
        assertEquals(1f, ZoomEngine.transformAt(listOf(segment), 5_500).scale, 0f)
    }

    @Test
    fun `holds at full scale through the middle`() {
        assertEquals(2f, ZoomEngine.transformAt(listOf(segment), 4_000).scale, 0f)
        assertEquals(2f, ZoomEngine.transformAt(listOf(segment), 3_250).scale, 0f)
        assertEquals(2f, ZoomEngine.transformAt(listOf(segment), 4_750).scale, 0f)
    }

    @Test
    fun `zoom in quarter is eased and continuous at both ends`() {
        assertEquals(1f, ZoomEngine.zoomScaleAt(2f, 0f), 0f)
        assertEquals(2f, ZoomEngine.zoomScaleAt(2f, 0.25f), 0f)
        val before = ZoomEngine.zoomScaleAt(2f, 0.249f)
        val after = ZoomEngine.zoomScaleAt(2f, 0.251f)
        assertEquals(2f, before, 0.001f)
        assertEquals(2f, after, 0f)
    }

    @Test
    fun `zoom out quarter is eased and continuous at both ends`() {
        assertEquals(2f, ZoomEngine.zoomScaleAt(2f, 0.75f), 0f)
        assertEquals(1f, ZoomEngine.zoomScaleAt(2f, 1f), 0f)
        val before = ZoomEngine.zoomScaleAt(2f, 0.749f)
        val after = ZoomEngine.zoomScaleAt(2f, 0.751f)
        assertEquals(2f, before, 0f)
        assertEquals(2f, after, 0.001f)
    }

    @Test
    fun `curve is monotonic increasing then constant then decreasing`() {
        var previous = ZoomEngine.zoomScaleAt(2f, 0f)
        for (i in 1..24) {
            val value = ZoomEngine.zoomScaleAt(2f, i / 100f)
            assert(value >= previous) { "curve dipped while zooming in at t=$i/100" }
            previous = value
        }
        for (i in 25..75 step 5) {
            assertEquals(2f, ZoomEngine.zoomScaleAt(2f, i / 100f), 0f)
        }
        previous = ZoomEngine.zoomScaleAt(2f, 0.75f)
        for (i in 76..100) {
            val value = ZoomEngine.zoomScaleAt(2f, i / 100f)
            assert(value <= previous) { "curve rose while zooming out at t=$i/100" }
            previous = value
        }
    }

    @Test
    fun `later starting segment wins during overlap`() {
        val early = segment("a", startMs = 2_500, endMs = 5_500, scale = 2f)
        val late = segment("b", startMs = 4_000, endMs = 6_500, scale = 3f, centerX = 0.6f)
        val segments = listOf(early, late)

        val onlyEarly = ZoomEngine.transformAt(segments, 3_000)
        assertEquals(1.7407407f, onlyEarly.scale, 0.0001f)
        assertEquals(0.5f, onlyEarly.centerX, 0f)

        val overlap = ZoomEngine.transformAt(segments, 5_000)
        assertEquals(3f, overlap.scale, 0f)
        assertEquals(0.6f, overlap.centerX, 0f)

        val onlyLate = ZoomEngine.transformAt(segments, 6_000)
        assertEquals(2.792f, onlyLate.scale, 0.001f)

        assertEquals(1f, ZoomEngine.transformAt(segments, 7_000).scale, 0f)
    }

    @Test
    fun `same start time resolves to the segment with the later end`() {
        val shorter = segment("a", startMs = 1_000, endMs = 4_000, scale = 2f)
        val longer = segment("b", startMs = 1_000, endMs = 6_000, scale = 3f)
        val result = ZoomEngine.transformAt(listOf(shorter, longer), 3_000)
        assertEquals(3f, result.scale, 0f)
    }

    @Test
    fun `identical segments resolve to the first in the list`() {
        val first = segment("a", startMs = 1_000, endMs = 4_000, scale = 2f, centerX = 0.5f)
        val second = segment("b", startMs = 1_000, endMs = 4_000, scale = 2f, centerX = 0.6f)
        val result = ZoomEngine.transformAt(listOf(first, second), 2_000)
        assertEquals(0.5f, result.centerX, 0f)
    }

    @Test
    fun `degenerate segments never cause zoom or crash`() {
        val zeroLength = segment("z", startMs = 1_000, endMs = 1_000, scale = 3f)
        val reversed = segment("r", startMs = 5_000, endMs = 2_000, scale = 3f)
        assertEquals(1f, ZoomEngine.transformAt(listOf(zeroLength), 1_000).scale, 0f)
        assertEquals(1f, ZoomEngine.transformAt(listOf(reversed), 3_000).scale, 0f)
    }

    @Test
    fun `scale clamps into the one to three range`() {
        assertEquals(3f, ZoomEngine.clampScale(5f), 0f)
        assertEquals(1f, ZoomEngine.clampScale(0.5f), 0f)
        assertEquals(2f, ZoomEngine.clampScale(2f), 0f)
    }

    @Test
    fun `center clamps so the zoomed view never leaves the picture`() {
        val halfCropAtThree = 1f / 6f
        assertEquals(halfCropAtThree, ZoomEngine.clampCenter(0f, 3f), 0.0001f)
        assertEquals(1f - halfCropAtThree, ZoomEngine.clampCenter(1f, 3f), 0.0001f)

        val halfCropAtTwo = 0.25f
        assertEquals(halfCropAtTwo, ZoomEngine.clampCenter(0f, 2f), 0.0001f)
        assertEquals(1f - halfCropAtTwo, ZoomEngine.clampCenter(1f, 2f), 0.0001f)
    }

    @Test
    fun `center inside the valid range stays as placed`() {
        assertEquals(0.5f, ZoomEngine.clampCenter(0.5f, 2f), 0f)
        assertEquals(0.3f, ZoomEngine.clampCenter(0.3f, 2f), 0f)
    }

    @Test
    fun `transform clamps center using the clamped scale`() {
        val extreme = segment("e", startMs = 0, endMs = 3_000, scale = 5f, centerX = 0f, centerY = 1f)
        val result = ZoomEngine.transformAt(listOf(extreme), 1_500)
        assertEquals(3f, result.scale, 0f)
        assertEquals(1f / 6f, result.centerX, 0.0001f)
        assertEquals(1f - 1f / 6f, result.centerY, 0.0001f)
    }

    @Test
    fun `returns to no zoom right after the segment ends`() {
        assertEquals(1f, ZoomEngine.transformAt(listOf(segment), 5_501).scale, 0f)
        assertEquals(0.5f, ZoomEngine.transformAt(listOf(segment), 5_501).centerX, 0f)
        assertEquals(0.5f, ZoomEngine.transformAt(listOf(segment), 5_501).centerY, 0f)
    }

    @Test
    fun `at scale one the only valid center is the middle`() {
        assertEquals(0.5f, ZoomEngine.clampCenter(0f, 1f), 0f)
        assertEquals(0.5f, ZoomEngine.clampCenter(1f, 1f), 0f)
    }
}
