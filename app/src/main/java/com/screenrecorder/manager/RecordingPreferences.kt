package com.screenrecorder.manager

import android.content.Context
import android.content.SharedPreferences

enum class RecordingMode(val value: String, val displayName: String) {
    OVERLAY("overlay", "Floating Overlay"),
    CLEAN("clean", "Clean Mode (Notification Only)");

    companion object {
        fun fromValue(value: String): RecordingMode =
            entries.firstOrNull { it.value == value } ?: OVERLAY
    }
}

enum class ThemeMode(val value: String, val displayName: String) {
    SYSTEM("system", "Follow System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromValue(value: String): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

object RecordingPreferences {

    private const val PREFS_NAME = "recording_prefs"

    private const val KEY_RECORDING_MODE = "recording_mode"
    private const val KEY_OVERLAY_OPACITY = "overlay_opacity"
    private const val KEY_OVERLAY_SIZE = "overlay_size"
    private const val KEY_AUTO_COLLAPSE = "auto_collapse"
    private const val KEY_SNAP_TO_EDGE = "snap_to_edge"
    private const val KEY_AUTO_COLLAPSE_DELAY_MS = "auto_collapse_delay_ms"
    private const val KEY_OVERLAY_X = "overlay_x"
    private const val KEY_OVERLAY_Y = "overlay_y"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_TOUCH_HIGHLIGHT = "touch_highlight"
    private const val KEY_TAP_COLOR = "tap_color"
    private const val KEY_TAP_SHAPE = "tap_shape"
    private const val KEY_TAP_SIZE = "tap_size"
    private const val KEY_SHOW_TOUCHES = "show_touches"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_OVERLAY_START_DENIED = "overlay_start_denied"

    private const val DEFAULT_MODE = "overlay"
    private const val DEFAULT_OPACITY = 80
    private const val DEFAULT_OVERLAY_SIZE = 40
    private const val DEFAULT_AUTO_COLLAPSE = true
    private const val DEFAULT_SNAP_TO_EDGE = true
    private const val DEFAULT_AUTO_COLLAPSE_DELAY_MS = 3000L
    private const val DEFAULT_OVERLAY_X = -1
    private const val DEFAULT_OVERLAY_Y = -1
    private const val DEFAULT_THEME_MODE = "system"
    private const val DEFAULT_TOUCH_HIGHLIGHT = false
    private const val DEFAULT_TAP_COLOR = "#FF808080"
    private const val DEFAULT_TAP_SHAPE = "circle"
    private const val DEFAULT_TAP_SIZE = 24
    private const val DEFAULT_SHOW_TOUCHES = false
    private const val DEFAULT_NOTIFICATIONS_ENABLED = true
    private const val DEFAULT_OVERLAY_START_DENIED = false

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecordingMode(context: Context): RecordingMode =
        RecordingMode.fromValue(prefs(context).getString(KEY_RECORDING_MODE, DEFAULT_MODE) ?: DEFAULT_MODE)

    fun setRecordingMode(context: Context, mode: RecordingMode) {
        prefs(context).edit().putString(KEY_RECORDING_MODE, mode.value).apply()
    }

    fun getOverlayOpacity(context: Context): Int =
        prefs(context).getInt(KEY_OVERLAY_OPACITY, DEFAULT_OPACITY)

    fun setOverlayOpacity(context: Context, opacity: Int) {
        prefs(context).edit().putInt(KEY_OVERLAY_OPACITY, opacity.coerceIn(30, 100)).apply()
    }

    fun getOverlaySize(context: Context): Int =
        prefs(context).getInt(KEY_OVERLAY_SIZE, DEFAULT_OVERLAY_SIZE)

    fun setOverlaySize(context: Context, sizeDp: Int) {
        prefs(context).edit().putInt(KEY_OVERLAY_SIZE, clampOverlaySize(sizeDp)).apply()
    }

    internal fun clampOverlaySize(sizeDp: Int): Int = sizeDp.coerceIn(30, 60)

    fun isAutoCollapseEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_COLLAPSE, DEFAULT_AUTO_COLLAPSE)

    fun setAutoCollapseEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_COLLAPSE, enabled).apply()
    }

    fun isSnapToEdgeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SNAP_TO_EDGE, DEFAULT_SNAP_TO_EDGE)

    fun setSnapToEdgeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SNAP_TO_EDGE, enabled).apply()
    }

    fun getAutoCollapseDelayMs(context: Context): Long =
        prefs(context).getLong(KEY_AUTO_COLLAPSE_DELAY_MS, DEFAULT_AUTO_COLLAPSE_DELAY_MS)

    fun setAutoCollapseDelayMs(context: Context, delayMs: Long) {
        prefs(context).edit().putLong(KEY_AUTO_COLLAPSE_DELAY_MS, delayMs.coerceIn(1000, 10000)).apply()
    }

    fun getOverlayX(context: Context): Int =
        prefs(context).getInt(KEY_OVERLAY_X, DEFAULT_OVERLAY_X)

    fun setOverlayX(context: Context, x: Int) {
        prefs(context).edit().putInt(KEY_OVERLAY_X, x).apply()
    }

    fun getOverlayY(context: Context): Int =
        prefs(context).getInt(KEY_OVERLAY_Y, DEFAULT_OVERLAY_Y)

    fun setOverlayY(context: Context, y: Int) {
        prefs(context).edit().putInt(KEY_OVERLAY_Y, y).apply()
    }

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.fromValue(prefs(context).getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE)

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.value).apply()
    }

    fun isTouchHighlightEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TOUCH_HIGHLIGHT, DEFAULT_TOUCH_HIGHLIGHT)

    fun setTouchHighlightEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOUCH_HIGHLIGHT, enabled).apply()
    }

    fun getTapColor(context: Context): String =
        prefs(context).getString(KEY_TAP_COLOR, DEFAULT_TAP_COLOR) ?: DEFAULT_TAP_COLOR

    fun setTapColor(context: Context, color: String) {
        prefs(context).edit().putString(KEY_TAP_COLOR, color).apply()
    }

    fun getTapShape(context: Context): String =
        prefs(context).getString(KEY_TAP_SHAPE, DEFAULT_TAP_SHAPE) ?: DEFAULT_TAP_SHAPE

    fun setTapShape(context: Context, shape: String) {
        prefs(context).edit().putString(KEY_TAP_SHAPE, shape).apply()
    }

    fun getTapSize(context: Context): Int =
        prefs(context).getInt(KEY_TAP_SIZE, DEFAULT_TAP_SIZE)

    fun setTapSize(context: Context, sizeDp: Int) {
        prefs(context).edit().putInt(KEY_TAP_SIZE, sizeDp.coerceIn(10, 40)).apply()
    }

    fun getShowTouchesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_TOUCHES, DEFAULT_SHOW_TOUCHES)

    fun setShowTouchesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_TOUCHES, enabled).apply()
    }

    internal fun isShowTouchesEnabled(rawValue: Int): Boolean = rawValue == 1

    fun isNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS_ENABLED, DEFAULT_NOTIFICATIONS_ENABLED)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun isOverlayStartDenied(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_START_DENIED, DEFAULT_OVERLAY_START_DENIED)

    fun setOverlayStartDenied(context: Context, denied: Boolean) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_START_DENIED, denied).apply()
    }
}
