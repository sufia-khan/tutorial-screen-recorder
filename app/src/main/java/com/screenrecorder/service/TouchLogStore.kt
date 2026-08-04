package com.screenrecorder.service

import com.screenrecorder.zoom.AutoZoomGenerator

object TouchLogStore {

    private val taps = mutableListOf<AutoZoomGenerator.Tap>()

    fun clear() {
        taps.clear()
    }

    fun add(tap: AutoZoomGenerator.Tap) {
        taps.add(tap)
    }

    fun snapshot(): List<AutoZoomGenerator.Tap> = taps.toList()
}
