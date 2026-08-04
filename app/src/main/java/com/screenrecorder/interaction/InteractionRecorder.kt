package com.screenrecorder.interaction

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.screenrecorder.model.RecorderRuntime
import com.screenrecorder.model.RecordingState
import com.screenrecorder.model.computeElapsedMs
import com.screenrecorder.session.model.InteractionEvent
import com.screenrecorder.session.model.ScreenInfo

object InteractionRecorder {

    private const val TAG = "InteractionRecorder"

    private val mapper = AccessibilityEventMapper()
    private val dedupStore = InteractionDedupStore(now = { SystemClock.elapsedRealtime() })
    private val buffer = mutableListOf<InteractionEvent>()

    @Volatile
    private var active = false

    fun begin() {
        synchronized(buffer) {
            active = true
            buffer.clear()
            dedupStore.clear()
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent, screenInfo: ScreenInfo) {
        if (!active) return
        if (RecorderRuntime.state != RecordingState.RECORDING) return
        val monotonicMs = SystemClock.elapsedRealtime()
        try {
            val extracted = mapper.extract(event) ?: return
            val deltaX = extracted.element?.deltaX ?: 0
            val deltaY = extracted.element?.deltaY ?: 0
            if (!dedupStore.shouldRecord(extracted.type, extracted.bounds, deltaX, deltaY, monotonicMs)) {
                return
            }
            val videoMs = computeElapsedMs(
                nowMs = monotonicMs,
                startedAtMs = RecorderRuntime.recordingStartedAtMs,
                pausedAccumulatedMs = RecorderRuntime.pausedAccumulatedMs,
                pauseStartedAtMs = RecorderRuntime.pauseStartedAtMs
            )
            val interaction = mapper.build(extracted, screenInfo, monotonicMs, videoMs)
            synchronized(buffer) {
                buffer.add(interaction)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record interaction event", e)
        }
    }

    fun end(): List<InteractionEvent> = synchronized(buffer) {
        active = false
        val snapshot = buffer.toList()
        buffer.clear()
        dedupStore.clear()
        snapshot
    }
}
