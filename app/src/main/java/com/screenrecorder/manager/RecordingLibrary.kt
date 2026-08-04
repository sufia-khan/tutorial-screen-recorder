package com.screenrecorder.manager

import com.screenrecorder.session.RecordingSessionManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedVideo(
    val path: String,
    val displayName: String,
    val durationSeconds: Long
)

object RecordingLibrary {

    private val NAME_PATTERN = Regex("ScreenRecord_(\\d{8}_\\d{6})\\.mp4", RegexOption.IGNORE_CASE)

    fun list(
        sessionsDir: File,
        legacyPrivateDir: File,
        legacyPublicDir: File,
        durationOf: (File) -> Long
    ): List<RecordedVideo> {
        val manager = RecordingSessionManager(sessionsDir)
        val sessionEntries = manager.loadAllSessions()
            .filter { it.videoPath.isFile }
            .mapNotNull { session ->
                val metadata = manager.loadMetadata(session) ?: return@mapNotNull null
                metadata.recordingMetadata.createdAtMs to RecordedVideo(
                    path = session.videoPath.absolutePath,
                    displayName = displayName(metadata.recordingMetadata.createdAtMs),
                    durationSeconds = metadata.videoMetadata.durationMs / 1000
                )
            }

        val legacyEntries = legacyFiles(legacyPrivateDir, legacyPublicDir)
            .map { file ->
                (parseTimestamp(file.name) ?: file.lastModified()) to RecordedVideo(
                    path = file.absolutePath,
                    displayName = displayName(file.name),
                    durationSeconds = durationOf(file)
                )
            }

        return (sessionEntries + legacyEntries)
            .sortedByDescending { it.first }
            .map { it.second }
    }

    private fun legacyFiles(privateDir: File, publicDir: File): List<File> {
        val privateFiles = privateDir.listFiles { f -> f.isFile && isMp4(f) }
            ?.toList()
            ?: emptyList()
        val privateNames = privateFiles.map { it.name }.toSet()
        val publicFallbacks = publicDir.listFiles { f ->
            f.isFile && isMp4(f) && f.name !in privateNames
        }?.toList() ?: emptyList()
        return privateFiles + publicFallbacks
    }

    private fun isMp4(file: File): Boolean =
        file.extension.equals("mp4", ignoreCase = true)

    private fun parseTimestamp(fileName: String): Long? {
        return try {
            val match = NAME_PATTERN.find(fileName) ?: return null
            val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            format.isLenient = false
            format.parse(match.groupValues[1])?.time
        } catch (e: Exception) {
            null
        }
    }

    fun displayName(fileName: String): String {
        val timestamp = parseTimestamp(fileName) ?: return fileName.substringBeforeLast('.')
        return displayName(timestamp)
    }

    fun displayName(createdAtMs: Long): String =
        "Recording \u2022 " + SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(createdAtMs))
}
