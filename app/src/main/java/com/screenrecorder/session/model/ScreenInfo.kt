package com.screenrecorder.session.model

import kotlinx.serialization.Serializable

@Serializable
data class ScreenInfo(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
)
