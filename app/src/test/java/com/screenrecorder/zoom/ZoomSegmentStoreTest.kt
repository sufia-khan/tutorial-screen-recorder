package com.screenrecorder.zoom

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZoomSegmentStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun videoFile(name: String = "ScreenRecord_20260801_123456.mp4"): File =
        File(tmp.newFolder("videos"), name)

    private fun store(video: File): ZoomSegmentStore = ZoomSegmentStore.forVideo(video)

    private fun segment(
        id: String = "s1",
        startMs: Long = 2_500,
        endMs: Long = 5_500,
        scale: Float = 2f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f
    ) = ZoomSegment(id, startMs, endMs, scale, centerX, centerY)

    @Test
    fun `missing sidecar file loads as empty list`() {
        assertEquals(emptyList<ZoomSegment>(), store(videoFile()).load())
    }

    @Test
    fun `save then load round-trips segments losslessly`() {
        val store = store(videoFile())
        val segments = listOf(
            segment(),
            segment(id = "s2", startMs = 100, endMs = 90_000, scale = 1.5f, centerX = 0.3333f, centerY = 0.6666f),
            segment(id = "s3", startMs = 40_000, endMs = 40_000, scale = 3f, centerX = 0f, centerY = 1f)
        )
        store.save(segments)
        assertEquals(segments, store.load())
    }

    @Test
    fun `saving an empty list writes a valid file that loads as empty`() {
        val store = store(videoFile())
        store.save(emptyList())
        assertTrue(store.sidecarFile.exists())
        assertEquals(emptyList<ZoomSegment>(), store.load())
    }

    @Test
    fun `saved file carries version one`() {
        val store = store(videoFile())
        store.save(listOf(segment()))
        val root = JSONObject(store.sidecarFile.readText())
        assertEquals(1, root.getInt("version"))
    }

    @Test
    fun `corrupt json loads as empty list without crashing`() {
        val store = store(videoFile())
        store.sidecarFile.writeText("{ this is not json at all")
        assertEquals(emptyList<ZoomSegment>(), store.load())
    }

    @Test
    fun `truncated json loads as empty list`() {
        val store = store(videoFile())
        store.sidecarFile.writeText("""{"version":1,"segments":[{"id":"s1"""")
        assertEquals(emptyList<ZoomSegment>(), store.load())
    }

    @Test
    fun `missing segments key loads as empty list`() {
        val store = store(videoFile())
        store.sidecarFile.writeText("""{"version":1}""")
        assertEquals(emptyList<ZoomSegment>(), store.load())
    }

    @Test
    fun `unknown extra fields are tolerated`() {
        val store = store(videoFile())
        store.sidecarFile.writeText(
            """{"version":1,"segments":[{"id":"s1","startMs":100,"endMs":2000,"scale":2.0,"centerX":0.5,"centerY":0.5,"futureField":"hello"}]}"""
        )
        assertEquals(listOf(segment(startMs = 100, endMs = 2_000)), store.load())
    }

    @Test
    fun `malformed segment entry is skipped while valid ones load`() {
        val store = store(videoFile())
        store.sidecarFile.writeText(
            """{"version":1,"segments":[
                {"id":"bad","startMs":100,"endMs":2000},
                {"id":"good","startMs":100,"endMs":2000,"scale":2.0,"centerX":0.5,"centerY":0.5}
            ]}"""
        )
        assertEquals(listOf(segment(id = "good", startMs = 100, endMs = 2_000)), store.load())
    }

    @Test
    fun `sidecar file naming replaces the video extension`() {
        val video = videoFile("ScreenRecord_abc.mp4")
        assertEquals(
            File(video.parentFile, "ScreenRecord_abc.zoom.json"),
            ZoomSegmentStore.sidecarFileFor(video)
        )
    }

    @Test
    fun `sidecar file lands next to the video`() {
        val video = videoFile()
        store(video).save(listOf(segment()))
        assertTrue(File(video.parentFile, "ScreenRecord_20260801_123456.zoom.json").exists())
    }

    @Test
    fun `save creates missing parent directories`() {
        val deepVideo = File(tmp.root, "a/b/c/video.mp4")
        val store = ZoomSegmentStore.forVideo(deepVideo)
        store.save(listOf(segment()))
        assertTrue(store.sidecarFile.exists())
        assertEquals(listOf(segment()), store.load())
    }

    @Test
    fun `repeated saves replace the previous content`() {
        val store = store(videoFile())
        store.save(listOf(segment(id = "first")))
        store.save(listOf(segment(id = "second", startMs = 9_000, endMs = 12_000)))
        assertEquals(listOf(segment(id = "second", startMs = 9_000, endMs = 12_000)), store.load())
    }
}
