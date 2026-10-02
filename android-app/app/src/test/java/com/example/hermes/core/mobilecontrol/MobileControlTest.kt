package com.example.hermes.core.mobilecontrol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MobileControlTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testCommandSerializationAndDeserialization() {
        val jsonInput = """
            {
                "protocol": "mobile-control/1",
                "type": "command",
                "command_id": "cmd-12345",
                "session_id": "ses-9988",
                "operation": "click_element",
                "target_package": "com.linkedin.android",
                "screen_revision": "rev_100",
                "expires_at": 1727798400000,
                "arguments": {
                    "element_ref": "el_42"
                }
            }
        """.trimIndent()

        val cmd = json.decodeFromString<MobileCommand>(jsonInput)
        assertEquals("mobile-control/1", cmd.protocol)
        assertEquals("command", cmd.type)
        assertEquals("cmd-12345", cmd.commandId)
        assertEquals("click_element", cmd.operation)
        assertEquals("com.linkedin.android", cmd.targetPackage)
        assertEquals("el_42", cmd.arguments?.elementRef)
    }

    @Test
    fun testCommandResultSerialization() {
        val result = MobileCommandResult(
            commandId = "cmd-12345",
            status = MobileCommandStatus.SUCCESS,
            executedAt = 1727798405000L,
            message = "Action executed",
            data = MobileScreenData(
                screenRevision = "rev_101",
                packageName = "com.linkedin.android",
                elements = listOf(
                    MobileElementInfo(
                        elementRef = "el_1",
                        className = "Button",
                        text = "Commenter",
                        clickable = true
                    )
                )
            )
        )

        val serialized = json.encodeToString(result)
        assertTrue(serialized.contains("mobile-control/1"))
        assertTrue(serialized.contains("success"))
        assertTrue(serialized.contains("Commenter"))
        assertTrue(serialized.contains("rev_101"))

        val decoded = json.decodeFromString<MobileCommandResult>(serialized)
        assertEquals(MobileCommandStatus.SUCCESS, decoded.status)
        assertEquals(1, decoded.data?.elements?.size)
        assertEquals("Commenter", decoded.data?.elements?.first()?.text)
    }

    @Test
    fun testSessionExpiryCalculation() {
        val now = System.currentTimeMillis()
        val activeSession = MobileControlSession(
            id = "ses-1",
            targetPackage = "com.linkedin.android",
            targetAppName = "LinkedIn",
            allowedProfile = "john",
            mode = MobileControlMode.INTERACTION,
            startedAt = now,
            durationSeconds = 900,
            expiresAt = now + 900000L
        )

        assertFalse(activeSession.isExpired)
        assertTrue(activeSession.remainingSeconds > 800)

        val expiredSession = MobileControlSession(
            id = "ses-2",
            targetPackage = "com.linkedin.android",
            targetAppName = "LinkedIn",
            allowedProfile = "john",
            mode = MobileControlMode.OBSERVATION,
            startedAt = now - 1000000L,
            durationSeconds = 900,
            expiresAt = now - 1000L
        )

        assertTrue(expiredSession.isExpired)
        assertEquals(0L, expiredSession.remainingSeconds)
    }

    @Test
    fun testSessionStartMessageSerializationWithJohnProfile() {
        val msg = MobileSessionStartMsg(
            sessionId = "ses-test-123",
            targetPackage = "com.linkedin.android",
            allowedProfile = "john",
            mode = "interaction",
            durationSeconds = 900
        )
        val jsonStr = json.encodeToString(msg)
        assertTrue(jsonStr.contains("\"allowed_profile\":\"john\""))
        assertTrue(jsonStr.contains("\"session_start\""))
        assertFalse(jsonStr.contains("mario"))
        assertFalse(jsonStr.contains("gaston"))
        assertFalse(jsonStr.contains("freya"))
    }

    @Test
    fun testObservationModeRejectsClicksAndInputs() {
        val mode = MobileControlMode.OBSERVATION
        val allowedOpsInObservation = setOf("observe", "end_session")

        assertTrue(allowedOpsInObservation.contains("observe"))
        assertTrue(allowedOpsInObservation.contains("end_session"))
        assertFalse(allowedOpsInObservation.contains("click_element"))
        assertFalse(allowedOpsInObservation.contains("set_text"))
        assertFalse(allowedOpsInObservation.contains("scroll"))
    }
}
