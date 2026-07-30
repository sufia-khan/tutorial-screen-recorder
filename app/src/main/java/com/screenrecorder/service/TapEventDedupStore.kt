package com.screenrecorder.service

data class ViewBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

class TapEventDedupStore(
    private val dedupWindowMs: Long = 200L,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    private val recentInterceptions = mutableMapOf<ViewBounds, Long>()

    fun reportTouchInterception(bounds: ViewBounds) {
        clearStale()
        recentInterceptions[bounds] = now()
    }

    fun shouldReportClick(bounds: ViewBounds): Boolean {
        clearStale()
        val recorded = recentInterceptions[bounds] ?: return true
        return now() - recorded > dedupWindowMs
    }

    fun clearStale() {
        val currentTime = now()
        recentInterceptions.entries.removeAll { (_, timestamp) ->
            currentTime - timestamp > dedupWindowMs
        }
    }
}
