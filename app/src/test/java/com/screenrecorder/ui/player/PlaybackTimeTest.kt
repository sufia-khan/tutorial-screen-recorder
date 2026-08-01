package com.screenrecorder.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeTest {

    @Test
    fun `zero formats as zero zero`() {
        assertEquals("00:00", formatPlaybackTime(0))
    }

    @Test
    fun `one minute and one second`() {
        assertEquals("01:01", formatPlaybackTime(61_000))
    }

    @Test
    fun `sub-second value rounds down`() {
        assertEquals("00:59", formatPlaybackTime(59_999))
    }

    @Test
    fun `over an hour keeps running minutes`() {
        assertEquals("60:00", formatPlaybackTime(3_600_000))
    }

    @Test
    fun `long duration keeps running minutes`() {
        assertEquals("62:03", formatPlaybackTime(3_723_000))
    }

    @Test
    fun `negative value clamps to zero`() {
        assertEquals("00:00", formatPlaybackTime(-5_000))
    }
}
