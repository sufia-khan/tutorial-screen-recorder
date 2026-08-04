package com.screenrecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.screenrecorder.manager.RecordedVideo
import com.screenrecorder.manager.TrashStore
import java.io.File

@Composable
fun EditScreen(
    trashStore: TrashStore,
    onRecordingClick: (String) -> Unit
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    val recordings = rememberVisibleRecordings(trashStore, refreshKey)

    var pendingDelete by remember { mutableStateOf<RecordedVideo?>(null) }

    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Edit",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        RecordingsList(
            recordings = recordings,
            onRecordingClick = onRecordingClick,
            onEditClick = onRecordingClick,
            onDeleteClick = { path -> pendingDelete = recordings.firstOrNull { it.path == path } },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
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
