package com.screenrecorder.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.screenrecorder.manager.RecordingMode
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.manager.RecordedVideo
import com.screenrecorder.manager.TrashStore
import com.screenrecorder.model.RecorderRuntime
import com.screenrecorder.model.RecordingState
import java.io.File
import kotlinx.coroutines.delay
@Composable
fun HomeScreen(
    trashStore: TrashStore,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onRecordingClick: (String) -> Unit = {},
    onEditClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val recordingMode = RecordingPreferences.getRecordingMode(context)

    var session by remember { mutableStateOf(RecorderRuntime.snapshot()) }
    var lastState by remember { mutableStateOf(session.state) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val recordings = rememberVisibleRecordings(trashStore, refreshKey)

    var pendingDelete by remember { mutableStateOf<RecordedVideo?>(null) }

    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        while (true) {
            session = RecorderRuntime.snapshot()
            if (session.state == RecordingState.IDLE && lastState != RecordingState.IDLE) {
                refreshKey++
            }
            lastState = session.state
            delay(200)
        }
    }

    val isRecording = session.state == RecordingState.RECORDING
    val isPaused = session.state == RecordingState.PAUSED
    val isCountdown = session.state == RecordingState.COUNTDOWN

    val pulseAlpha by if (isRecording) {
        rememberInfiniteTransition().animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            RecordingsList(
                recordings = recordings,
                onRecordingClick = onRecordingClick,
                onEditClick = onEditClick,
                onDeleteClick = { path -> pendingDelete = recordings.firstOrNull { it.path == path } },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isRecording || isPaused) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = if (isPaused) "Recording Paused" else "Recording",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = RecorderRuntime.formatElapsed(session.elapsedSeconds),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (isPaused) {
                            Button(
                                onClick = onResumeClick,
                                modifier = Modifier.size(width = 120.dp, height = 48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Resume", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onPauseClick,
                                modifier = Modifier.size(width = 120.dp, height = 48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Pause", fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = onStopClick,
                            modifier = Modifier.size(width = 120.dp, height = 48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(
                    onClick = onStartClick,
                    enabled = !isCountdown,
                    modifier = Modifier.size(width = 260.dp, height = 60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isCountdown) "Starting..." else "Start Recording",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (recordingMode == RecordingMode.CLEAN)
                    "Clean Mode \u2022 No overlay during recording"
                else
                    "Floating Controls \u2022 Tap pill to expand",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Screen Recorder v1.0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }

    pendingDelete?.let { video ->
        ConfirmTrashDialog(
            video = video,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                trashStore.add(File(video.path).name, System.currentTimeMillis())
                pendingDelete = null
                refreshKey++
            }
        )
    }
}
