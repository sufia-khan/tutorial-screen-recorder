package com.screenrecorder.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.screenrecorder.zoom.ZoomEngine
import com.screenrecorder.zoom.ZoomSegment
import java.util.Locale

@Composable
fun ZoomEditPanel(
    segment: ZoomSegment,
    onScaleChange: (Float) -> Unit,
    onTimesChange: (startMs: Long, endMs: Long) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startText by remember(segment.id) { mutableStateOf(formatSeconds(segment.startMs)) }
    var endText by remember(segment.id) { mutableStateOf(formatSeconds(segment.endMs)) }

    LaunchedEffect(segment.id, segment.startMs, segment.endMs) {
        startText = formatSeconds(segment.startMs)
        endText = formatSeconds(segment.endMs)
    }

    fun commitTimes() {
        val startSec = startText.toFloatOrNull()
        val endSec = endText.toFloatOrNull()
        if (startSec != null && endSec != null) {
            onTimesChange((startSec * 1000).toLong(), (endSec * 1000).toLong())
        } else {
            startText = formatSeconds(segment.startMs)
            endText = formatSeconds(segment.endMs)
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Zoom point",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onDone) {
                    Text("Done")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Zoom strength",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = String.format(Locale.US, "%.1fx", segment.scale),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = segment.scale,
                onValueChange = onScaleChange,
                valueRange = ZoomEngine.MIN_SCALE..ZoomEngine.MAX_SCALE,
                steps = 19
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("Start (s)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (!it.isFocused) commitTimes() }
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("End (s)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (!it.isFocused) commitTimes() }
                )
            }

            Button(
                onClick = onDelete,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete Zoom Point")
            }
        }
    }
}

internal fun formatSeconds(ms: Long): String =
    String.format(Locale.US, "%.1f", ms / 1000.0)
