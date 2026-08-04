package com.screenrecorder.session.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class InteractionEvent(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val type: InteractionType,
    val timeline: TimelineInfo,
    val touch: TouchInfo? = null,
    val element: ElementInfo? = null,
    val screen: ScreenInfo? = null
)
