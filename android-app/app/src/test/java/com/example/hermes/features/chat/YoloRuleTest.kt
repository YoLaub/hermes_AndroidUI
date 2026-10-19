package com.example.hermes.features.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class YoloRuleTest {
    @Test
    fun `turning auto-approval on asks first, turning it off is immediate`() {
        assertEquals(YoloTap.AskToEnable, yoloTap(enabled = false))
        assertEquals(YoloTap.Disable, yoloTap(enabled = true))
    }
}
