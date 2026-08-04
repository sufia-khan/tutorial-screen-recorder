package com.screenrecorder.session.serialization

import com.screenrecorder.session.model.InteractionEvent
import com.screenrecorder.session.model.MetadataFile
import com.screenrecorder.session.model.RecordingMetadata
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object SessionJsonCodec {

    private const val SUPPORTED_SCHEMA_VERSION = RecordingMetadata.CURRENT_SCHEMA_VERSION

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeMetadata(metadataFile: MetadataFile): String =
        json.encodeToString(MetadataFile.serializer(), metadataFile)

    fun decodeMetadata(jsonText: String): MetadataFile? {
        return try {
            val decoded = json.decodeFromString(MetadataFile.serializer(), jsonText)
            if (decoded.recordingMetadata.schemaVersion > SUPPORTED_SCHEMA_VERSION) null else decoded
        } catch (e: Exception) {
            null
        }
    }

    fun encodeInteractions(events: List<InteractionEvent>): String =
        json.encodeToString(ListSerializer(InteractionEvent.serializer()), events)

    fun decodeInteractions(jsonText: String): List<InteractionEvent> {
        return try {
            json.decodeFromString(ListSerializer(InteractionEvent.serializer()), jsonText)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
