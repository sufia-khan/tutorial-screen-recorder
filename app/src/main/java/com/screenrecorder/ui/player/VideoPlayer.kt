package com.screenrecorder.ui.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VideoPlayer(filePath: String?, modifier: Modifier = Modifier) {
    if (filePath.isNullOrEmpty() || !File(filePath).exists()) {
        PlayerMessageBox(
            message = "Video not found",
            modifier = modifier
        )
        return
    }

    val context = LocalView.current.context
    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
            prepare()
            playWhenReady = true
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isEnded by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    val listener = remember(player) {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isEnded = playbackState == Player.STATE_ENDED
                durationMs = player.duration.coerceAtLeast(0L)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isPlaying = false
            }
        }
    }

    DisposableEffect(player, listener) {
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!isScrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
            }
            durationMs = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    val view = LocalView.current
    SideEffect {
        view.keepScreenOn = isPlaying
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
            )
            if (hasError) {
                PlayerMessageBox(
                    message = "Could not play this video",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        PlayerControls(
            player = player,
            positionMs = if (isScrubbing) scrubPositionMs else positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            isEnded = isEnded,
            onScrubChange = { scrubPositionMs = it; isScrubbing = true },
            onScrubFinished = {
                player.seekTo(scrubPositionMs)
                isScrubbing = false
            }
        )
    }
}

@Composable
private fun PlayerControls(
    player: Player,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isEnded: Boolean,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: () -> Unit
) {
    val maxPosition = durationMs.coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                when {
                    isEnded -> {
                        player.seekTo(0)
                        player.play()
                    }
                    isPlaying -> player.pause()
                    else -> player.play()
                }
            }
        ) {
            Icon(
                imageVector = when {
                    isEnded -> ReplayIcon
                    isPlaying -> PauseIcon
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = when {
                    isEnded -> "Replay"
                    isPlaying -> "Pause"
                    else -> "Play"
                }
            )
        }

        Text(
            text = formatPlaybackTime(positionMs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Slider(
            value = positionMs.coerceIn(0L, maxPosition).toFloat(),
            onValueChange = { onScrubChange(it.toLong()) },
            onValueChangeFinished = onScrubFinished,
            valueRange = 0f..maxPosition.toFloat(),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        Text(
            text = formatPlaybackTime(durationMs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlayerMessageBox(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(6f, 19f)
            horizontalLineTo(10f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            close()
            moveTo(14f, 5f)
            verticalLineTo(19f)
            horizontalLineTo(18f)
            verticalLineTo(5f)
            close()
        }
    }.build()
}

private val ReplayIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Replay",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 5f)
            verticalLineTo(1f)
            lineTo(7f, 6f)
            lineTo(12f, 11f)
            verticalLineTo(7f)
            curveTo(15.31f, 7f, 18f, 9.69f, 18f, 13f)
            reflectiveCurveTo(15.31f, 19f, 12f, 19f)
            reflectiveCurveTo(6f, 16.31f, 6f, 13f)
            horizontalLineTo(4f)
            curveTo(4f, 17.42f, 7.58f, 21f, 12f, 21f)
            reflectiveCurveTo(20f, 17.42f, 20f, 13f)
            reflectiveCurveTo(16.42f, 5f, 12f, 5f)
            close()
        }
    }.build()
}
