package com.screenrecorder.manager

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
}
