package com.screenrecorder.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
fun PlayerControlsRow(
    player: Player,
    uiState: PlaybackUiState,
    modifier: Modifier = Modifier
) {
    val maxPosition = uiState.durationMs.coerceAtLeast(1L)
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                when {
                    uiState.isEnded -> {
                        player.seekTo(0)
                        player.play()
                    }
                    uiState.isPlaying -> player.pause()
                    else -> player.play()
                }
            }
        ) {
            Icon(
                imageVector = when {
                    uiState.isEnded -> ReplayIcon
                    uiState.isPlaying -> PauseIcon
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = when {
                    uiState.isEnded -> "Replay"
                    uiState.isPlaying -> "Pause"
                    else -> "Play"
                }
            )
        }

        Text(
            text = formatPlaybackTime(uiState.positionMs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Slider(
            value = (if (uiState.isScrubbing) uiState.scrubPositionMs else uiState.positionMs)
                .coerceIn(0L, maxPosition)
                .toFloat(),
            onValueChange = {
                uiState.scrubPositionMs = it.toLong()
                uiState.isScrubbing = true
            },
            onValueChangeFinished = {
                player.seekTo(uiState.scrubPositionMs)
                uiState.isScrubbing = false
            },
            valueRange = 0f..maxPosition.toFloat(),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        Text(
            text = formatPlaybackTime(uiState.durationMs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
