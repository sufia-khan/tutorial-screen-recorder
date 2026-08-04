package com.screenrecorder.session.model

import kotlinx.serialization.Serializable

@Serializable
data class TimelineInfo(
    val monotonicMs: Long = 0,
    val videoMs: Long = 0
)
