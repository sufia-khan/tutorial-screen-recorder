package com.screenrecorder.manager

import android.media.MediaMetadataRetriever
import java.io.File

object VideoMetadataReader {

    fun durationSeconds(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val ms = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                ms / 1000
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            0L
        }
    }
}
