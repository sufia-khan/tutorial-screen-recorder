package com.screenrecorder.manager

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingLibraryTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "rec-library-test-$name-${System.nanoTime()}")
        dir.mkdirs()
        tempDirs.add(dir)
        return dir
    }

    private fun file(dir: File, name: String): File {
        val f = File(dir, name)
        f.writeText("bytes")
        return f
    }

    private fun zeroDuration(file: File) = 0L

    private fun list(
        privateDir: File,
        publicDir: File,
        durationOf: (File) -> Long
    ): List<RecordedVideo> =
        RecordingLibrary.list(tempDir("sessions"), privateDir, publicDir, durationOf)

    @Test
    fun onlyMp4Files_listed() {
        val privateDir = tempDir("private")
        file(privateDir, "ScreenRecord_20260801_100000.mp4")
        file(privateDir, "notes.txt")
        file(privateDir, "ScreenRecord_20260801_110000.MP4")
        val publicDir = tempDir("public")

        val result = list(privateDir, publicDir, ::zeroDuration)

        assertEquals(2, result.size)
        assertTrue(result.all { it.path.endsWith(".mp4", ignoreCase = true) })
    }

    @Test
    fun newestFirst_order() {
        val privateDir = tempDir("private")
        file(privateDir, "ScreenRecord_20260801_120000.mp4")
        file(privateDir, "ScreenRecord_20260730_090000.mp4")
        file(privateDir, "ScreenRecord_20260731_230000.mp4")
        val publicDir = tempDir("public")

        val result = list(privateDir, publicDir, ::zeroDuration)

        assertEquals(
            listOf(
                "Recording \u2022 Aug 1, 12:00 PM",
                "Recording \u2022 Jul 31, 11:00 PM",
                "Recording \u2022 Jul 30, 9:00 AM"
            ),
            result.map { it.displayName }
        )
    }

    @Test
    fun friendlyName_parsed_afternoon() {
        val privateDir = tempDir("private")
        file(privateDir, "ScreenRecord_20260801_143045.mp4")
        val publicDir = tempDir("public")

        val result = list(privateDir, publicDir, ::zeroDuration)

        assertEquals("Recording \u2022 Aug 1, 2:30 PM", result.single().displayName)
    }

    @Test
    fun friendlyName_parsed_midnight() {
        val privateDir = tempDir("private")
        file(privateDir, "ScreenRecord_20260801_000005.mp4")
        val publicDir = tempDir("public")

        val result = list(privateDir, publicDir, ::zeroDuration)

        assertEquals("Recording \u2022 Aug 1, 12:00 AM", result.single().displayName)
    }

    @Test
    fun fallback_publicWhenPrivateMissing() {
        val privateDir = tempDir("private")
        val privateFile = file(privateDir, "ScreenRecord_20260801_100000.mp4")
        val publicDir = tempDir("public")
        file(publicDir, "ScreenRecord_20260801_100000.mp4")
        val publicOnly = file(publicDir, "ScreenRecord_20260730_090000.mp4")

        val result = list(privateDir, publicDir, ::zeroDuration)

        assertEquals(2, result.size)
        assertEquals(privateFile.absolutePath, result[0].path)
        assertEquals(publicOnly.absolutePath, result[1].path)
    }

    @Test
    fun duration_passedThrough() {
        val privateDir = tempDir("private")
        val video = file(privateDir, "ScreenRecord_20260801_100000.mp4")
        val publicDir = tempDir("public")

        val result = list(privateDir, publicDir) { if (it == video) 42L else 0L }

        assertEquals(42L, result.single().durationSeconds)
    }

    @Test
    fun nonMatchingName_usesRawNameAndLastModified() {
        val privateDir = tempDir("private")
        val old = file(privateDir, "MyVideo.mp4")
        old.setLastModified(1_000_000L)
        val fresh = file(privateDir, "Weekend.mp4")
        fresh.setLastModified(2_000_000L)
        val publicDir = tempDir("public")

        val result = list(privateDir, publicDir, ::zeroDuration)

        assertEquals(listOf("Weekend", "MyVideo"), result.map { it.displayName })
    }

    @Test
    fun emptyDirs_returnsEmptyList() {
        val privateDir = tempDir("private")
        val publicDir = tempDir("public")

        val result = list(privateDir, publicDir, ::zeroDuration)

        assertTrue(result.isEmpty())
    }
}
