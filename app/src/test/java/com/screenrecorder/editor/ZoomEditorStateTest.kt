package com.screenrecorder.editor

import com.screenrecorder.zoom.ZoomSegment
import com.screenrecorder.zoom.ZoomSegmentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZoomEditorStateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun stateWithStore(): Pair<ZoomEditorState, ZoomSegmentStore> {
        val video = File(tmp.newFolder("videos"), "video.mp4")
        val store = ZoomSegmentStore.forVideo(video)
        return ZoomEditorState(store) to store
    }

    @Test
    fun `state loads existing segments from the store on creation`() {
        val video = File(tmp.newFolder("videos"), "video.mp4")
        val store = ZoomSegmentStore.forVideo(video)
        val saved = ZoomSegment(id = "s1", startMs = 100, endMs = 2_000, scale = 2f, centerX = 0.5f, centerY = 0.5f)
        store.save(listOf(saved))
        val state = ZoomEditorState(store)
        assertEquals(listOf(saved), state.segments)
    }

    @Test
    fun `addSegmentAt places a default segment at the position and persists it`() {
        val (state, store) = stateWithStore()
        state.addSegmentAt(positionMs = 2_500, videoDurationMs = 60_000)
        val added = state.segments.single()
        assertEquals(2_500, added.startMs)
        assertEquals(5_500, added.endMs)
        assertEquals(ZoomEditorState.DEFAULT_SCALE, added.scale, 0f)
        assertEquals(0.5f, added.centerX, 0f)
        assertEquals(0.5f, added.centerY, 0f)
        assertEquals(state.segments, store.load())
    }

    @Test
    fun `addSegmentAt clamps the end to the video duration`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(positionMs = 59_000, videoDurationMs = 60_000)
        assertEquals(60_000, state.segments.single().endMs)
    }

    @Test
    fun `addSegmentAt with unknown duration falls back to three seconds`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(positionMs = 2_500, videoDurationMs = 0)
        assertEquals(5_500, state.segments.single().endMs)
    }

    @Test
    fun `addSegmentAt with negative position clamps to zero`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(positionMs = -500, videoDurationMs = 60_000)
        assertEquals(0, state.segments.single().startMs)
    }

    @Test
    fun `each addSegmentAt generates a unique id`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(0, 60_000)
        state.addSegmentAt(1_000, 60_000)
        assertNotEquals(state.segments[0].id, state.segments[1].id)
    }

    @Test
    fun `deleteSegment removes the segment and persists the change`() {
        val (state, store) = stateWithStore()
        state.addSegmentAt(0, 60_000)
        state.addSegmentAt(10_000, 60_000)
        val removedId = state.segments[0].id
        state.deleteSegment(removedId)
        assertEquals(1, state.segments.size)
        assertTrue(state.segments.none { it.id == removedId })
        assertEquals(state.segments, store.load())
    }

    @Test
    fun `deleteSegment of an unknown id leaves the list unchanged`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(0, 60_000)
        state.deleteSegment("does-not-exist")
        assertEquals(1, state.segments.size)
    }

    @Test
    fun `selectSegment selects and clearSelection deselects`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(0, 60_000)
        val id = state.segments.single().id
        assertEquals(null, state.selectedSegmentId)
        state.selectSegment(id)
        assertEquals(id, state.selectedSegmentId)
        state.clearSelection()
        assertEquals(null, state.selectedSegmentId)
    }

    @Test
    fun `deleting the selected segment clears the selection`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(0, 60_000)
        state.addSegmentAt(10_000, 60_000)
        state.selectSegment(state.segments[0].id)
        state.deleteSegment(state.segments[0].id)
        assertEquals(null, state.selectedSegmentId)
        assertEquals(1, state.segments.size)
    }

    @Test
    fun `updateScale changes the scale, clamps it, and persists`() {
        val (state, store) = stateWithStore()
        state.addSegmentAt(0, 60_000)
        val id = state.segments.single().id
        state.updateScale(id, 2.5f)
        assertEquals(2.5f, state.segments.single().scale, 0f)
        assertEquals(state.segments, store.load())
        state.updateScale(id, 9f)
        assertEquals(3f, state.segments.single().scale, 0f)
        state.updateScale(id, 0.1f)
        assertEquals(1f, state.segments.single().scale, 0f)
    }

    @Test
    fun `updateSegmentTimes resizes the segment and persists`() {
        val (state, store) = stateWithStore()
        state.addSegmentAt(2_500, 60_000)
        val id = state.segments.single().id
        state.updateSegmentTimes(id, startMs = 1_000, endMs = 8_000, videoDurationMs = 60_000)
        assertEquals(1_000, state.segments.single().startMs)
        assertEquals(8_000, state.segments.single().endMs)
        assertEquals(state.segments, store.load())
    }

    @Test
    fun `updateSegmentTimes never lets start pass end and keeps a minimum length`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(2_500, 60_000)
        val id = state.segments.single().id
        state.updateSegmentTimes(id, startMs = 9_000, endMs = 8_000, videoDurationMs = 60_000)
        assertEquals(7_900, state.segments.single().startMs)
        assertEquals(8_000, state.segments.single().endMs)
        state.updateSegmentTimes(id, startMs = 1_000, endMs = 1_010, videoDurationMs = 60_000)
        assertEquals(910, state.segments.single().startMs)
        assertEquals(1_010, state.segments.single().endMs)
    }

    @Test
    fun `updateSegmentTimes clamps into the video duration`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(2_500, 60_000)
        val id = state.segments.single().id
        state.updateSegmentTimes(id, startMs = -500, endMs = 90_000, videoDurationMs = 60_000)
        assertEquals(0, state.segments.single().startMs)
        assertEquals(60_000, state.segments.single().endMs)
    }

    @Test
    fun `updateSegmentTimes with persist false updates memory but not the store`() {
        val (state, store) = stateWithStore()
        state.addSegmentAt(2_500, 60_000)
        val id = state.segments.single().id
        state.updateSegmentTimes(id, startMs = 1_000, endMs = 8_000, videoDurationMs = 60_000, persist = false)
        assertEquals(1_000, state.segments.single().startMs)
        assertEquals(2_500, store.load().single().startMs)
    }

    @Test
    fun `updateSegmentTimes with unknown duration does not clamp the end`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(2_500, 0)
        val id = state.segments.single().id
        state.updateSegmentTimes(id, startMs = 1_000, endMs = 90_000, videoDurationMs = 0)
        assertEquals(1_000, state.segments.single().startMs)
        assertEquals(90_000, state.segments.single().endMs)
    }

    @Test
    fun `updateCenter changes the focus point and persists`() {
        val (state, store) = stateWithStore()
        state.addSegmentAt(0, 60_000)
        val id = state.segments.single().id
        state.updateCenter(id, centerX = 0.8f, centerY = 0.2f)
        assertEquals(0.8f, state.segments.single().centerX, 0f)
        assertEquals(0.2f, state.segments.single().centerY, 0f)
        assertEquals(state.segments, store.load())
    }

    @Test
    fun `updateCenter clamps into the zero to one range`() {
        val (state, _) = stateWithStore()
        state.addSegmentAt(0, 60_000)
        val id = state.segments.single().id
        state.updateCenter(id, centerX = -1f, centerY = 2f)
        assertEquals(0f, state.segments.single().centerX, 0f)
        assertEquals(1f, state.segments.single().centerY, 0f)
    }

    @Test
    fun `updateCenter with persist false updates memory but not the store`() {
        val (state, store) = stateWithStore()
        state.addSegmentAt(0, 60_000)
        val id = state.segments.single().id
        state.updateCenter(id, centerX = 0.3f, centerY = 0.4f, persist = false)
        assertEquals(0.3f, state.segments.single().centerX, 0f)
        assertEquals(0.5f, store.load().single().centerX, 0f)
    }
}
