package com.screenrecorder.session

import android.content.Context
import com.screenrecorder.session.model.InteractionEvent
import com.screenrecorder.session.model.MetadataFile
import com.screenrecorder.session.model.RecordingMetadata
import com.screenrecorder.session.model.RecordingSession
import com.screenrecorder.session.model.SessionStatus
import com.screenrecorder.session.model.VideoMetadata
import com.screenrecorder.session.serialization.SessionJsonCodec
import java.io.File
import java.util.UUID

class RecordingSessionManager(private val rootDir: File) {

    fun loadMetadata(session: RecordingSession): MetadataFile? = readMetadata(session.directory)

    fun createSession(createdAtMs: Long = System.currentTimeMillis()): RecordingSession {
        val sessionId = UUID.randomUUID()
        val recordingId = UUID.randomUUID()
        val directory = File(rootDir, sessionId.toString())
        if (!directory.mkdirs()) {
            throw IllegalStateException("Cannot create session directory: $directory")
        }
        val exportsDirectory = File(directory, EXPORTS_DIR_NAME).apply { mkdirs() }
        val interactionsFile = File(directory, INTERACTIONS_FILE_NAME)
        interactionsFile.writeText(SessionJsonCodec.encodeInteractions(emptyList()))
        val session = RecordingSession(
            sessionId = sessionId,
            recordingId = recordingId,
            directory = directory,
            videoPath = File(directory, VIDEO_FILE_NAME),
            thumbnailPath = File(directory, THUMBNAIL_FILE_NAME),
            metadataPath = File(directory, METADATA_FILE_NAME),
            interactionsPath = interactionsFile,
            exportsDirectory = exportsDirectory,
            createdAtMs = createdAtMs,
            status = SessionStatus.RECORDING
        )
        writeMetadata(
            session,
            MetadataFile(
                recordingMetadata = RecordingMetadata(
                    sessionId = sessionId,
                    recordingId = recordingId,
                    createdAtMs = createdAtMs,
                    status = SessionStatus.RECORDING
                )
            )
        )
        return session
    }

    fun finalizeSession(
        session: RecordingSession,
        metadataFile: MetadataFile
    ): RecordingSession {
        val finalized = session.update(SessionStatus.COMPLETED)
        writeMetadata(finalized, metadataFile.withStatus(finalized.status))
        return finalized
    }

    fun markFailed(session: RecordingSession): RecordingSession {
        val failed = session.update(SessionStatus.FAILED)
        val minimal = MetadataFile(
            videoMetadata = VideoMetadata(),
            recordingMetadata = RecordingMetadata(
                sessionId = session.sessionId,
                recordingId = session.recordingId,
                createdAtMs = session.createdAtMs
            )
        )
        writeMetadata(failed, minimal.withStatus(failed.status))
        return failed
    }

    fun saveThumbnail(session: RecordingSession, jpegBytes: ByteArray) {
        session.thumbnailPath.writeBytes(jpegBytes)
    }

    fun saveInteractions(session: RecordingSession, events: List<InteractionEvent>) {
        session.interactionsPath.writeText(SessionJsonCodec.encodeInteractions(events))
    }

    fun loadSession(sessionId: UUID): RecordingSession? {
        val directory = File(rootDir, sessionId.toString())
        if (!directory.isDirectory) return null
        val metadata = readMetadata(directory) ?: return null
        return sessionFrom(directory, metadata)
    }

    fun loadAllSessions(): List<RecordingSession> {
        val directories = rootDir.listFiles { it.isDirectory } ?: return emptyList()
        return directories
            .mapNotNull { directory ->
                readMetadata(directory)?.let { metadata -> sessionFrom(directory, metadata) }
            }
            .sortedByDescending { it.createdAtMs }
    }

    fun updateSession(session: RecordingSession) {
        val current = readMetadata(session.directory)
            ?: throw IllegalStateException("Cannot update session without metadata: ${session.directory}")
        writeMetadata(session, current.withStatus(session.status))
    }

    private fun writeMetadata(session: RecordingSession, metadataFile: MetadataFile) {
        session.metadataPath.writeText(SessionJsonCodec.encodeMetadata(metadataFile))
    }

    private fun readMetadata(directory: File): MetadataFile? {
        val metadataFile = File(directory, METADATA_FILE_NAME)
        if (!metadataFile.isFile) return null
        return try {
            SessionJsonCodec.decodeMetadata(metadataFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun sessionFrom(directory: File, metadata: MetadataFile): RecordingSession {
        val recordingMetadata = metadata.recordingMetadata
        return RecordingSession(
            sessionId = recordingMetadata.sessionId,
            recordingId = recordingMetadata.recordingId,
            directory = directory,
            videoPath = File(directory, VIDEO_FILE_NAME),
            thumbnailPath = File(directory, THUMBNAIL_FILE_NAME),
            metadataPath = File(directory, METADATA_FILE_NAME),
            interactionsPath = File(directory, INTERACTIONS_FILE_NAME),
            exportsDirectory = File(directory, EXPORTS_DIR_NAME),
            createdAtMs = recordingMetadata.createdAtMs,
            status = recordingMetadata.status
        )
    }

    private fun MetadataFile.withStatus(status: SessionStatus): MetadataFile =
        copy(recordingMetadata = recordingMetadata.copy(status = status))

    companion object {
        const val SESSIONS_DIR_NAME = "sessions"
        const val VIDEO_FILE_NAME = "recording.mp4"
        const val THUMBNAIL_FILE_NAME = "thumbnail.jpg"
        const val METADATA_FILE_NAME = "metadata.json"
        const val INTERACTIONS_FILE_NAME = "interactions.json"
        const val EXPORTS_DIR_NAME = "exports"

        fun sessionsRoot(context: Context): File =
            File(context.getExternalFilesDir(null), SESSIONS_DIR_NAME)

        fun forContext(context: Context): RecordingSessionManager =
            RecordingSessionManager(sessionsRoot(context))
    }
}
