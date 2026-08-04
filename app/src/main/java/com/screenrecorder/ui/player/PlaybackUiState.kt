package com.screenrecorder.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import kotlinx.coroutines.delay

class PlaybackUiState {
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var isEnded by mutableStateOf(false)
    var isScrubbing by mutableStateOf(false)
    var scrubPositionMs by mutableLongStateOf(0L)
}

@Composable
fun rememberPlaybackUiState(player: Player): PlaybackUiState {
    val state = remember(player) { PlaybackUiState() }
    val listener = remember(player) {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.isEnded = playbackState == Player.STATE_ENDED
                state.durationMs = player.duration.coerceAtLeast(0L)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                state.isPlaying = playing
            }
        }
    }

    DisposableEffect(player, listener) {
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!state.isScrubbing) {
                state.positionMs = player.currentPosition.coerceAtLeast(0L)
            }
            state.durationMs = player.duration.coerceAtLeast(0L)
            delay(if (player.isPlaying) PLAYING_POLL_MS else IDLE_POLL_MS)
        }
    }

    return state
}

private const val PLAYING_POLL_MS = 33L
private const val IDLE_POLL_MS = 250L
