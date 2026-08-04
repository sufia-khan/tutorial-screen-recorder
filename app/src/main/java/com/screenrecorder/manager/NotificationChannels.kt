package com.screenrecorder.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    const val RECORDING_CHANNEL_ID = "screen_recorder_channel"
    const val LOCK_CHANNEL_ID = "lock_alert_channel"
    const val SAVED_CHANNEL_ID = "recording_saved_channel"

    fun createChannels(context: Context) {
        ensureChannel(context, RECORDING_CHANNEL_ID, "Screen Recording",
            NotificationManager.IMPORTANCE_LOW)
        ensureChannel(context, LOCK_CHANNEL_ID, "Lock Events",
            NotificationManager.IMPORTANCE_DEFAULT)
        ensureChannel(context, SAVED_CHANNEL_ID, "Recording Saved",
            NotificationManager.IMPORTANCE_DEFAULT)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled) createChannels(context)
    }

    private fun ensureChannel(context: Context, id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(id)
        if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
            if (channel != null) nm.deleteNotificationChannel(id)
            nm.createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }
}
