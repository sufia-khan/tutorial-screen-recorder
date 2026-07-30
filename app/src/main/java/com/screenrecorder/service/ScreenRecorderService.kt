package com.screenrecorder.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.screenrecorder.MainActivity
import com.screenrecorder.R
import com.screenrecorder.manager.RecordingManager
import com.screenrecorder.manager.RecordingMode
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.manager.TimerManager
import com.screenrecorder.model.RecordingSession
import com.screenrecorder.model.RecordingState

class ScreenRecorderService : Service() {

    private lateinit var recordingManager: RecordingManager
    private lateinit var timerManager: TimerManager
    private var recordingStarted = false
    private var screenLockReceiver: ScreenLockReceiver? = null
    private var touchIndicatorView: TouchIndicatorView? = null

    companion object {
        private const val TAG = "ScreenRecorderSvc"

        const val ACTION_START = "com.screenrecorder.action.START"
        const val ACTION_PAUSE = "com.screenrecorder.action.PAUSE"
        const val ACTION_RESUME = "com.screenrecorder.action.RESUME"
        const val ACTION_STOP = "com.screenrecorder.action.STOP"
        const val ACTION_PAUSE_BY_LOCK = "com.screenrecorder.action.PAUSE_BY_LOCK"
        const val ACTION_UNLOCKED = "com.screenrecorder.action.UNLOCKED"
        const val ACTION_RESUME_FROM_NOTIFICATION = "com.screenrecorder.action.RESUME_FROM_NOTIFICATION"
        const val ACTION_STOP_FROM_NOTIFICATION = "com.screenrecorder.action.STOP_FROM_NOTIFICATION"

        private const val CHANNEL_ID = "screen_recorder_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LOCK_CHANNEL_ID = "lock_event_channel"
        private const val LOCK_NOTIFICATION_ID = 1002
        private const val REQUEST_CODE_STOP = 0
        private const val REQUEST_CODE_PAUSE = 1
        private const val REQUEST_CODE_RESUME = 2
        private const val REQUEST_CODE_RESUME_NOTIF = 10
        private const val REQUEST_CODE_STOP_NOTIF = 11

        @Volatile
        private var pendingResultCode: Int = -1

        @Volatile
        private var pendingData: Intent? = null

        fun setGrantData(resultCode: Int, data: Intent) {
            Log.d(TAG, "setGrantData() resultCode=$resultCode hasData=${data != null}")
            pendingResultCode = resultCode
            pendingData = data
        }

        fun startRecording(context: Context) {
            Log.d(TAG, "=== startRecording() ===")
            val intent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun pause(context: Context) {
            context.startService(Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_PAUSE
            })
        }

        fun resume(context: Context) {
            context.startService(Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_RESUME
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        recordingManager = RecordingManager(this)
        timerManager = TimerManager()
        createNotificationChannels()
        registerScreenLockReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStop()
            ACTION_PAUSE_BY_LOCK -> handlePauseByLock()
            ACTION_UNLOCKED -> handleUnlocked()
            ACTION_RESUME_FROM_NOTIFICATION -> handleResumeFromNotification()
            ACTION_STOP_FROM_NOTIFICATION -> handleStopFromNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called recordingStarted=$recordingStarted state=${RecordingSession.state}")
        unregisterScreenLockReceiver()
        try {
            cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy cleanup exception", e)
        }
        super.onDestroy()
    }

    private fun registerScreenLockReceiver() {
        if (screenLockReceiver != null) return
        screenLockReceiver = ScreenLockReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenLockReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenLockReceiver, filter)
        }
        Log.d(TAG, "ScreenLockReceiver registered")
    }

    private fun unregisterScreenLockReceiver() {
        screenLockReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "ScreenLockReceiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "unregisterReceiver error", e)
            }
            screenLockReceiver = null
        }
    }

    private fun handleStart() {
        Log.d(TAG, "=== handleStart() ===")

        startForeground(NOTIFICATION_ID, createNotification("Starting..."))
        Log.d(TAG, "startForeground() OK")

        val resultCode = pendingResultCode
        val data = pendingData
        pendingResultCode = -1
        pendingData = null

        Log.d(TAG, "Grant: resultCode=$resultCode (RESULT_OK=${Activity.RESULT_OK}) data=${data != null}")

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "INVALID GRANT: resultCode=$resultCode hasData=${data != null}")
            updateNotification("Invalid grant")
            stopForegroundCompat()
            stopSelf()
            return
        }

        Log.d(TAG, "Grant valid. Starting MediaProjection + MediaRecorder...")

        try {
            recordingManager.setupAndStart(resultCode, data)
            Log.d(TAG, "setupAndStart() completed - recording is active")

            recordingStarted = true
            RecordingSession.state = RecordingState.RECORDING
            RecordingSession.pausedByLock = false
            Log.d(TAG, "Session state = RECORDING")

            updateNotification("Recording")

            timerManager.start { seconds ->
                try {
                    RecordingSession.elapsedSeconds = seconds
                    updateNotification(formatTime(seconds))
                } catch (e: Exception) {
                    Log.e(TAG, "Timer tick exception (non-fatal)", e)
                }
            }
            Log.d(TAG, "Timer started at 00:00")

            if (RecordingPreferences.getRecordingMode(this) == RecordingMode.OVERLAY) {
                FloatingOverlayService.show(this)
            }
            Log.d(TAG, "=== RECORDING ACTIVE ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== RECORDING STARTUP FAILED ===", e)
            recordingStarted = false
            RecordingSession.state = RecordingState.IDLE
            RecordingSession.pausedByLock = false
            updateNotification("Failed: ${e.message}")
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun handlePause() {
        if (!recordingStarted) return
        if (RecordingSession.state != RecordingState.RECORDING) return
        Log.d(TAG, "handlePause() - manual pause from notification")
        recordingManager.pauseRecording()
        timerManager.pause()
        RecordingSession.state = RecordingState.PAUSED
        RecordingSession.pausedByLock = false
        updateNotification("Paused")
    }

    private fun handlePauseByLock() {
        if (!recordingStarted) return
        if (RecordingSession.state != RecordingState.RECORDING) return
        Log.d(TAG, "handlePauseByLock() - device locked")
        recordingManager.pauseRecording()
        timerManager.pause()
        RecordingSession.state = RecordingState.PAUSED
        RecordingSession.pausedByLock = true
        updateNotification("Paused - device locked")
        FloatingOverlayService.hide(this)
        Log.d(TAG, "Recording paused due to lock")
    }

    private fun handleResume() {
        if (!recordingStarted) return
        if (RecordingSession.state != RecordingState.PAUSED) return
        Log.d(TAG, "handleResume()")
        recordingManager.resumeRecording()
        timerManager.resume()
        RecordingSession.state = RecordingState.RECORDING
        RecordingSession.pausedByLock = false
        updateNotification(formatTime(RecordingSession.elapsedSeconds))
        if (RecordingPreferences.getRecordingMode(this) == RecordingMode.OVERLAY) {
            FloatingOverlayService.show(this)
        }
        cancelLockNotification()
    }

    private fun handleUnlocked() {
        if (!recordingStarted) return
        if (RecordingSession.state != RecordingState.PAUSED) return
        if (!RecordingSession.pausedByLock) return
        Log.d(TAG, "handleUnlocked() - showing unlock notification")
        showUnlockNotification()
    }

    private fun handleResumeFromNotification() {
        Log.d(TAG, "handleResumeFromNotification()")
        handleResume()
    }

    private fun handleStopFromNotification() {
        Log.d(TAG, "handleStopFromNotification()")
        handleStop()
    }

    private fun handleStop() {
        if (!recordingStarted) return
        Log.d(TAG, "=== handleStop() ===")

        recordingStarted = false
        timerManager.stop()

        val resultFile = recordingManager.stopRecording()

        if (resultFile != null && resultFile.exists() && resultFile.length() > 0) {
            Log.d(TAG, "Valid file: ${resultFile.absolutePath} size=${resultFile.length()}")
            MediaScannerConnection.scanFile(this, arrayOf(resultFile.absolutePath), null, null)
            RecordingSession.lastSavedFilePath = resultFile.absolutePath
            RecordingSession.lastRecordingSuccess = true
            RecordingPreviewService.show(this, resultFile.absolutePath)
        } else {
            Log.e(TAG, "Invalid file: ${resultFile?.absolutePath} exists=${resultFile?.exists()} size=${resultFile?.length()}")
            RecordingSession.lastRecordingSuccess = false
            Toast.makeText(this, "Recording failed - could not save file", Toast.LENGTH_LONG).show()
        }

        RecordingSession.state = RecordingState.IDLE
        RecordingSession.elapsedSeconds = 0
        RecordingSession.pausedByLock = false
        FloatingOverlayService.hide(this)
        cancelLockNotification()
        stopForegroundCompat()
        stopSelf()
        Log.d(TAG, "Service stopped")
    }

    private fun cleanup() {
        if (recordingStarted) {
            Log.d(TAG, "cleanup() releasing")
            timerManager.stop()
            try { recordingManager.stopRecording() } catch (e: Exception) { Log.e(TAG, "cleanup error", e) }
            RecordingSession.state = RecordingState.IDLE
            RecordingSession.elapsedSeconds = 0
            RecordingSession.pausedByLock = false
            recordingStarted = false
        }
    }

    private fun showUnlockNotification() {
        createLockChannel()

        val resumeIntent = Intent(this, ScreenRecorderService::class.java).apply {
            action = ACTION_RESUME_FROM_NOTIFICATION
        }
        val stopIntent = Intent(this, ScreenRecorderService::class.java).apply {
            action = ACTION_STOP_FROM_NOTIFICATION
        }

        val resumePendingIntent = PendingIntent.getService(
            this, REQUEST_CODE_RESUME_NOTIF, resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this, REQUEST_CODE_STOP_NOTIF, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, LOCK_CHANNEL_ID)
            .setContentTitle("Recording Paused")
            .setContentText("Recording was paused because your device was locked.")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_notification, "Resume Recording", resumePendingIntent)
            .addAction(R.drawable.ic_notification, "Stop & Save", stopPendingIntent)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(LOCK_NOTIFICATION_ID, notification)
        Log.d(TAG, "Unlock notification shown")
    }

    private fun cancelLockNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(LOCK_NOTIFICATION_ID)
    }

    private fun buildPauseAction(): NotificationCompat.Action {
        val intent = Intent(this, ScreenRecorderService::class.java).apply {
            action = ACTION_PAUSE
        }
        val pendingIntent = PendingIntent.getService(
            this, REQUEST_CODE_PAUSE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(R.drawable.ic_notification, "Pause", pendingIntent)
    }

    private fun buildResumeAction(): NotificationCompat.Action {
        val intent = Intent(this, ScreenRecorderService::class.java).apply {
            action = ACTION_RESUME
        }
        val pendingIntent = PendingIntent.getService(
            this, REQUEST_CODE_RESUME, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(R.drawable.ic_notification, "Resume", pendingIntent)
    }

    private fun buildStopAction(): NotificationCompat.Action {
        val intent = Intent(this, ScreenRecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingIntent = PendingIntent.getService(
            this, REQUEST_CODE_STOP, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(R.drawable.ic_notification, "Stop", pendingIntent)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createLockChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(LOCK_CHANNEL_ID, "Lock Events", NotificationManager.IMPORTANCE_HIGH)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)

        if (recordingStarted) {
            builder.addAction(buildStopAction())
            when (RecordingSession.state) {
                RecordingState.RECORDING -> builder.addAction(buildPauseAction())
                RecordingState.PAUSED -> builder.addAction(buildResumeAction())
                else -> {}
            }
        }

        return builder.build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun showTouchIndicatorOverlay() {
        if (touchIndicatorView != null) return

        IndicatorConfigProvider.update(
            IndicatorConfig(
                color = RecordingPreferences.getTapColor(this),
                shape = RecordingPreferences.getTapShape(this),
                sizeDp = RecordingPreferences.getTapSize(this)
            )
        )

        val view = TouchIndicatorView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.addView(view, params)
            touchIndicatorView = view
            TouchIndicators.attach(view)
            Log.d(TAG, "Touch indicator overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show touch indicator overlay", e)
        }
    }

    private fun hideTouchIndicatorOverlay() {
        touchIndicatorView?.let {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove touch indicator overlay", e)
            }
        }
        TouchIndicators.detach()
        touchIndicatorView = null
        Log.d(TAG, "Touch indicator overlay hidden")
    }

    private fun formatTime(seconds: Int): String {
        return String.format("%02d:%02d", seconds / 60, seconds % 60)
    }
}