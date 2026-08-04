package com.screenrecorder.interaction

import com.screenrecorder.session.model.InteractionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionDedupStoreTest {

    private val bounds = ViewBounds(100, 200, 300, 400)
    private val otherBounds = ViewBounds(500, 600, 700, 800)

    private fun storeAt(time: Long): Pair<InteractionDedupStore, () -> Unit> {
        var current = time
        val advance = { current += 10L }
        return InteractionDedupStore(now = { current }) to advance
    }

    @Test
    fun firstTap_isRecorded() {
        val (store, _) = storeAt(1000)
        assertTrue(store.shouldRecord(InteractionType.TAP, bounds, 0, 0, 1000))
    }

    @Test
    fun duplicateTapWithinWindow_isIgnored() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time)
        time += 50
        assertFalse(store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time))
    }

    @Test
    fun tapAfterWindowElapsed_isRecorded() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time)
        time += 250
        assertTrue(store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time))
    }

    @Test
    fun tapOnDifferentBounds_isRecorded() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time)
        time += 50
        assertTrue(store.shouldRecord(InteractionType.TAP, otherBounds, 0, 0, time))
    }

    @Test
    fun tapAfterLongPressOnSameBounds_isIgnored() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.LONG_PRESS, bounds, 0, 0, time)
        time += 100
        assertFalse(store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time))
    }

    @Test
    fun tapAfterLongPressWindowElapsed_isRecorded() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.LONG_PRESS, bounds, 0, 0, time)
        time += 600
        assertTrue(store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time))
    }

    @Test
    fun duplicateLongPressWithinWindow_isIgnored() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.LONG_PRESS, bounds, 0, 0, time)
        time += 50
        assertFalse(store.shouldRecord(InteractionType.LONG_PRESS, bounds, 0, 0, time))
    }

    @Test
    fun scrollWithinThrottleWindow_isIgnored() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.SCROLL, bounds, 0, 10, time)
        time += 20
        assertFalse(store.shouldRecord(InteractionType.SCROLL, bounds, 0, 8, time))
    }

    @Test
    fun scrollAfterThrottleWindow_isRecorded() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.SCROLL, bounds, 0, 10, time)
        time += 100
        assertTrue(store.shouldRecord(InteractionType.SCROLL, bounds, 0, 8, time))
    }

    @Test
    fun zeroDeltaScroll_isIgnored() {
        val (store, _) = storeAt(1000)
        assertFalse(store.shouldRecord(InteractionType.SCROLL, bounds, 0, 0, 1000))
    }

    @Test
    fun scrollOnDifferentBounds_isRecorded() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.SCROLL, bounds, 0, 10, time)
        time += 20
        assertTrue(store.shouldRecord(InteractionType.SCROLL, otherBounds, 5, 0, time))
    }

    @Test
    fun screenChange_isAlwaysRecorded() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        assertTrue(store.shouldRecord(InteractionType.SCREEN_CHANGE, null, 0, 0, time))
        time += 10
        assertTrue(store.shouldRecord(InteractionType.SCREEN_CHANGE, null, 0, 0, time))
    }

    @Test
    fun tapWithoutBounds_isRecorded() {
        val (store, _) = storeAt(1000)
        assertTrue(store.shouldRecord(InteractionType.TAP, null, 0, 0, 1000))
    }

    @Test
    fun scrollWithoutBounds_isRecorded() {
        val (store, _) = storeAt(1000)
        assertTrue(store.shouldRecord(InteractionType.SCROLL, null, 0, 10, 1000))
    }

    @Test
    fun staleEntriesAreCleared() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time)
        time += 600
        assertTrue(store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time))
    }

    @Test
    fun windowsAreConfigurable() {
        var time = 1000L
        val store = InteractionDedupStore(
            now = { time },
            tapWindowMs = 50,
            longPressSuppressMs = 100,
            scrollThrottleMs = 10
        )
        store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time)
        time += 75
        assertTrue(store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time))
    }

    @Test
    fun clear_resetsAllState() {
        var time = 1000L
        val store = InteractionDedupStore(now = { time })
        store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time)
        time += 50
        store.clear()
        assertTrue(store.shouldRecord(InteractionType.TAP, bounds, 0, 0, time))
    }
}
