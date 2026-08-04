package com.screenrecorder.session

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import com.screenrecorder.session.model.VideoMetadata
import java.io.ByteArrayOutputStream
import java.io.File

object SessionMetadataExtractor {

    fun videoMetadataOf(file: File): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            return VideoMetadata(
                durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0,
                width = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0,
                height = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0,
                frameRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        ?.toIntOrNull()
                } else {
                    null
                },
                bitrate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()
                } else {
                    null
                },
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                fileSizeBytes = file.length(),
                orientationDegrees = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0,
                videoFilePath = file.absolutePath
            )
        } catch (e: Exception) {
            return VideoMetadata(
                fileSizeBytes = file.length(),
                videoFilePath = file.absolutePath
            )
        } finally {
            retriever.release()
        }
    }

    fun thumbnailJpegBytes(file: File, quality: Int = 85): ByteArray? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(
                durationMs / 2,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            val out = ByteArrayOutputStream()
            frame.compress(Bitmap.CompressFormat.JPEG, quality, out)
            frame.recycle()
            return out.toByteArray()
        } catch (e: Exception) {
            return null
        } finally {
            retriever.release()
        }
    }
}
