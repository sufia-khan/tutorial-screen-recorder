package com.screenrecorder.manager

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerManager {

    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e("TimerManager", "Uncaught timer exception", e)
    }

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)
    private var _elapsedSeconds = 0
    @Volatile
    private var isPaused = false
    private var onTick: ((Int) -> Unit)? = null

    fun start(onTick: (Int) -> Unit) {
        this.onTick = onTick
        _elapsedSeconds = 0
        isPaused = false
        job = scope.launch {
            while (isActive) {
                delay(1000)
                if (!isPaused) {
                    _elapsedSeconds++
                    this@TimerManager.onTick?.invoke(_elapsedSeconds)
                }
            }
        }
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun stop() {
        job?.cancel()
        _elapsedSeconds = 0
        onTick = null
    }
}