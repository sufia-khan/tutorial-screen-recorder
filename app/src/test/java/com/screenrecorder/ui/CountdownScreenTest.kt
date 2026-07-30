package com.screenrecorder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownScreenTest {

    @Test
    fun stepZeroWithNoElapsed_returnsStepDuration() {
        val result = nextCountdownDelayMs(stepIndex = 0, startTimeNanos = 0, nowNanos = 0)
        assertEquals(1000, result)
    }

    @Test
    fun stepOneWithExactElapsed_returnsStepDuration() {
        val result = nextCountdownDelayMs(
            stepIndex = 1, startTimeNanos = 0, nowNanos = 1_000_000_000
        )
        assertEquals(1000, result)
    }

    @Test
    fun stepTwoWithExactElapsed_returnsStepDuration() {
        val result = nextCountdownDelayMs(
            stepIndex = 2, startTimeNanos = 0, nowNanos = 2_000_000_000
        )
        assertEquals(1000, result)
    }

    @Test
    fun stepSelfCorrectsWhenPreviousStepOverran() {
        val result = nextCountdownDelayMs(
            stepIndex = 1, startTimeNanos = 0, nowNanos = 1_100_000_000
        )
        assertEquals(900, result)
    }

    @Test
    fun largeOverrun_returnsZero() {
        val result = nextCountdownDelayMs(
            stepIndex = 0, startTimeNanos = 0, nowNanos = 10_000_000_000
        )
        assertEquals(0, result)
    }

    @Test
    fun halfOverrun_returnsHalfDuration() {
        val result = nextCountdownDelayMs(
            stepIndex = 0, startTimeNanos = 0, nowNanos = 500_000_000
        )
        assertEquals(500, result)
    }

    @Test
    fun customStepDuration_respected() {
        val result = nextCountdownDelayMs(
            stepIndex = 0, startTimeNanos = 0, nowNanos = 0, stepDurationMs = 500
        )
        assertEquals(500, result)
    }
}
