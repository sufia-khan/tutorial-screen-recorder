package com.screenrecorder.interaction

import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.screenrecorder.session.model.ElementInfo
import com.screenrecorder.session.model.InteractionEvent
import com.screenrecorder.session.model.InteractionType
import com.screenrecorder.session.model.ScreenInfo
import com.screenrecorder.session.model.TimelineInfo
import com.screenrecorder.session.model.TouchInfo
import java.util.UUID

class AccessibilityEventMapper {

    internal fun extract(event: AccessibilityEvent): ExtractedEventData? {
        val type = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> InteractionType.TAP
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> InteractionType.LONG_PRESS
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> InteractionType.SCROLL
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> InteractionType.SCREEN_CHANGE
            else -> return null
        }

        if (type == InteractionType.SCREEN_CHANGE) {
            return ExtractedEventData(type, bounds = null, element = null)
        }

        val source = try {
            event.source
        } catch (e: Exception) {
            null
        }
        if (source == null) {
            return ExtractedEventData(type, bounds = null, element = null)
        }

        val element = try {
            val base = readElement(source)
            if (type == InteractionType.SCROLL) {
                @Suppress("DEPRECATION")
                base.copy(
                    scrollX = event.scrollX,
                    scrollY = event.scrollY,
                    maxScrollX = event.maxScrollX,
                    maxScrollY = event.maxScrollY,
                    deltaX = event.scrollDeltaX,
                    deltaY = event.scrollDeltaY
                )
            } else {
                base
            }
        } catch (e: Exception) {
            null
        }
        if (element == null) {
            return ExtractedEventData(type, bounds = null, element = null)
        }

        val bounds = element.left?.let { left ->
            element.top?.let { top ->
                element.right?.let { right ->
                    element.bottom?.let { bottom -> ViewBounds(left, top, right, bottom) }
                }
            }
        }
        return ExtractedEventData(type, bounds, element)
    }

    fun build(
        extracted: ExtractedEventData,
        screenInfo: ScreenInfo,
        monotonicMs: Long,
        videoMs: Long
    ): InteractionEvent {
        val touch = when (extracted.type) {
            InteractionType.TAP,
            InteractionType.LONG_PRESS -> extracted.element?.let { element ->
                if (element.centerX != null && element.centerY != null) {
                    TouchInfo(element.centerX, element.centerY)
                } else {
                    null
                }
            }

            else -> null
        }
        return InteractionEvent(
            id = UUID.randomUUID(),
            type = extracted.type,
            timeline = TimelineInfo(monotonicMs = monotonicMs, videoMs = videoMs),
            touch = touch,
            element = extracted.element,
            screen = screenInfo
        )
    }

    @Suppress("DEPRECATION")
    private fun readElement(node: AccessibilityNodeInfo): ElementInfo {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val hasBounds = !bounds.isEmpty
        return ElementInfo(
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            clickable = node.isClickable,
            enabled = node.isEnabled,
            focused = node.isFocused,
            selected = node.isSelected,
            checkable = node.isCheckable,
            checked = node.isChecked,
            left = if (hasBounds) bounds.left else null,
            top = if (hasBounds) bounds.top else null,
            right = if (hasBounds) bounds.right else null,
            bottom = if (hasBounds) bounds.bottom else null,
            centerX = if (hasBounds) bounds.centerX() else null,
            centerY = if (hasBounds) bounds.centerY() else null
        )
    }

    data class ExtractedEventData(
        val type: InteractionType,
        val bounds: ViewBounds?,
        val element: ElementInfo?
    )

    companion object {
        fun screenInfoOf(windowManager: WindowManager): ScreenInfo {
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds
            } else {
                @Suppress("DEPRECATION")
                val display = windowManager.defaultDisplay
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
                Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
            }
            @Suppress("DEPRECATION")
            val rotation = windowManager.defaultDisplay.rotation
            return ScreenInfo(
                width = bounds.width(),
                height = bounds.height(),
                rotationDegrees = rotation * 90
            )
        }
    }
}
