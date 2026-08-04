package com.screenrecorder.session.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionStatus {
    RECORDING,
    COMPLETED,
    FAILED
}
