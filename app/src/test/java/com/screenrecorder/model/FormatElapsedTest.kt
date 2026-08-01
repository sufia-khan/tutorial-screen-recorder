package com.screenrecorder.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatElapsedTest {

    @Test
    fun `zero seconds formats as zero zero`() {
        assertEquals("00:00", RecordingSession.formatElapsed(0))
    }

    @Test
    fun `sixty five seconds rolls over to minutes`() {
        assertEquals("01:05", RecordingSession.formatElapsed(65))
    }

    @Test
    fun `over an hour keeps running minutes`() {
        assertEquals("62:03", RecordingSession.formatElapsed(3723))
    }
}
