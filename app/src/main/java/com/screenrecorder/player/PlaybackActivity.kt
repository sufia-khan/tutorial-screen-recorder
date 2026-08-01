package com.screenrecorder.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.ui.player.VideoPlayer
import com.screenrecorder.ui.theme.ScreenRecorderTheme
import java.io.File

class PlaybackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        setContent {
            ScreenRecorderTheme(themeMode = RecordingPreferences.getThemeMode(this)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlaybackScreen(
                        filePath = filePath,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_FILE_PATH = "file_path"

        fun start(context: android.content.Context, filePath: String) {
            val intent = Intent(context, PlaybackActivity::class.java)
                .putExtra(EXTRA_FILE_PATH, filePath)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun PlaybackScreen(
    filePath: String?,
    onBackClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                text = File(filePath ?: "").name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        VideoPlayer(
            filePath = filePath,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
