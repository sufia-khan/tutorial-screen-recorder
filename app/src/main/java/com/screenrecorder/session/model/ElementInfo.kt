package com.screenrecorder.session.model

import kotlinx.serialization.Serializable

@Serializable
data class ElementInfo(
    val className: String? = null,
    val packageName: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val clickable: Boolean? = null,
    val enabled: Boolean? = null,
    val focused: Boolean? = null,
    val selected: Boolean? = null,
    val checkable: Boolean? = null,
    val checked: Boolean? = null,
    val left: Int? = null,
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null,
    val centerX: Int? = null,
    val centerY: Int? = null,
    val scrollX: Int? = null,
    val scrollY: Int? = null,
    val maxScrollX: Int? = null,
    val maxScrollY: Int? = null,
    val deltaX: Int? = null,
    val deltaY: Int? = null
)
