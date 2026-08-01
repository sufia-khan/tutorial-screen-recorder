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
    private var onTick: (() -> Unit)? = null

    fun start(onTick: () -> Unit) {
        this.onTick = onTick
        job = scope.launch {
            while (isActive) {
                delay(1000)
                this@TimerManager.onTick?.invoke()
            }
        }
    }

    fun stop() {
        job?.cancel()
        onTick = null
    }
}
