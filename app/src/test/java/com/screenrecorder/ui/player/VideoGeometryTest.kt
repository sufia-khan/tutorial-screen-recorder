package com.screenrecorder.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoGeometryTest {

    @Test
    fun `same aspect ratio fills the whole view`() {
        val rect = videoRect(viewWidthPx = 1080f, viewHeightPx = 1920f, videoWidthPx = 1080, videoHeightPx = 1920)
        assertEquals(Rect(0f, 0f, 1080f, 1920f), rect)
    }

    @Test
    fun `landscape video in portrait view is letterboxed top and bottom`() {
        val rect = videoRect(viewWidthPx = 1080f, viewHeightPx = 1920f, videoWidthPx = 1920, videoHeightPx = 1080)
        assertEquals(Rect(0f, 656.25f, 1080f, 1263.75f), rect)
    }

    @Test
    fun `portrait video in landscape view is letterboxed left and right`() {
        val rect = videoRect(viewWidthPx = 1920f, viewHeightPx = 1080f, videoWidthPx = 1080, videoHeightPx = 1920)
        assertEquals(Rect(656.25f, 0f, 1263.75f, 1080f), rect)
    }

    @Test
    fun `unknown video size yields no rect`() {
        assertNull(videoRect(viewWidthPx = 1080f, viewHeightPx = 1920f, videoWidthPx = 0, videoHeightPx = 0))
    }

    @Test
    fun `tap at the video center maps to normalized center`() {
        val rect = Rect(420f, 0f, 660f, 1920f)
        assertEquals(Offset(0.5f, 0.5f), toNormalized(Offset(540f, 960f), rect))
    }

    @Test
    fun `tap at the video corners maps to the normalized corners`() {
        val rect = Rect(420f, 0f, 660f, 1920f)
        assertEquals(Offset(0f, 0f), toNormalized(Offset(420f, 0f), rect))
        assertEquals(Offset(1f, 1f), toNormalized(Offset(660f, 1920f), rect))
    }

    @Test
    fun `tap on the letterbox bar clamps to the nearest edge`() {
        val rect = Rect(420f, 0f, 660f, 1920f)
        assertEquals(Offset(0f, 0.5f), toNormalized(Offset(0f, 960f), rect))
        assertEquals(Offset(1f, 0.5f), toNormalized(Offset(1080f, 960f), rect))
    }

    @Test
    fun `tap outside the view clamps into range`() {
        val rect = Rect(0f, 0f, 1080f, 1920f)
        assertEquals(Offset(0f, 0f), toNormalized(Offset(-50f, -50f), rect))
        assertEquals(Offset(1f, 1f), toNormalized(Offset(2000f, 5000f), rect))
    }

    @Test
    fun `normalized to view position places the marker inside the video rect`() {
        val rect = Rect(420f, 0f, 660f, 1920f)
        assertEquals(Offset(540f, 960f), toViewPosition(Offset(0.5f, 0.5f), rect))
        assertEquals(Offset(420f, 0f), toViewPosition(Offset(0f, 0f), rect))
        assertEquals(Offset(660f, 1920f), toViewPosition(Offset(1f, 1f), rect))
    }
}
