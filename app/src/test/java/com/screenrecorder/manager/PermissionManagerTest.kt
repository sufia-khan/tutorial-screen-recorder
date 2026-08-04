package com.screenrecorder.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionManagerTest {

    @Test
    fun nullServices_returnsFalse() {
        assertFalse(PermissionManager.isAccessibilityServiceEnabled(null, "com.example.Svc"))
    }

    @Test
    fun emptyString_returnsFalse() {
        assertFalse(PermissionManager.isAccessibilityServiceEnabled("", "com.example.Svc"))
    }

    @Test
    fun singleServiceMatching_returnsTrue() {
        assertTrue(
            PermissionManager.isAccessibilityServiceEnabled(
                "com.example/com.example.Svc",
                "com.example/com.example.Svc"
            )
        )
    }

    @Test
    fun singleServiceNonMatching_returnsFalse() {
        assertFalse(
            PermissionManager.isAccessibilityServiceEnabled(
                "com.example/com.example.OtherSvc",
                "com.example/com.example.Svc"
            )
        )
    }

    @Test
    fun multipleServicesWithMatch_returnsTrue() {
        assertTrue(
            PermissionManager.isAccessibilityServiceEnabled(
                "com.a/com.a.SvcA:com.example/com.example.Svc:com.b/com.b.SvcB",
                "com.example/com.example.Svc"
            )
        )
    }

    @Test
    fun multipleServicesWithoutMatch_returnsFalse() {
        assertFalse(
            PermissionManager.isAccessibilityServiceEnabled(
                "com.a/com.a.SvcA:com.b/com.b.SvcB",
                "com.example/com.example.Svc"
            )
        )
    }

    @Test
    fun cleanModeNoPermission_neverAsks() {
        assertEquals(
            OverlayStartAction.START_DIRECTLY,
            PermissionManager.decideOverlayStartAction(RecordingMode.CLEAN, false, false)
        )
    }

    @Test
    fun cleanModeNoPermissionDenied_neverAsks() {
        assertEquals(
            OverlayStartAction.START_DIRECTLY,
            PermissionManager.decideOverlayStartAction(RecordingMode.CLEAN, false, true)
        )
    }

    @Test
    fun overlayModeWithPermission_neverAsks() {
        assertEquals(
            OverlayStartAction.START_DIRECTLY,
            PermissionManager.decideOverlayStartAction(RecordingMode.OVERLAY, true, false)
        )
    }

    @Test
    fun overlayModeNoPermissionNotDenied_asks() {
        assertEquals(
            OverlayStartAction.ASK_PERMISSION,
            PermissionManager.decideOverlayStartAction(RecordingMode.OVERLAY, false, false)
        )
    }

    @Test
    fun overlayModeNoPermissionDenied_neverAsks() {
        assertEquals(
            OverlayStartAction.START_DIRECTLY,
            PermissionManager.decideOverlayStartAction(RecordingMode.OVERLAY, false, true)
        )
    }

    @Test
    fun touchCaptureDisabled_neverPrompts() {
        assertEquals(
            AccessibilityStartAction.START_DIRECTLY,
            PermissionManager.decideAccessibilityStartAction(false, false)
        )
    }

    @Test
    fun touchCaptureEnabledAndServiceEnabled_neverPrompts() {
        assertEquals(
            AccessibilityStartAction.START_DIRECTLY,
            PermissionManager.decideAccessibilityStartAction(true, true)
        )
    }

    @Test
    fun touchCaptureEnabledButServiceDisabled_prompts() {
        assertEquals(
            AccessibilityStartAction.PROMPT_TO_ENABLE,
            PermissionManager.decideAccessibilityStartAction(true, false)
        )
    }
}
