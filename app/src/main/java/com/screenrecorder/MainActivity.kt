package com.screenrecorder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenrecorder.editor.ZoomEditorActivity
import com.screenrecorder.manager.NotificationChannels
import com.screenrecorder.manager.OverlayStartAction
import com.screenrecorder.manager.AccessibilityStartAction
import com.screenrecorder.manager.PermissionManager
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.manager.ThemeMode
import com.screenrecorder.manager.TrashStore
import com.screenrecorder.model.RecorderRuntime
import com.screenrecorder.model.RecordingState
import com.screenrecorder.player.PlaybackActivity
import com.screenrecorder.service.FloatingOverlayService
import com.screenrecorder.service.ScreenRecorderService
import com.screenrecorder.ui.CountdownScreen
import com.screenrecorder.ui.EditScreen
import com.screenrecorder.ui.HomeScreen
import com.screenrecorder.ui.SettingsScreen
import com.screenrecorder.ui.TrashScreen
import com.screenrecorder.ui.theme.ScreenRecorderTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val tag = "MainActivity"

    private var showOverlayStartDialog by mutableStateOf(false)
    private var showAccessibilityStartDialog by mutableStateOf(false)

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(tag, "Permission result: code=${result.resultCode} hasData=${result.data != null}")
        val grantData = result.data
        if (result.resultCode == RESULT_OK && grantData != null) {
            ScreenRecorderService.setGrantData(result.resultCode, grantData)
            viewModel.onMediaProjectionGranted()
        } else {
            viewModel.onMediaProjectionDenied()
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val recordingStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ScreenRecorderService.ACTION_STOPPED) {
                Log.d(tag, "Recording stopped broadcast received")
                viewModel.onRecordingStopped()
            }
        }
    }

    private fun registerRecordingStoppedReceiver() {
        val filter = IntentFilter(ScreenRecorderService.ACTION_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordingStoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(recordingStoppedReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(recordingStoppedReceiver)
        } catch (e: Exception) {
            Log.e(tag, "unregister recordingStoppedReceiver error", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate()")
        checkNotificationPermission()
        NotificationChannels.createChannels(this)
        registerRecordingStoppedReceiver()
        handleOpenScreen(intent)
        setContent {
            var themeMode by remember {
                mutableStateOf(RecordingPreferences.getThemeMode(this@MainActivity))
            }
            val prefs = getSharedPreferences("recording_prefs", MODE_PRIVATE)
            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "theme_mode") {
                        themeMode = RecordingPreferences.getThemeMode(this@MainActivity)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            ScreenRecorderTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val recordingState by viewModel.state.collectAsStateWithLifecycle()
                    val currentScreen by viewModel.screen.collectAsStateWithLifecycle()
                    val trashStore = remember {
                        TrashStore(File(this@MainActivity.filesDir, "trash.json"))
                    }

                    LaunchedEffect(recordingState) {
                        if (recordingState == RecordingState.RECORDING) {
                            Toast.makeText(
                                this@MainActivity,
                                "Recording started",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    val isCountdown = recordingState == RecordingState.COUNTDOWN

                    LaunchedEffect(currentScreen) {
                        FloatingOverlayService.collapse(this@MainActivity)
                    }

                    DisposableEffect(isCountdown) {
                        if (!isCountdown) {
                            return@DisposableEffect onDispose { }
                        }
                        val screenOffReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                                    Log.d(tag, "Screen off during countdown - cancelling countdown")
                                    viewModel.onCountdownCancelled()
                                    ScreenRecorderService.clearPendingGrant()
                                }
                            }
                        }
                        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_EXPORTED)
                        } else {
                            registerReceiver(screenOffReceiver, filter)
                        }
                        onDispose {
                            try {
                                unregisterReceiver(screenOffReceiver)
                            } catch (e: Exception) {
                                Log.e(tag, "unregister screenOffReceiver error", e)
                            }
                        }
                    }

                    LaunchedEffect(recordingState) {
                        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
                            var lastSeenState = RecorderRuntime.state
                            while (RecorderRuntime.state == RecordingState.RECORDING || RecorderRuntime.state == RecordingState.PAUSED) {
                                if (RecorderRuntime.state != lastSeenState) {
                                    lastSeenState = RecorderRuntime.state
                                    when (RecorderRuntime.state) {
                                        RecordingState.PAUSED -> viewModel.onRecordingPaused()
                                        RecordingState.RECORDING -> viewModel.onRecordingResumed()
                                        else -> {}
                                    }
                                }
                                delay(500)
                            }
                            viewModel.checkRecordingJustStopped()
                        }
                    }

                    val screenContent: @Composable () -> Unit = {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                Crossfade(
                                    targetState = currentScreen,
                                    animationSpec = tween(300)
                                ) { screen ->
                                    when (screen) {
                                        Screen.HOME -> HomeScreen(
                                            trashStore = trashStore,
                                            onStartClick = { onStartRecording() },
                                            onRecordingClick = { filePath ->
                                                PlaybackActivity.start(this@MainActivity, filePath)
                                            },
                                            onEditClick = { filePath ->
                                                ZoomEditorActivity.start(this@MainActivity, filePath)
                                            },
                                            onPauseClick = {
                                                Intent(this@MainActivity, ScreenRecorderService::class.java).apply {
                                                    action = ScreenRecorderService.ACTION_PAUSE
                                                    startService(this)
                                                }
                                            },
                                            onResumeClick = {
                                                Intent(this@MainActivity, ScreenRecorderService::class.java).apply {
                                                    action = ScreenRecorderService.ACTION_RESUME
                                                    startService(this)
                                                }
                                            },
                                            onStopClick = {
                                                Intent(this@MainActivity, ScreenRecorderService::class.java).apply {
                                                    action = ScreenRecorderService.ACTION_STOP
                                                    startService(this)
                                                }
                                            }
                                        )
                                        Screen.EDIT -> EditScreen(
                                            trashStore = trashStore,
                                            onRecordingClick = { filePath ->
                                                ZoomEditorActivity.start(this@MainActivity, filePath)
                                            }
                                        )
                                        Screen.SETTINGS -> SettingsScreen(
                                            recordingState = recordingState,
                                            onBackClick = { viewModel.closeSettings() }
                                        )
                                        Screen.TRASH -> TrashScreen(
                                            trashStore = trashStore,
                                            onBackClick = { viewModel.closeTrash() }
                                        )
                                    }
                                }
                            }
                            BottomNavBar(
                                current = currentScreen,
                                onSelect = viewModel::navigateTo
                            )
                        }
                    }

                    when (recordingState) {
                        RecordingState.COUNTDOWN -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                screenContent()
                                CountdownScreen(onCountdownFinished = ::onCountdownFinished)
                            }
                        }
                        RecordingState.STARTING, RecordingState.RECORDING, RecordingState.PAUSED -> screenContent()
                        RecordingState.IDLE -> screenContent()
                    }

                    if (showOverlayStartDialog) {
                        AlertDialog(
                            onDismissRequest = { showOverlayStartDialog = false },
                            title = { Text("Allow Floating Controls") },
                            text = {
                                Text(
                                    "Floating controls need the \"Display over other apps\" " +
                                        "permission. Allow it to see controls on screen, or " +
                                        "continue and control recording from the notification."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showOverlayStartDialog = false
                                    PermissionManager.openOverlaySettings(this@MainActivity)
                                }) {
                                    Text("Go to Settings")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showOverlayStartDialog = false
                                    RecordingPreferences.setOverlayStartDenied(this@MainActivity, true)
                                    startScreenCapture()
                                }) {
                                    Text("Control from Notification Only")
                                }
                            }
                        )
                    }

                    if (showAccessibilityStartDialog) {
                        AlertDialog(
                            onDismissRequest = { showAccessibilityStartDialog = false },
                            title = { Text("Touch Capture Is Off") },
                            text = {
                                Text(
                                    "Touch & interaction capture is enabled, but the accessibility " +
                                        "service is not on in your device settings. Enable it so your " +
                                        "taps can be recorded for highlights and auto-zoom."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showAccessibilityStartDialog = false
                                    PermissionManager.openAccessibilitySettings(this@MainActivity)
                                }) {
                                    Text("Open Settings")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showAccessibilityStartDialog = false
                                    startScreenCapture()
                                }) {
                                    Text("Continue Without")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(tag, "onResume() state=${viewModel.state.value}")
        viewModel.checkRecordingJustStopped()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenScreen(intent)
    }

    private fun handleOpenScreen(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN_SCREEN) == VALUE_SCREEN_SETTINGS) {
            viewModel.openSettings()
        } else if (intent?.action == Intent.ACTION_MAIN && intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true) {
            Log.d(tag, "Launcher tap - keeping current screen")
        } else {
            viewModel.closeSettings()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_OPEN_SCREEN = "open_screen"
        const val VALUE_SCREEN_SETTINGS = "settings"
    }

    private fun onStartRecording() {
        Log.d(tag, "onStartRecording()")
        if (PermissionManager.decideAccessibilityStartAction(
                touchCaptureEnabled = RecordingPreferences.isTouchCaptureEnabled(this),
                serviceEnabled = PermissionManager.isAccessibilityServiceEnabled(this)
            ) == AccessibilityStartAction.PROMPT_TO_ENABLE
        ) {
            showAccessibilityStartDialog = true
            return
        }
        val decision = PermissionManager.decideOverlayStartAction(
            mode = RecordingPreferences.getRecordingMode(this),
            hasOverlayPermission = PermissionManager.hasOverlayPermission(this),
            denialRemembered = RecordingPreferences.isOverlayStartDenied(this)
        )
        if (decision == OverlayStartAction.ASK_PERMISSION) {
            showOverlayStartDialog = true
            return
        }
        startScreenCapture()
    }

    private fun startScreenCapture() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mgr.createScreenCaptureIntent()
        }
        mediaProjectionLauncher.launch(intent)
    }

    private fun onCountdownFinished() {
        if (viewModel.state.value != RecordingState.COUNTDOWN) {
            Log.d(tag, "Countdown finished but state=${viewModel.state.value} - ignoring")
            return
        }
        Log.d(tag, "=== onCountdownFinished() ===")
        viewModel.onCountdownPreparing()
        try {
            ScreenRecorderService.startRecording(this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start recording service - resetting to idle", e)
            viewModel.onStartFailed()
            return
        }
        viewModel.onRecordingStarted()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    current: Screen,
    onSelect: (Screen) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = current == Screen.HOME,
            onClick = { onSelect(Screen.HOME) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = null,
                    tint = if (current == Screen.HOME) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = current == Screen.EDIT,
            onClick = { onSelect(Screen.EDIT) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = if (current == Screen.EDIT) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = { Text("Edit") }
        )
        NavigationBarItem(
            selected = current == Screen.SETTINGS,
            onClick = { onSelect(Screen.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = if (current == Screen.SETTINGS) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = { Text("Settings") }
        )
        NavigationBarItem(
            selected = current == Screen.TRASH,
            onClick = { onSelect(Screen.TRASH) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = if (current == Screen.TRASH) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            label = { Text("Trash") }
        )
    }
}
