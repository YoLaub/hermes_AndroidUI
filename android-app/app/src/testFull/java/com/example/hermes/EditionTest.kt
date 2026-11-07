package com.example.hermes

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class EditionTest {
    @Test
    fun theFullEditionIsNeverTheAgentPhone() {
        assertFalse(BuildConfig.AGENT_PHONE)
        assertFalse(BuildConfig.APPLICATION_ID.endsWith(".lite"))
    }

    @Test
    fun theFullServiceIsNeverDeclaredAnAccessibilityTool() {
        val config = File("src/main/res/xml/accessibility_service_config.xml").readText()
        assertFalse(config.contains("isAccessibilityTool"))
        assertFalse(File("src/full/res/xml/accessibility_service_config.xml").exists())
    }
}
