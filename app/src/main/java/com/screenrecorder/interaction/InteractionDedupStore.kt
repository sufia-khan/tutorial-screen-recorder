package com.screenrecorder.interaction

import com.screenrecorder.session.model.InteractionType

data class ViewBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

class InteractionDedupStore(
    private val now: () -> Long,
    private val tapWindowMs: Long = 200,
    private val longPressSuppressMs: Long = 500,
    private val scrollThrottleMs: Long = 50
) {

    private data class Key(val type: InteractionType, val bounds: ViewBounds)

    private val recent = mutableMapOf<Key, Long>()

    fun shouldRecord(
        type: InteractionType,
        bounds: ViewBounds?,
        scrollDeltaX: Int,
        scrollDeltaY: Int,
        time: Long
    ): Boolean {
        clearStale(time)

        if (type == InteractionType.SCREEN_CHANGE) return true

        if (type == InteractionType.SCROLL) {
            if (bounds == null) return true
            if (scrollDeltaX == 0 && scrollDeltaY == 0) return false
            return throttle(Key(type, bounds), time, scrollThrottleMs)
        }

        if (bounds == null) return true

        val key = Key(type, bounds)
        if (withinWindow(recent[key], time, tapWindowMs)) return false
        if (type == InteractionType.TAP &&
            withinWindow(recent[Key(InteractionType.LONG_PRESS, bounds)], time, longPressSuppressMs)
        ) {
            return false
        }
        recent[key] = time
        return true
    }

    fun clear() {
        recent.clear()
    }

    private fun throttle(key: Key, time: Long, windowMs: Long): Boolean {
        val recorded = recent[key]
        return if (recorded != null && time - recorded <= windowMs) {
            false
        } else {
            recent[key] = time
            true
        }
    }

    private fun withinWindow(recordedAt: Long?, time: Long, windowMs: Long): Boolean =
        recordedAt != null && time - recordedAt <= windowMs

    private fun clearStale(time: Long) {
        val maxWindowMs = maxOf(tapWindowMs, longPressSuppressMs, scrollThrottleMs)
        recent.entries.removeAll { (_, timestamp) -> time - timestamp > maxWindowMs }
    }
}
