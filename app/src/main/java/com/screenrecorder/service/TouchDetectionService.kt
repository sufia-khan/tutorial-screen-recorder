package com.screenrecorder.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.screenrecorder.interaction.AccessibilityEventMapper
import com.screenrecorder.interaction.InteractionRecorder

class TouchDetectionService : AccessibilityService() {

    private companion object {
        const val TAG = "TouchDetectionSvc"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected() events=${serviceInfo.eventTypes}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(
            TAG,
            "eventType=${event.eventType} pkg=${event.packageName} cls=${event.className} " +
                "src=${event.source != null} t=${event.eventTime}"
        )
        InteractionRecorder.onAccessibilityEvent(
            event,
            AccessibilityEventMapper.screenInfoOf(getSystemService(WINDOW_SERVICE) as WindowManager)
        )
    }

    override fun onInterrupt() = Unit
}
