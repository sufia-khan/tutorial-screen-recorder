package com.screenrecorder.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.ui.player.PlayerControlsRow
import com.screenrecorder.ui.player.PlayerMessageBox
import com.screenrecorder.ui.player.PlayerSurface
import com.screenrecorder.ui.player.formatPlaybackTime
import com.screenrecorder.ui.player.rememberPlaybackUiState
import com.screenrecorder.ui.player.rememberPlayerForFile
import com.screenrecorder.ui.theme.ScreenRecorderTheme
import com.screenrecorder.zoom.ZoomEngine
import com.screenrecorder.zoom.ZoomExporter
import com.screenrecorder.zoom.ZoomSegment
import com.screenrecorder.zoom.ZoomSegmentStore
import java.io.File

class ZoomEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        setContent {
            ScreenRecorderTheme(themeMode = RecordingPreferences.getThemeMode(this)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ZoomEditorScreen(
                        filePath = filePath,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_FILE_PATH = "file_path"

        fun start(context: Context, filePath: String) {
            val intent = Intent(context, ZoomEditorActivity::class.java)
                .putExtra(EXTRA_FILE_PATH, filePath)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun ZoomEditorScreen(
    filePath: String?,
    onBackClick: () -> Unit
) {
    val player = rememberPlayerForFile(filePath)

    Column(modifier = Modifier.fillMaxSize()) {
        EditorTopBar(
            title = File(filePath ?: "").name,
            onBackClick = onBackClick
        )

        if (player == null) {
            PlayerMessageBox(
                message = "Video not found",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            return
        }

        val uiState = rememberPlaybackUiState(player)
        val videoFile = File(filePath!!)
        val editorContext = LocalContext.current
        val state = remember(player) {
            ZoomEditorState(ZoomSegmentStore.forVideo(editorContext, videoFile))
        }

        val view = LocalView.current
        SideEffect {
            view.keepScreenOn = uiState.isPlaying
        }

        val selected = state.segments.firstOrNull { it.id == state.selectedSegmentId }

        val zoomTransform = remember(state.segments, uiState.positionMs) {
            ZoomEngine.transformAt(state.segments, uiState.positionMs)
        }
        val zoomPreviewModifier = Modifier.graphicsLayer {
            scaleX = zoomTransform.scale
            scaleY = zoomTransform.scale
            transformOrigin = TransformOrigin(zoomTransform.centerX, zoomTransform.centerY)
            clip = true
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            PlayerSurface(
                player = player,
                modifier = Modifier.fillMaxSize(),
                videoModifier = zoomPreviewModifier
            )
            ZoomFocusOverlay(
                player = player,
                isPlaying = uiState.isPlaying,
                selectedSegment = selected,
                onCenterChange = { x, y, persist ->
                    selected?.let { state.updateCenter(it.id, x, y, persist) }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        PlayerControlsRow(
            player = player,
            uiState = uiState,
            modifier = Modifier.fillMaxWidth()
        )

        ZoomTimeline(
            segments = state.segments,
            durationMs = uiState.durationMs,
            positionMs = uiState.positionMs,
            selectedSegmentId = state.selectedSegmentId,
            onSegmentTap = state::selectSegment,
            onResize = { id, startMs, endMs, persist ->
                state.updateSegmentTimes(id, startMs, endMs, uiState.durationMs, persist)
            },
            onSeekTo = player::seekTo,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                state.addSegmentAt(
                    positionMs = player.currentPosition,
                    videoDurationMs = player.duration
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Zoom")
        }

        val context = LocalContext.current
        var exportState by remember(videoFile) {
            mutableStateOf<ZoomExportState>(ZoomExportState.Idle)
        }
        val currentExportState by rememberUpdatedState(exportState)
        DisposableEffect(videoFile) {
            onDispose {
                (currentExportState as? ZoomExportState.Exporting)?.exporter?.cancel()
            }
        }
        fun startExport() {
            val exporter = ZoomExporter(context, ZoomExporter.outputFileFor(videoFile))
            exporter.onCompleted = { exportState = ZoomExportState.Done }
            exporter.onError = { exportState = ZoomExportState.Failed }
            exporter.start(videoFile, state.segments)
            exportState = ZoomExportState.Exporting(exporter)
        }
        ZoomExportPanel(
            exportState = exportState,
            onExport = ::startExport,
            onCancel = {
                (exportState as? ZoomExportState.Exporting)?.exporter?.cancel()
                exportState = ZoomExportState.Idle
            },
            onShare = { shareVideo(context, ZoomExporter.outputFileFor(videoFile)) },
            modifier = Modifier.fillMaxWidth()
        )

        if (selected != null) {
            ZoomEditPanel(
                segment = selected,
                onScaleChange = { state.updateScale(selected.id, it) },
                onTimesChange = { startMs, endMs ->
                    state.updateSegmentTimes(selected.id, startMs, endMs, uiState.durationMs)
                },
                onDelete = { state.deleteSegment(selected.id) },
                onDone = state::clearSelection,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f)
            )
        } else {
            ZoomSegmentList(
                segments = state.segments,
                onDelete = state::deleteSegment,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ZoomSegmentList(
    segments: List<ZoomSegment>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) {
        Box(
            modifier = modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No zoom points yet.\nPlay the video, then tap Add Zoom.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(segments, key = { it.id }) { segment ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatPlaybackTime(segment.startMs)} – ${formatPlaybackTime(segment.endMs)}  ·  ${segment.scale}x",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onDelete(segment.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete zoom point"
                        )
                    }
                }
            }
        }
    }
}
