package com.screenrecorder.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeElapsedMsTest {

    @Test
    fun noPause_countsFullElapsed() {
        assertEquals(5_000, computeElapsedMs(nowMs = 10_000, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 0))
    }

    @Test
    fun accumulatedPause_isExcluded() {
        assertEquals(3_000, computeElapsedMs(nowMs = 10_000, startedAtMs = 5_000, pausedAccumulatedMs = 2_000, pauseStartedAtMs = 0))
    }

    @Test
    fun currentPause_isExcluded() {
        assertEquals(4_000, computeElapsedMs(nowMs = 10_000, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 9_000))
    }

    @Test
    fun exactOneSecond_isOneThousand() {
        assertEquals(1_000, computeElapsedMs(nowMs = 6_000, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 0))
    }

    @Test
    fun subSecond_isPreserved() {
        assertEquals(999, computeElapsedMs(nowMs = 5_999, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 0))
    }

    @Test
    fun clockGuard_neverNegative() {
        assertEquals(0, computeElapsedMs(nowMs = 4_000, startedAtMs = 5_000, pausedAccumulatedMs = 0, pauseStartedAtMs = 0))
        assertEquals(0, computeElapsedMs(nowMs = 5_000, startedAtMs = 5_000, pausedAccumulatedMs = 5_000, pauseStartedAtMs = 0))
    }

    @Test
    fun noStartTime_countsZero() {
        assertEquals(0, computeElapsedMs(nowMs = 123_456_789, startedAtMs = 0, pausedAccumulatedMs = 0, pauseStartedAtMs = 0))
    }
}
