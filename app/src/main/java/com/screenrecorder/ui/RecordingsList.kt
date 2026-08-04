package com.screenrecorder.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenrecorder.manager.RecordingLibrary
import com.screenrecorder.manager.RecordedVideo
import com.screenrecorder.manager.TrashStore
import com.screenrecorder.manager.VideoMetadataReader
import com.screenrecorder.session.RecordingSessionManager
import com.screenrecorder.zoom.ZoomSegmentStore
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ThumbnailCache {
    private const val MAX_ENTRIES = 50
    private val cache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > MAX_ENTRIES
    }

    operator fun get(key: String): Bitmap? = cache[key]

    fun put(key: String, bitmap: Bitmap) {
        cache[key] = bitmap
    }
}

@Composable
private fun rememberVideoThumbnail(path: String): Bitmap? {
    var thumbnail by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        thumbnail = ThumbnailCache[path] ?: withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    val frame = retriever.getFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (frame != null) ThumbnailCache.put(path, frame)
                    frame
                } finally {
                    retriever.release()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    return thumbnail
}

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val secs = s % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

@Composable
fun rememberVisibleRecordings(
    trashStore: TrashStore,
    refreshKey: Int
): List<RecordedVideo> {
    val context = LocalContext.current
    val state = produceState(initialValue = emptyList<RecordedVideo>(), key1 = refreshKey) {
        value = withContext(Dispatchers.IO) {
            val privateDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "ScreenRecorder"
            )
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "ScreenRecorder"
            )
            trashStore.purgeExpired(System.currentTimeMillis()) { recording ->
                File(recording.path).delete()
                ZoomSegmentStore.sidecarFileFor(File(recording.path)).delete()
            }
            RecordingLibrary.list(
                sessionsDir = RecordingSessionManager.sessionsRoot(context),
                legacyPrivateDir = privateDir,
                legacyPublicDir = publicDir
            ) { VideoMetadataReader.durationSeconds(it) }
                .filterNot { trashStore.isTrashed(it.path) }
        }
    }
    return state.value
}

@Composable
fun ConfirmTrashDialog(
    video: RecordedVideo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Trash?") },
        text = {
            Text("The video stays on your device. It will be removed from the app after 24 hours.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Move to Trash")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RecordingsList(
    recordings: List<RecordedVideo>,
    onRecordingClick: (String) -> Unit,
    onEditClick: (String) -> Unit = {},
    onDeleteClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (recordings.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No recordings yet",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 4.dp
        )
    ) {
        items(recordings, key = { it.path }) { recording ->
            RecordingRow(
                recording = recording,
                onClick = { onRecordingClick(recording.path) },
                onEditClick = { onEditClick(recording.path) },
                onDeleteClick = { onDeleteClick(recording.path) }
            )
        }
    }
}

@Composable
private fun RecordingRow(
    recording: RecordedVideo,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val thumbnail = rememberVideoThumbnail(recording.path)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 40.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(width = 64.dp, height = 40.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recording.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(recording.durationSeconds),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onEditClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
