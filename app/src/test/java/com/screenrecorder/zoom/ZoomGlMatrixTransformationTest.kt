package com.screenrecorder.zoom

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomGlMatrixTransformationTest {

    private val transformation = ZoomGlMatrixTransformation(emptyList())

    private fun ZoomSegment.times(startMs: Long, endMs: Long): ZoomSegment = copy(startMs = startMs, endMs = endMs)

    @Test
    fun `no segments produces the identity matrix`() {
        assertMatrix(
            matrix = ZoomGlMatrixTransformation(emptyList()).getGlMatrixArray(5_000_000L),
            scale = 1f,
            translateX = 0f,
            translateY = 0f
        )
    }

    @Test
    fun `hold phase maps the crop around the focus point`() {
        val segments = listOf(
            ZoomSegment("a", 0L, 4000L, 2f, 0.75f, 0.25f)
        )
        val matrix = ZoomGlMatrixTransformation(segments).getGlMatrixArray(2_000_000L)
        assertMatrix(matrix, scale = 2f, translateX = -1f, translateY = -1f)
    }

    @Test
    fun `clamped center cannot push the crop out of the frame`() {
        val segments = listOf(
            ZoomSegment("a", 0L, 4000L, 2f, 0.1f, 0.9f)
        )
        val matrix = ZoomGlMatrixTransformation(segments).getGlMatrixArray(2_000_000L)
        assertMatrix(matrix, scale = 2f, translateX = 1f, translateY = 1f)
    }

    @Test
    fun `ease-in phase scales with smoothstep at half the ease duration`() {
        val segments = listOf(
            ZoomSegment("a", 0L, 4000L, 2f, 0.5f, 0.5f)
        )
        val matrix = ZoomGlMatrixTransformation(segments).getGlMatrixArray(500_000L)
        assertMatrix(matrix, scale = 1.5f, translateX = 0f, translateY = 0f)
    }

    @Test
    fun `before and after the segment the matrix is identity`() {
        val segments = listOf(
            ZoomSegment("a", 1000L, 4000L, 2f, 0.75f, 0.25f)
        )
        val before = ZoomGlMatrixTransformation(segments).getGlMatrixArray(999_000L)
        val after = ZoomGlMatrixTransformation(segments).getGlMatrixArray(4_001_000L)
        assertMatrix(before, scale = 1f, translateX = 0f, translateY = 0f)
        assertMatrix(after, scale = 1f, translateX = 0f, translateY = 0f)
    }

    @Test
    fun `later-start overlapping segment wins`() {
        val segments = listOf(
            ZoomSegment("first", 0L, 3000L, 1.5f, 0.5f, 0.5f),
            ZoomSegment("later", 1000L, 3000L, 2.5f, 0.5f, 0.5f)
        )
        val matrix = ZoomGlMatrixTransformation(segments).getGlMatrixArray(2_000_000L)
        assertMatrix(matrix, scale = 2.5f, translateX = 0f, translateY = 0f)
    }

    private fun assertMatrix(matrix: FloatArray, scale: Float, translateX: Float, translateY: Float) {
        assertEquals("scale x", scale, matrix[0], 0.0001f)
        assertEquals("scale y", scale, matrix[5], 0.0001f)
        assertEquals("z axis", 1f, matrix[10], 0.0001f)
        assertEquals("translate x", translateX, matrix[12], 0.0001f)
        assertEquals("translate y", translateY, matrix[13], 0.0001f)
        assertEquals("homogeneous", 1f, matrix[15], 0.0001f)
        for (i in 0..15) {
            when (i) {
                0, 5, 10, 12, 13, 15 -> Unit
                else -> assertEquals("unexpected non-zero entry at $i", 0f, matrix[i], 0.0001f)
            }
        }
    }
}
