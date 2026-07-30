package com.screenrecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenLockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenLockReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen OFF (device locked)")
                context.startService(Intent(context, ScreenRecorderService::class.java).apply {
                    action = ScreenRecorderService.ACTION_PAUSE_BY_LOCK
                })
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present (device unlocked)")
                context.startService(Intent(context, ScreenRecorderService::class.java).apply {
                    action = ScreenRecorderService.ACTION_UNLOCKED
                })
            }
        }
    }
}