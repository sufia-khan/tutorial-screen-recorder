package com.screenrecorder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max

fun nextCountdownDelayMs(
    stepIndex: Int,
    startTimeNanos: Long,
    nowNanos: Long,
    stepDurationMs: Long = 1000L
): Long {
    val targetTimeNanos = startTimeNanos + (stepIndex + 1) * stepDurationMs * 1_000_000
    val remainingNanos = targetTimeNanos - nowNanos
    return max(0L, remainingNanos / 1_000_000)
}

@Composable
fun CountdownScreen(onCountdownFinished: () -> Unit) {
    var count by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        val startTime = System.nanoTime()
        while (count > 0) {
            val stepIndex = 3 - count
            val delayMs = nextCountdownDelayMs(stepIndex, startTime, System.nanoTime())
            delay(delayMs)
            count--
        }
        onCountdownFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = count > 0,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Text(
                text = count.toString(),
                fontSize = 150.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}