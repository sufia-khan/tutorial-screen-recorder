package com.screenrecorder.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeElapsedSecondsTest {

    @Test
    fun noPause_countsFullElapsed() {
        assertEquals(5, computeElapsedSeconds(nowMs = 10_000, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 0))
    }

    @Test
    fun accumulatedPause_isExcluded() {
        assertEquals(3, computeElapsedSeconds(nowMs = 10_000, startedAtMs = 5_000, pausedAccumulatedMs = 2_000, pauseStartedAtMs = 0))
    }

    @Test
    fun currentPause_isExcluded() {
        assertEquals(4, computeElapsedSeconds(nowMs = 10_000, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 9_000))
    }

    @Test
    fun exactlyOneSecond_returnsOne() {
        assertEquals(1, computeElapsedSeconds(nowMs = 6_000, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 0))
    }

    @Test
    fun subSecond_returnsZero() {
        assertEquals(0, computeElapsedSeconds(nowMs = 5_999, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 0))
    }

    @Test
    fun clockGuard_neverNegative() {
        assertEquals(0, computeElapsedSeconds(nowMs = 4_000, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 0))
        assertEquals(0, computeElapsedSeconds(nowMs = 5_000, startedAtMs = 5_000, pausedAccumulatedMs = 5_000, pauseStartedAtMs = 0))
    }
}
