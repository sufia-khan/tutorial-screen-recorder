package com.screenrecorder.session.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

@Serializable
data class RecordingMetadata(
    @Serializable(with = UuidSerializer::class)
    val sessionId: UUID = UUID(0, 0),
    @Serializable(with = UuidSerializer::class)
    val recordingId: UUID = UUID(0, 0),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAtMs: Long = 0,
    val startedAtMs: Long = 0,
    val endedAtMs: Long = 0,
    val totalPausedMs: Long = 0,
    val appVersion: String = "",
    val androidVersion: String = "",
    val deviceModel: String = "",
    val status: SessionStatus = SessionStatus.RECORDING
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

object UuidSerializer : KSerializer<UUID> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}
