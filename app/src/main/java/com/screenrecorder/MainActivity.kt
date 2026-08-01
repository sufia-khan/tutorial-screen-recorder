package com.screenrecorder

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import com.screenrecorder.manager.PermissionManager
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.manager.ThemeMode
import com.screenrecorder.model.RecordingSession
import com.screenrecorder.model.RecordingState
import com.screenrecorder.service.ScreenRecorderService
import com.screenrecorder.ui.CountdownScreen
import com.screenrecorder.ui.HomeScreen
import com.screenrecorder.ui.SettingsScreen
import com.screenrecorder.ui.theme.ScreenRecorderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val tag = "MainActivity"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate()")
        checkNotificationPermission()
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

                    LaunchedEffect(recordingState) {
                        if (recordingState == RecordingState.RECORDING) {
                            Toast.makeText(
                                this@MainActivity,
                                "Recording started",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    LaunchedEffect(recordingState) {
                        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
                            var lastSeenState = RecordingSession.state
                            while (RecordingSession.state == RecordingState.RECORDING || RecordingSession.state == RecordingState.PAUSED) {
                                if (RecordingSession.state != lastSeenState) {
                                    lastSeenState = RecordingSession.state
                                    when (RecordingSession.state) {
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
                        Crossfade(
                            targetState = currentScreen,
                            animationSpec = tween(300)
                        ) { screen ->
                            when (screen) {
                                Screen.HOME -> HomeScreen(
                                    onStartClick = { onStartRecording() },
                                    onSettingsClick = { viewModel.openSettings() },
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
                                Screen.SETTINGS -> SettingsScreen(
                                    recordingState = recordingState,
                                    onBackClick = { viewModel.closeSettings() }
                                )
                            }
                        }
                    }

                    when (recordingState) {
                        RecordingState.COUNTDOWN -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                screenContent()
                                CountdownScreen(onCountdownFinished = ::onCountdownFinished)
                            }
                        }
                        RecordingState.RECORDING, RecordingState.PAUSED -> screenContent()
                        RecordingState.IDLE -> screenContent()
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
        if (!PermissionManager.hasOverlayPermission(this)) {
            PermissionManager.openOverlaySettings(this)
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show()
            return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mgr.createScreenCaptureIntent()
        }
        mediaProjectionLauncher.launch(intent)
    }

    private fun onCountdownFinished() {
        Log.d(tag, "=== onCountdownFinished() ===")
        ScreenRecorderService.startRecording(this)
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
