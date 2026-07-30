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
import androidx.core.content.ContextCompat
import com.screenrecorder.service.TouchDetectionService

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
}