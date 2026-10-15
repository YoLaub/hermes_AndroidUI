package com.example.hermes.core.security

import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryOwnerTokenSecretTest {
    @Test
    fun theOwnerTokenIsAlwaysStoredSealed() {
        assertTrue("memory_owner_token" in SecretKeys.NAMES)
    }
}
