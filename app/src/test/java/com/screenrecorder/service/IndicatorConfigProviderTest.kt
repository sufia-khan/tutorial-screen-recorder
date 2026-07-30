package com.screenrecorder.service

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IndicatorConfigProviderTest {

    @Before
    fun setUp() {
        IndicatorConfigProvider.reset()
    }

    @Test
    fun defaultsAreCorrect() {
        val config = IndicatorConfigProvider.get()
        assertEquals("#FF808080", config.color)
        assertEquals("circle", config.shape)
        assertEquals(24, config.sizeDp)
    }

    @Test
    fun updateChangesConfig() {
        val newConfig = IndicatorConfig(color = "#FF0000", shape = "square", sizeDp = 32)
        IndicatorConfigProvider.update(newConfig)
        val config = IndicatorConfigProvider.get()
        assertEquals("#FF0000", config.color)
        assertEquals("square", config.shape)
        assertEquals(32, config.sizeDp)
    }

    @Test
    fun updateOverridesAllFields() {
        IndicatorConfigProvider.update(IndicatorConfig(color = "#00FF00", shape = "ripple", sizeDp = 16))
        IndicatorConfigProvider.update(IndicatorConfig(color = "#0000FF", shape = "square", sizeDp = 32))
        val config = IndicatorConfigProvider.get()
        assertEquals("#0000FF", config.color)
        assertEquals("square", config.shape)
        assertEquals(32, config.sizeDp)
    }
}
