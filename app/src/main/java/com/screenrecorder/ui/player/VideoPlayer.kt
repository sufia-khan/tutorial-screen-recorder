package com.screenrecorder.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView

@Composable
fun VideoPlayer(filePath: String?, modifier: Modifier = Modifier) {
    val player = rememberPlayerForFile(filePath)
    if (player == null) {
        PlayerMessageBox(
            message = "Video not found",
            modifier = modifier
        )
        return
    }
    val uiState = rememberPlaybackUiState(player)

    val view = LocalView.current
    SideEffect {
        view.keepScreenOn = uiState.isPlaying
    }

    Column(modifier = modifier) {
        PlayerSurface(
            player = player,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        PlayerControlsRow(
            player = player,
            uiState = uiState,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
