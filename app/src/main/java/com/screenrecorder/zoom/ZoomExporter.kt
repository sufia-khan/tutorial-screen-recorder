package com.screenrecorder.zoom

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * Re-encodes a recording through Media3 Transformer applying the zoom engine per frame.
 *
 * Note: [cancel] is the caller's responsibility to invoke; Media3 does not deliver any listener
 * callback for cancellation, and the partial output file must be deleted by the caller.
 */
class ZoomExporter(
    context: Context,
    private val outputFile: File
) {

    private val transformer: Transformer

    var onCompleted: ((ExportResult) -> Unit)? = null
    var onError: ((ExportException) -> Unit)? = null

    init {
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                onCompleted?.invoke(exportResult)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                onError?.invoke(exportException)
            }
        }
        transformer = Transformer.Builder(context).addListener(listener).build()
    }

    fun start(videoFile: File, segments: List<ZoomSegment>) {
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(videoFile)))
            .setEffects(
                Effects(
                    listOf<AudioProcessor>(),
                    listOf<Effect>(ZoomGlMatrixTransformation(segments))
                )
            )
            .build()
        transformer.start(editedMediaItem, outputFile.absolutePath)
    }

    /** Returns progress percentage (0..100), or 0 while progress is unavailable. */
    fun progress(): Int {
        val holder = ProgressHolder()
        return if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            holder.progress
        } else {
            0
        }
    }

    /** Cancels the export and removes the partial output file, if any. */
    fun cancel() {
        transformer.cancel()
        outputFile.delete()
    }

    companion object {
        /** e.g. ScreenRecord_1699999999.mp4 -> ScreenRecord_1699999999_zoom.mp4 in the same folder. */
        fun outputFileFor(videoFile: File): File {
            val name = videoFile.name.substringBeforeLast('.', videoFile.name)
            val parent = videoFile.parentFile
            return if (parent != null) File(parent, "${name}_zoom.mp4") else File("${name}_zoom.mp4")
        }
    }
}
