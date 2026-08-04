package com.screenrecorder.manager

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File

class RecordingManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputFile: File? = null

    val recordedFilePath: String? get() = outputFile?.absolutePath
    val recordedFile: File? get() = outputFile

    fun setupAndStart(resultCode: Int, data: Intent, outputFile: File) {
        Log.d("RecordingManager", "=== setupAndStart() step 1/6: obtaining MediaProjection ===")
        val projectionManager: MediaProjectionManager
        try {
            projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        } catch (e: Exception) {
            Log.e("RecordingManager", "FAILED at step 1: getSystemService MEDIA_PROJECTION_SERVICE", e)
            throw RuntimeException("Cannot get MediaProjectionManager", e)
        }

        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e("RecordingManager", "FAILED at step 1: getMediaProjection() threw", e)
            throw RuntimeException("getMediaProjection() failed", e)
        }
        Log.d("RecordingManager", "Step 1/6: MediaProjection obtained = ${mediaProjection != null}")

        if (mediaProjection == null) {
            throw RuntimeException("MediaProjection is null")
        }

        Log.d("RecordingManager", "Step 2/6: output file = ${outputFile.absolutePath}")
        this.outputFile = outputFile

        Log.d("RecordingManager", "Step 3/6: getting display metrics")
        val metrics = DisplayMetrics()
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
        } catch (e: Exception) {
            Log.e("RecordingManager", "FAILED at step 3: getRealMetrics", e)
            throw RuntimeException("Cannot get display metrics", e)
        }
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi
        val bitRate = (width * height * 4).coerceIn(4_000_000, 20_000_000)
        Log.d("RecordingManager", "Step 3/6: ${width}x$height @ ${dpi}dpi, bitrate=$bitRate")

        Log.d("RecordingManager", "Step 4/6: creating and preparing MediaRecorder")
        try {
            mediaRecorder = MediaRecorder()
            mediaRecorder!!.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile!!.absolutePath)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(bitRate)
                Log.d("RecordingManager", "Calling MediaRecorder.prepare()...")
                prepare()
            }
            Log.d("RecordingManager", "Step 4/6: MediaRecorder.prepare() OK")
        } catch (e: Exception) {
            Log.e("RecordingManager", "FAILED at step 4: MediaRecorder prepare()", e)
            mediaRecorder?.release()
            mediaRecorder = null
            throw RuntimeException("MediaRecorder prepare failed", e)
        }

        Log.d("RecordingManager", "Step 4b/6: registering MediaProjection callback (required on API 34+)")
        try {
            mediaProjection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d("RecordingManager", "MediaProjection onStop")
                }
            }, null)
            Log.d("RecordingManager", "Step 4b/6: callback registered")
        } catch (e: Exception) {
            Log.e("RecordingManager", "FAILED at step 4b: registerCallback", e)
            mediaRecorder?.release()
            mediaRecorder = null
            throw RuntimeException("MediaProjection callback registration failed", e)
        }

        Log.d("RecordingManager", "Step 5/6: creating VirtualDisplay")
        try {
            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "ScreenRecorder",
                width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder!!.surface,
                null,
                null
            )
            Log.d("RecordingManager", "Step 5/6: VirtualDisplay created")
        } catch (e: Exception) {
            Log.e("RecordingManager", "FAILED at step 5: createVirtualDisplay", e)
            mediaRecorder?.release()
            mediaRecorder = null
            throw RuntimeException("VirtualDisplay creation failed", e)
        }

        Log.d("RecordingManager", "Step 6/6: calling MediaRecorder.start()...")
        try {
            mediaRecorder!!.start()
            Log.d("RecordingManager", "Step 6/6: MediaRecorder.start() OK")
        } catch (e: Exception) {
            Log.e("RecordingManager", "FAILED at step 6: MediaRecorder.start()", e)
            virtualDisplay?.release()
            mediaRecorder?.release()
            virtualDisplay = null
            mediaRecorder = null
            throw RuntimeException("MediaRecorder.start() failed", e)
        }

        Log.d("RecordingManager", "=== setupAndStart() COMPLETE - recording is active ===")
    }

    fun pauseRecording() {
        if (mediaRecorder == null) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try { mediaRecorder?.pause(); Log.d("RecordingManager", "Paused") } catch (e: Exception) { Log.e("RecordingManager", "Pause error", e) }
        }
    }

    fun resumeRecording() {
        if (mediaRecorder == null) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try { mediaRecorder?.resume(); Log.d("RecordingManager", "Resumed") } catch (e: Exception) { Log.e("RecordingManager", "Resume error", e) }
        }
    }

    fun stopRecording(): File? {
        Log.d("RecordingManager", "=== stopRecording() ===")
        val file = outputFile
        try {
            mediaRecorder?.let {
                try { it.stop() } catch (e: Exception) { Log.e("RecordingManager", "MediaRecorder stop error", e) }
                it.release()
                Log.d("RecordingManager", "MediaRecorder stopped and released")
            }
        } catch (e: Exception) { Log.e("RecordingManager", "MediaRecorder release error", e) }
        try { virtualDisplay?.release(); Log.d("RecordingManager", "VirtualDisplay released") }
        catch (e: Exception) { Log.e("RecordingManager", "VirtualDisplay release error", e) }
        try { mediaProjection?.stop(); Log.d("RecordingManager", "MediaProjection stopped") }
        catch (e: Exception) { Log.e("RecordingManager", "MediaProjection stop error", e) }
        mediaRecorder = null
        virtualDisplay = null
        mediaProjection = null
        Log.d("RecordingManager", "Output file: ${file?.absolutePath} exists=${file?.exists()} size=${file?.length()}")
        return file
    }
}