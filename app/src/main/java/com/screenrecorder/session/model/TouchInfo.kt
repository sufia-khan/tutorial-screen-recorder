package com.screenrecorder.session.model

import kotlinx.serialization.Serializable

@Serializable
data class TouchInfo(
    val x: Int,
    val y: Int
)
