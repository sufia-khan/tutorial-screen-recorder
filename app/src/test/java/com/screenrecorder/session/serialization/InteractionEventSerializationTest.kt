package com.screenrecorder.session.serialization

import com.screenrecorder.session.model.ElementInfo
import com.screenrecorder.session.model.InteractionEvent
import com.screenrecorder.session.model.InteractionType
import com.screenrecorder.session.model.ScreenInfo
import com.screenrecorder.session.model.TimelineInfo
import com.screenrecorder.session.model.TouchInfo
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionEventSerializationTest {

    @Test
    fun fullEvent_roundTrips() {
        val event = InteractionEvent(
            id = UUID.fromString("f7c2a1b4-9d3e-4a5b-8c6d-2e1f0a9b8c7d"),
            type = InteractionType.TAP,
            timeline = TimelineInfo(monotonicMs = 48230123, videoMs = 2450),
            touch = TouchInfo(x = 540, y = 1200),
            element = ElementInfo(
                className = "android.widget.Button",
                packageName = "com.example.notes",
                text = "Add Note",
                contentDescription = null,
                viewIdResourceName = "com.example.notes:id/btn_add",
                clickable = true,
                enabled = true,
                focused = false,
                selected = false,
                checkable = false,
                checked = false,
                left = 380, top = 1150, right = 700, bottom = 1250,
                centerX = 540, centerY = 1200
            ),
            screen = ScreenInfo(width = 1080, height = 2400, rotationDegrees = 0)
        )

        val json = SessionJsonCodec.encodeInteractions(listOf(event))
        val decoded = SessionJsonCodec.decodeInteractions(json)

        assertEquals(listOf(event), decoded)
    }

    @Test
    fun nullFields_roundTrip() {
        val event = InteractionEvent(
            id = UUID.randomUUID(),
            type = InteractionType.SCREEN_CHANGE,
            timeline = TimelineInfo(monotonicMs = 100, videoMs = 50),
            touch = null,
            element = null,
            screen = null
        )

        val json = SessionJsonCodec.encodeInteractions(listOf(event))
        val decoded = SessionJsonCodec.decodeInteractions(json)

        assertEquals(listOf(event), decoded)
    }

    @Test
    fun emptyList_encodesToEmptyArray() {
        assertEquals("[]", SessionJsonCodec.encodeInteractions(emptyList()))
        assertTrue(SessionJsonCodec.decodeInteractions("[]").isEmpty())
    }

    @Test
    fun corruptedInput_decodesToEmptyList() {
        assertTrue(SessionJsonCodec.decodeInteractions("not json at all").isEmpty())
        assertTrue(SessionJsonCodec.decodeInteractions("[{\"type\":\"TAP\"}]").isEmpty())
    }
}
