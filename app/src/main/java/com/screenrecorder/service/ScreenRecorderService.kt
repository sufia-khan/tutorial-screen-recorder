package com.screenrecorder.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.screenrecorder.MainActivity
import com.screenrecorder.R
import com.screenrecorder.interaction.InteractionRecorder
import com.screenrecorder.manager.NotificationChannels
import com.screenrecorder.manager.RecordingManager
import com.screenrecorder.manager.RecordingMode
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.manager.TimerManager
import com.screenrecorder.model.RecorderRuntime
import com.screenrecorder.model.RecordingState
import com.screenrecorder.model.shouldShowOverlay
import com.screenrecorder.session.RecordingSessionManager
import com.screenrecorder.session.SessionMetadataExtractor
import com.screenrecorder.session.model.MetadataFile
import com.screenrecorder.session.model.RecordingMetadata
import com.screenrecorder.session.model.RecordingSession
import com.screenrecorder.session.serialization.SessionJsonCodec
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScreenRecorderService : Service() {

    private lateinit var recordingManager: RecordingManager
    private lateinit var timerManager: TimerManager
    private lateinit var sessionManager: RecordingSessionManager
    private var currentSession: RecordingSession? = null
    private var startedAtMs: Long = 0
    private var recordingStarted = false
    private var stopByLock = false
    private var waitingForUnlockNotification = false
    private var screenLockReceiver: ScreenLockReceiver? = null
    private var touchIndicatorView: TouchIndicatorView? = null

    companion object {
        private const val TAG = "ScreenRecorderSvc"

        const val ACTION_START = "com.screenrecorder.action.START"
        const val ACTION_PAUSE = "com.screenrecorder.action.PAUSE"
        const val ACTION_RESUME = "com.screenrecorder.action.RESUME"
        const val ACTION_STOP = "com.screenrecorder.action.STOP"
        const val ACTION_STOPPED = "com.screenrecorder.action.STOPPED"
        const val ACTION_PAUSE_BY_LOCK = "com.screenrecorder.action.PAUSE_BY_LOCK"
        const val ACTION_UNLOCKED = "com.screenrecorder.action.UNLOCKED"
        const val ACTION_RESUME_FROM_NOTIFICATION = "com.screenrecorder.action.RESUME_FROM_NOTIFICATION"
        const val ACTION_STOP_FROM_NOTIFICATION = "com.screenrecorder.action.STOP_FROM_NOTIFICATION"
        const val ACTION_NOTIFICATIONS_CHANGED = "com.screenrecorder.action.NOTIFICATIONS_CHANGED"

        private const val NOTIFICATION_ID = 1001
        private const val LOCK_NOTIFICATION_ID = 1002
        private const val SAVED_NOTIFICATION_ID = 1003
        private const val REQUEST_CODE_STOP = 0
        private const val REQUEST_CODE_PAUSE = 1
        private const val REQUEST_CODE_RESUME = 2

        @Volatile
        private var pendingResultCode: Int = -1

        @Volatile
        private var pendingData: Intent? = null

        fun setGrantData(resultCode: Int, data: Intent) {
            Log.d(TAG, "setGrantData() resultCode=$resultCode hasData=${data != null}")
            pendingResultCode = resultCode
            pendingData = data
        }

        fun clearPendingGrant() {
            Log.d(TAG, "clearPendingGrant()")
            pendingResultCode = -1
            pendingData = null
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
        sessionManager = RecordingSessionManager.forContext(this)
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
            ACTION_NOTIFICATIONS_CHANGED -> handleNotificationsChanged()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called recordingStarted=$recordingStarted state=${RecorderRuntime.state}")
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
        if (!RecordingPreferences.isNotificationsEnabled(this)) {
            showSilentForegroundNotification()
            Log.d(TAG, "Notifications disabled - silent foreground notification kept")
        }

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
            val session = sessionManager.createSession()
            currentSession = session
            Log.d(TAG, "Session created: ${session.directory.absolutePath}")

            InteractionRecorder.begin()
            Log.d(TAG, "Interaction recorder started")

            recordingManager.setupAndStart(resultCode, data, session.videoPath)
            Log.d(TAG, "setupAndStart() completed - recording is active")

            startedAtMs = System.currentTimeMillis()
            recordingStarted = true
            RecorderRuntime.state = RecordingState.RECORDING
            RecorderRuntime.recordingStartedAtMs = SystemClock.elapsedRealtime()
            RecorderRuntime.pausedAccumulatedMs = 0
            RecorderRuntime.pauseStartedAtMs = 0
            RecorderRuntime.pausedByLock = false
            Log.d(TAG, "Session state = RECORDING")

            updateNotification()

            startTimerHeartbeat()
            Log.d(TAG, "Timer started at 00:00")

            if (RecordingPreferences.getRecordingMode(this) == RecordingMode.OVERLAY &&
                !RecorderRuntime.deviceLocked
            ) {
                FloatingOverlayService.show(this)
            }
            Log.d(TAG, "=== RECORDING ACTIVE ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== RECORDING STARTUP FAILED ===", e)
            markSessionFailed()
            recordingStarted = false
            currentSession = null
            RecorderRuntime.state = RecordingState.IDLE
            RecorderRuntime.pausedByLock = false
            updateNotification("Failed: ${e.message}")
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun handlePause() {
        if (!recordingStarted) return
        if (RecorderRuntime.state != RecordingState.RECORDING) return
        Log.d(TAG, "handlePause() - manual pause from notification")
        recordingManager.pauseRecording()
        RecorderRuntime.pauseStartedAtMs = SystemClock.elapsedRealtime()
        RecorderRuntime.state = RecordingState.PAUSED
        RecorderRuntime.pausedByLock = false
        timerManager.stop()
        updateNotification()
    }

    private fun handlePauseByLock() {
        Log.d(TAG, "[DEBUG-lock] handlePauseByLock enter recordingStarted=$recordingStarted state=${RecorderRuntime.state}")
        RecorderRuntime.deviceLocked = true
        if (recordingStarted && RecorderRuntime.state == RecordingState.RECORDING) {
            Log.d(TAG, "[DEBUG-lock] device locked - stopping and saving recording")
            stopByLock = true
            handleStop()
            return
        }
        FloatingOverlayService.hide(this)
        Log.d(TAG, "[DEBUG-lock] hide sent")
    }

    private fun handleResume() {
        if (!recordingStarted) return
        if (RecorderRuntime.state != RecordingState.PAUSED) return
        Log.d(TAG, "handleResume()")
        recordingManager.resumeRecording()
        if (RecorderRuntime.pauseStartedAtMs > 0) {
            RecorderRuntime.pausedAccumulatedMs +=
                (SystemClock.elapsedRealtime() - RecorderRuntime.pauseStartedAtMs).coerceAtLeast(0)
            RecorderRuntime.pauseStartedAtMs = 0
        }
        RecorderRuntime.state = RecordingState.RECORDING
        RecorderRuntime.pausedByLock = false
        startTimerHeartbeat()
        updateNotification()
        if (RecordingPreferences.getRecordingMode(this) == RecordingMode.OVERLAY &&
            !RecorderRuntime.deviceLocked
        ) {
            FloatingOverlayService.show(this)
        }
        cancelLockNotification()
    }

    private fun handleUnlocked() {
        RecorderRuntime.deviceLocked = false
        if (waitingForUnlockNotification) {
            Log.d(TAG, "handleUnlocked() - showing recording saved notification")
            waitingForUnlockNotification = false
            showRecordingSavedNotification()
            sendStopBroadcast()
            stopSelf()
            return
        }
        if (!recordingStarted) return
        if (shouldShowOverlay(RecorderRuntime.state, RecorderRuntime.pausedByLock, deviceLocked = false)) {
            if (RecordingPreferences.getRecordingMode(this) == RecordingMode.OVERLAY) {
                FloatingOverlayService.show(this)
            }
        }
    }

    private fun handleResumeFromNotification() {
        Log.d(TAG, "handleResumeFromNotification()")
        handleResume()
    }

    private fun handleStopFromNotification() {
        Log.d(TAG, "handleStopFromNotification()")
        handleStop()
    }

    private fun handleNotificationsChanged() {
        val enabled = RecordingPreferences.isNotificationsEnabled(this)
        Log.d(TAG, "handleNotificationsChanged() enabled=$enabled recordingStarted=$recordingStarted")
        if (enabled) {
            if (recordingStarted) {
                startForeground(NOTIFICATION_ID, createNotification("Starting..."))
                updateNotification()
            }
        } else {
            if (recordingStarted) {
                showSilentForegroundNotification()
            } else {
                hideForegroundNotification()
            }
            cancelLockNotification()
        }
    }

    private fun handleStop() {
        if (!recordingStarted) return
        Log.d(TAG, "=== handleStop() ===")

        val lockedStop = stopByLock
        stopByLock = false

        recordingStarted = false
        timerManager.stop()

        val resultFile = recordingManager.stopRecording()
        saveInteractionsForCurrentSession()

        if (resultFile != null && resultFile.exists() && resultFile.length() > 0) {
            Log.d(TAG, "Valid file: ${resultFile.absolutePath} size=${resultFile.length()}")
            finalizeSession(resultFile)
            RecorderRuntime.lastSavedFilePath = resultFile.absolutePath
            RecorderRuntime.lastRecordingSuccess = true
            if (!lockedStop) {
                RecordingPreviewService.show(this, resultFile.absolutePath)
            }
        } else {
            Log.e(TAG, "Invalid file: ${resultFile?.absolutePath} exists=${resultFile?.exists()} size=${resultFile?.length()}")
            markSessionFailed()
            RecorderRuntime.lastRecordingSuccess = false
            Toast.makeText(this, "Recording failed - could not save file", Toast.LENGTH_LONG).show()
        }

        currentSession = null

        RecorderRuntime.state = RecordingState.IDLE
        RecorderRuntime.recordingStartedAtMs = 0
        RecorderRuntime.pausedAccumulatedMs = 0
        RecorderRuntime.pauseStartedAtMs = 0
        RecorderRuntime.pausedByLock = false
        FloatingOverlayService.hide(this)
        cancelLockNotification()
        stopForegroundCompat()

        if (lockedStop && RecorderRuntime.deviceLocked) {
            Log.d(TAG, "Stopped while device locked - waiting for unlock before notifying")
            waitingForUnlockNotification = true
            return
        }

        sendStopBroadcast()
        stopSelf()
        Log.d(TAG, "Service stopped")
    }

    private fun finalizeSession(videoFile: File) {
        val session = currentSession ?: return
        try {
            val videoMetadata = SessionMetadataExtractor.videoMetadataOf(videoFile)
            SessionMetadataExtractor.thumbnailJpegBytes(videoFile)?.let { jpeg ->
                sessionManager.saveThumbnail(session, jpeg)
            }
            val recordingMetadata = RecordingMetadata(
                sessionId = session.sessionId,
                recordingId = session.recordingId,
                createdAtMs = session.createdAtMs,
                startedAtMs = startedAtMs,
                endedAtMs = System.currentTimeMillis(),
                totalPausedMs = RecorderRuntime.pausedAccumulatedMs,
                appVersion = appVersion(),
                androidVersion = Build.VERSION.RELEASE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            sessionManager.finalizeSession(
                session,
                MetadataFile(videoMetadata = videoMetadata, recordingMetadata = recordingMetadata)
            )
            Log.d(TAG, "Session finalized: ${session.directory.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Session finalize failed", e)
        }
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    private fun saveInteractionsForCurrentSession() {
        val session = currentSession ?: return
        val events = InteractionRecorder.end()
        Log.d(TAG, "Interactions end: ${events.size} events, session dir=${session.directory.absolutePath}")
        if (events.isEmpty()) return
        ioScope.launch {
            try {
                sessionManager.saveInteractions(session, events)
                Log.d(TAG, "Interactions saved: ${events.size} events")
                Log.d(TAG, "Interactions content: ${SessionJsonCodec.encodeInteractions(events)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save interactions", e)
            }
        }
    }

    private fun markSessionFailed() {
        val session = currentSession ?: return
        try {
            sessionManager.markFailed(session)
            Log.d(TAG, "Session marked FAILED: ${session.directory.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "markFailed failed", e)
        }
    }

    private fun appVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun cleanup() {
        if (recordingStarted) {
            Log.d(TAG, "cleanup() releasing")
            timerManager.stop()
            try { recordingManager.stopRecording() } catch (e: Exception) { Log.e(TAG, "cleanup error", e) }
            saveInteractionsForCurrentSession()
            markSessionFailed()
            currentSession = null
            RecorderRuntime.state = RecordingState.IDLE
            RecorderRuntime.recordingStartedAtMs = 0
            RecorderRuntime.pausedAccumulatedMs = 0
            RecorderRuntime.pauseStartedAtMs = 0
            RecorderRuntime.pausedByLock = false
            recordingStarted = false
            sendStopBroadcast()
        }
    }

    private fun sendStopBroadcast() {
        Log.d(TAG, "sendStopBroadcast()")
        try {
            sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        } catch (e: Exception) {
            Log.e(TAG, "sendStopBroadcast error", e)
        }
    }

    private fun startTimerHeartbeat() {
        timerManager.start {
            try {
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "Timer tick exception (non-fatal)", e)
            }
        }
    }

    private fun cancelLockNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(LOCK_NOTIFICATION_ID)
    }

    private fun showRecordingSavedNotification() {
        if (!RecordingPreferences.isNotificationsEnabled(this)) {
            Log.d(TAG, "Recording saved notification skipped - notifications disabled")
            return
        }
        val notification = NotificationCompat.Builder(this, NotificationChannels.SAVED_CHANNEL_ID)
            .setContentTitle("Recording saved")
            .setContentText("Your recording was saved successfully")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(SAVED_NOTIFICATION_ID, notification)
        Log.d(TAG, "Recording saved notification shown")
    }

    private fun hideForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun showSilentForegroundNotification() {
        val notification = NotificationCompat.Builder(this, NotificationChannels.RECORDING_CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText("Recording in progress")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Silent foreground notification shown")
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
        NotificationChannels.createChannels(this)
    }

    private fun createNotification(text: String): Notification {
        val builder = NotificationCompat.Builder(this, NotificationChannels.RECORDING_CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)

        if (recordingStarted) {
            builder.addAction(buildStopAction())
            when (RecorderRuntime.state) {
                RecordingState.RECORDING -> builder.addAction(buildPauseAction())
                RecordingState.PAUSED -> builder.addAction(buildResumeAction())
                else -> {}
            }
        }

        return builder.build()
    }

    private fun updateNotification() {
        val snap = RecorderRuntime.snapshot()
        val text = when (snap.state) {
            RecordingState.RECORDING -> RecorderRuntime.formatElapsed(snap.elapsedSeconds)
            RecordingState.PAUSED ->
                if (RecorderRuntime.pausedByLock) "Paused - device locked" else "Paused"
            else -> "Starting..."
        }
        updateNotification(text)
    }

    private fun updateNotification(text: String) {
        if (!RecordingPreferences.isNotificationsEnabled(this)) return
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
}