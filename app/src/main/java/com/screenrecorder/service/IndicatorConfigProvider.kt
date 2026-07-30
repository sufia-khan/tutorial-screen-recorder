package com.screenrecorder.service

data class IndicatorConfig(
    val color: String = "#FF808080",
    val shape: String = "circle",
    val sizeDp: Int = 24
)

object IndicatorConfigProvider {
    private var config = IndicatorConfig()

    fun get(): IndicatorConfig = config

    fun update(newConfig: IndicatorConfig) {
        config = newConfig
    }

    fun reset() {
        config = IndicatorConfig()
    }
}
