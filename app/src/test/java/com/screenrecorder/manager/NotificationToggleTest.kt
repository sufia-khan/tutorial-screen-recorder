package com.screenrecorder.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationToggleTest {

    @Test
    fun switchOn_systemAllowed_disablesInApp() {
        assertEquals(
            NotificationToggleAction.DISABLE,
            PermissionManager.decideNotificationToggleAction(switchOn = true, systemAllowed = true)
        )
    }

    @Test
    fun switchOff_systemAllowed_enablesInApp() {
        assertEquals(
            NotificationToggleAction.ENABLE,
            PermissionManager.decideNotificationToggleAction(switchOn = false, systemAllowed = true)
        )
    }

    @Test
    fun switchOff_systemDenied_opensSystemSettings() {
        assertEquals(
            NotificationToggleAction.OPEN_SYSTEM_SETTINGS,
            PermissionManager.decideNotificationToggleAction(switchOn = false, systemAllowed = false)
        )
    }

    @Test
    fun switchOn_systemDenied_opensSystemSettings() {
        assertEquals(
            NotificationToggleAction.OPEN_SYSTEM_SETTINGS,
            PermissionManager.decideNotificationToggleAction(switchOn = true, systemAllowed = false)
        )
    }

    @Test
    fun android13_shouldShowToggle() {
        assertTrue(PermissionManager.shouldShowNotificationToggle(33))
    }

    @Test
    fun android14_shouldShowToggle() {
        assertTrue(PermissionManager.shouldShowNotificationToggle(34))
    }

    @Test
    fun android12_shouldHideToggle() {
        assertFalse(PermissionManager.shouldShowNotificationToggle(32))
    }

    @Test
    fun android10_shouldHideToggle() {
        assertFalse(PermissionManager.shouldShowNotificationToggle(29))
    }
}
