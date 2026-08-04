package com.screenrecorder.session.model

import kotlinx.serialization.Serializable

@Serializable
enum class InteractionType {
    TAP,
    LONG_PRESS,
    SCROLL,
    SCREEN_CHANGE
}
