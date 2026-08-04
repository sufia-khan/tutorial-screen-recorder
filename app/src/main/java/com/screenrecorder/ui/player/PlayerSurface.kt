package com.screenrecorder.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame

@Composable
fun PlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
    videoModifier: Modifier = Modifier
) {
    var hasError by remember(player) { mutableStateOf(false) }
    val listener = remember(player) {
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
            }
        }
    }

    DisposableEffect(player, listener) {
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    BoxWithConstraints(modifier = modifier.background(Color.Black)) {
        val density = LocalDensity.current
        val videoSize = player.videoSize
        val rect = videoRect(
            viewWidthPx = constraints.maxWidth.toFloat(),
            viewHeightPx = constraints.maxHeight.toFloat(),
            videoWidthPx = videoSize.width,
            videoHeightPx = videoSize.height
        )
        val layerModifier = if (rect != null) {
            Modifier
                .size(
                    with(density) { rect.width.toDp() },
                    with(density) { rect.height.toDp() }
                )
                .offset(
                    x = with(density) { rect.left.toDp() },
                    y = with(density) { rect.top.toDp() }
                )
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = layerModifier.then(videoModifier)) {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (hasError) {
            PlayerMessageBox(
                message = "Could not play this video",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun PlayerMessageBox(message: String, modifier: Modifier = Modifier) {
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
