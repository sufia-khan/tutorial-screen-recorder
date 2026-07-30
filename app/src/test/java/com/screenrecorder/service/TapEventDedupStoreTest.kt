package com.screenrecorder.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapEventDedupStoreTest {

    private val bounds = ViewBounds(100, 200, 300, 400)
    private val otherBounds = ViewBounds(500, 600, 700, 800)

    @Test
    fun clickWithNoPriorInterception_isReported() {
        val store = TapEventDedupStore()
        assertTrue(store.shouldReportClick(bounds))
    }

    @Test
    fun duplicateClickWithinWindow_isIgnored() {
        var time = 1000L
        val store = TapEventDedupStore(dedupWindowMs = 200L, now = { time })

        store.reportTouchInterception(bounds)
        time += 50

        assertFalse(store.shouldReportClick(bounds))
    }

    @Test
    fun clickAfterWindowElapsed_isReported() {
        var time = 1000L
        val store = TapEventDedupStore(dedupWindowMs = 200L, now = { time })

        store.reportTouchInterception(bounds)
        time += 250

        assertTrue(store.shouldReportClick(bounds))
    }

    @Test
    fun differentViewBounds_areNotTreatedAsDuplicate() {
        var time = 1000L
        val store = TapEventDedupStore(dedupWindowMs = 200L, now = { time })

        store.reportTouchInterception(bounds)
        time += 50

        assertTrue(store.shouldReportClick(otherBounds))
    }

    @Test
    fun staleEntriesAreCleared() {
        var time = 1000L
        val store = TapEventDedupStore(dedupWindowMs = 200L, now = { time })

        store.reportTouchInterception(bounds)
        time += 250

        store.clearStale()
        assertTrue(store.shouldReportClick(bounds))
    }

    @Test
    fun touchInterception_isAlwaysReported() {
        var time = 1000L
        val store = TapEventDedupStore(dedupWindowMs = 200L, now = { time })

        store.reportTouchInterception(bounds)
        time += 50

        store.reportTouchInterception(bounds)
        time += 50

        assertFalse(store.shouldReportClick(bounds))
    }
}
