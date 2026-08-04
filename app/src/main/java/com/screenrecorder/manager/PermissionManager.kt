package com.screenrecorder.manager

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.screenrecorder.service.TouchDetectionService

enum class NotificationToggleAction {
    DISABLE,
    ENABLE,
    OPEN_SYSTEM_SETTINGS
}

enum class OverlayStartAction { ASK_PERMISSION, START_DIRECTLY }

enum class AccessibilityStartAction { PROMPT_TO_ENABLE, START_DIRECTLY }

object PermissionManager {

    fun hasAllPermissions(context: Context): Boolean {
        return hasOverlayPermission(context) && hasNotificationPermission(context)
    }

    fun hasOverlayPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context)
        }
        return true
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            android.util.Log.e("PermissionManager", "No activity for notification settings", e)
        }
    }

    internal fun decideNotificationToggleAction(
        switchOn: Boolean,
        systemAllowed: Boolean
    ): NotificationToggleAction = when {
        !systemAllowed -> NotificationToggleAction.OPEN_SYSTEM_SETTINGS
        switchOn -> NotificationToggleAction.DISABLE
        else -> NotificationToggleAction.ENABLE
    }

    fun setAppNotificationsEnabled(context: Context, enabled: Boolean) {
        RecordingPreferences.setNotificationsEnabled(context, enabled)
        NotificationChannels.setEnabled(context, enabled)
    }

    internal fun shouldShowNotificationToggle(sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.TIRAMISU

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val targetComponent = ComponentName(
            context, TouchDetectionService::class.java
        ).flattenToString()
        return isAccessibilityServiceEnabled(services, targetComponent)
    }

    internal fun isAccessibilityServiceEnabled(
        enabledServices: String?,
        targetComponent: String
    ): Boolean {
        if (enabledServices == null) return false
        return enabledServices.split(":").any { it == targetComponent }
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            android.util.Log.e("PermissionManager", "No activity for overlay settings", e)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            android.util.Log.e("PermissionManager", "No activity for accessibility settings", e)
        }
    }

    /** Whether a recording start should nudge the user to enable the accessibility service. */
    internal fun decideAccessibilityStartAction(
        touchCaptureEnabled: Boolean,
        serviceEnabled: Boolean
    ): AccessibilityStartAction = when {
        !touchCaptureEnabled -> AccessibilityStartAction.START_DIRECTLY
        serviceEnabled -> AccessibilityStartAction.START_DIRECTLY
        else -> AccessibilityStartAction.PROMPT_TO_ENABLE
    }

    internal fun decideOverlayStartAction(
        mode: RecordingMode,
        hasOverlayPermission: Boolean,
        denialRemembered: Boolean
    ): OverlayStartAction {
        return if (mode == RecordingMode.OVERLAY && !hasOverlayPermission && !denialRemembered) {
            OverlayStartAction.ASK_PERMISSION
        } else {
            OverlayStartAction.START_DIRECTLY
        }
    }
}