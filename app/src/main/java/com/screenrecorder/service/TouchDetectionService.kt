package com.screenrecorder.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TouchDetectionService : AccessibilityService() {

    companion object {
        private const val TAG = "TouchDetectionSvc"
        private const val DEBUG_TAP = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val source = event.source ?: return
        val bounds = Rect()
        source.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return

        if (DEBUG_TAP) {
            logTapDebugInfo(event, source, bounds)
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED -> {
                TouchIndicators.show(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
        }
    }

    private fun logTapDebugInfo(event: AccessibilityEvent, source: AccessibilityNodeInfo, bounds: Rect) {
        try {
            val sb = StringBuilder()
            sb.append("\n=== TAP DEBUG ===")
            sb.append("\nEvent type: ").append(eventTypeName(event.eventType))
            sb.append("\nPackage: ").append(event.packageName ?: "N/A")
            sb.append("\nClass: ").append(source.className ?: "N/A")
            sb.append("\nView ID: ").append(source.viewIdResourceName ?: "N/A")
            sb.append("\nText: ").append(source.text ?: "N/A")
            sb.append("\nContent desc: ").append(source.contentDescription ?: "N/A")
            sb.append("\nClickable: ").append(source.isClickable)
            sb.append("\nFocusable: ").append(source.isFocusable)
            sb.append("\nBounds: left=").append(bounds.left)
                .append(" top=").append(bounds.top)
                .append(" right=").append(bounds.right)
                .append(" bottom=").append(bounds.bottom)
                .append(" centerX=").append(bounds.centerX())
                .append(" centerY=").append(bounds.centerY())
                .append(" width=").append(bounds.width())
                .append(" height=").append(bounds.height())

            val parent = source.parent
            if (parent != null) {
                val parentBounds = Rect()
                parent.getBoundsInScreen(parentBounds)
                sb.append("\nParent class: ").append(parent.className ?: "N/A")
                sb.append("\nParent bounds: left=").append(parentBounds.left)
                    .append(" top=").append(parentBounds.top)
                    .append(" right=").append(parentBounds.right)
                    .append(" bottom=").append(parentBounds.bottom)
                sb.append("\nChild count: ").append(parent.childCount)
                for (i in 0 until minOf(parent.childCount, 5)) {
                    try {
                        val child = parent.getChild(i)
                        if (child != null) {
                            val childBounds = Rect()
                            child.getBoundsInScreen(childBounds)
                            sb.append("\n  Child[$i] class=").append(child.className ?: "N/A")
                                .append(" bounds=").append(childBounds.toShortString())
                            child.recycle()
                        }
                    } catch (e: Exception) {
                        sb.append("\n  Child[$i] error: ").append(e.message)
                    }
                }
                parent.recycle()
            }
            sb.append("\n=== END ===")
            Log.d(TAG, sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Debug logging failed", e)
        }
    }

    private fun eventTypeName(type: Int): String {
        return when (type) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "TYPE_VIEW_LONG_CLICKED"
            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED -> "TYPE_VIEW_CONTEXT_CLICKED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "TYPE_VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "TYPE_VIEW_SELECTED"

            else -> "UNKNOWN($type)"
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService connected")
    }
}