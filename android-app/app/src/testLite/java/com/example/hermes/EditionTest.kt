package com.example.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditionTest {
    @Test
    fun theLiteEditionIsTheAgentPhoneWithItsOwnApplicationId() {
        assertTrue(BuildConfig.AGENT_PHONE)
        assertEquals("com.example.hermes.lite", BuildConfig.APPLICATION_ID)
    }

    /**
     * Android 14+ hides "sensitive" screens (the Wi-Fi page, with its passwords and QR codes) from accessibility
     * services that are not declared as accessibility tools: the agent then reads 0 elements on them. Only the
     * agent-phone edition makes that declaration.
     */
    @Test
    fun theLiteServiceIsDeclaredAnAccessibilityToolSoSensitiveScreensAreReadable() {
        val config = File("src/lite/res/xml/accessibility_service_config.xml")
        assertTrue("lite overlay missing", config.exists())
        assertTrue(config.readText().contains("android:isAccessibilityTool=\"true\""))
    }
}
