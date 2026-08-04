package com.screenrecorder.interaction

import com.screenrecorder.session.model.ElementInfo
import com.screenrecorder.session.model.InteractionType
import com.screenrecorder.session.model.ScreenInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityEventMapperTest {

    private val mapper = AccessibilityEventMapper()
    private val screen = ScreenInfo(width = 1080, height = 2400, rotationDegrees = 0)

    @Test
    fun tap_buildsEventWithTouchCenter() {
        val extracted = AccessibilityEventMapper.ExtractedEventData(
            type = InteractionType.TAP,
            bounds = ViewBounds(100, 200, 300, 400),
            element = elementWithBounds()
        )

        val event = mapper.build(extracted, screen, monotonicMs = 5000, videoMs = 2000)

        assertEquals(InteractionType.TAP, event.type)
        assertEquals(200, event.touch?.x)
        assertEquals(300, event.touch?.y)
        assertEquals(5000, event.timeline.monotonicMs)
        assertEquals(2000, event.timeline.videoMs)
        assertEquals(screen, event.screen)
        assertEquals(extracted.element, event.element)
    }

    @Test
    fun longPress_buildsEventWithTouchCenter() {
        val extracted = AccessibilityEventMapper.ExtractedEventData(
            type = InteractionType.LONG_PRESS,
            bounds = ViewBounds(100, 200, 300, 400),
            element = elementWithBounds()
        )

        val event = mapper.build(extracted, screen, monotonicMs = 1, videoMs = 1)

        assertEquals(InteractionType.LONG_PRESS, event.type)
        assertEquals(200, event.touch?.x)
        assertEquals(300, event.touch?.y)
    }

    @Test
    fun scroll_buildsEventWithoutTouch() {
        val extracted = AccessibilityEventMapper.ExtractedEventData(
            type = InteractionType.SCROLL,
            bounds = ViewBounds(0, 0, 100, 100),
            element = elementWithBounds().copy(deltaX = 0, deltaY = 12, scrollY = 480, maxScrollY = 3200)
        )

        val event = mapper.build(extracted, screen, monotonicMs = 1, videoMs = 1)

        assertEquals(InteractionType.SCROLL, event.type)
        assertNull(event.touch)
        assertEquals(12, event.element?.deltaY)
        assertEquals(480, event.element?.scrollY)
    }

    @Test
    fun screenChange_buildsEventWithoutElementOrTouch() {
        val extracted = AccessibilityEventMapper.ExtractedEventData(
            type = InteractionType.SCREEN_CHANGE,
            bounds = null,
            element = null
        )

        val event = mapper.build(extracted, screen, monotonicMs = 1, videoMs = 1)

        assertEquals(InteractionType.SCREEN_CHANGE, event.type)
        assertNull(event.touch)
        assertNull(event.element)
    }

    @Test
    fun elementWithoutBounds_buildsTapWithoutTouch() {
        val extracted = AccessibilityEventMapper.ExtractedEventData(
            type = InteractionType.TAP,
            bounds = null,
            element = ElementInfo(className = "android.widget.TextView", packageName = "com.example")
        )

        val event = mapper.build(extracted, screen, monotonicMs = 1, videoMs = 1)

        assertEquals(InteractionType.TAP, event.type)
        assertNull(event.touch)
        assertEquals("android.widget.TextView", event.element?.className)
    }

    private fun elementWithBounds(): ElementInfo = ElementInfo(
        className = "android.widget.Button",
        packageName = "com.example",
        text = "Save",
        viewIdResourceName = "com.example:id/btn_save",
        clickable = true,
        enabled = true,
        checked = false,
        left = 100, top = 200, right = 300, bottom = 400,
        centerX = 200, centerY = 300
    )
}
