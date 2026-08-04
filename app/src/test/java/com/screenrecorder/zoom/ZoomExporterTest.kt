package com.screenrecorder.zoom

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ZoomExporterTest {

    @Test
    fun `output file keeps the folder and appends _zoom before the extension`() {
        val output = ZoomExporter.outputFileFor(
            File("C:/Movies/ScreenRecorder/ScreenRecord_1699999999.mp4")
        )
        assertEquals(
            File("C:/Movies/ScreenRecorder/ScreenRecord_1699999999_zoom.mp4"),
            output
        )
    }

    @Test
    fun `output file for a bare name without folder`() {
        val output = ZoomExporter.outputFileFor(File("ScreenRecord_1.mp4"))
        assertEquals(File("ScreenRecord_1_zoom.mp4"), output)
    }

    @Test
    fun `output file always ends in _zoom mp4 regardless of source extension`() {
        val output = ZoomExporter.outputFileFor(File("clip.mkv"))
        assertEquals(File("clip_zoom.mp4"), output)
    }

    @Test
    fun `output file for a name without extension`() {
        val output = ZoomExporter.outputFileFor(File("clip"))
        assertEquals(File("clip_zoom.mp4"), output)
    }
}
