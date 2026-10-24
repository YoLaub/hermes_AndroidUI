package com.example.hermes.features.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvKeyTest {
    @Test
    fun `typing is turned into an env name`() {
        assertEquals("OPENAI_API_KEY", sanitizeEnvKey("openai-api key"))
        assertEquals("CLE", sanitizeEnvKey("clé")) // accents are dropped, not kept as letters
        assertEquals("A_B", sanitizeEnvKey("a.b"))
    }

    @Test
    fun `a name is upper case letters, digits and underscores, not starting with a digit`() {
        assertNull(envKeyError("OPENAI_API_KEY"))
        assertNull(envKeyError("_PRIVATE"))
        assertEquals(EnvKeyError.Required, envKeyError(""))
        assertEquals(EnvKeyError.StartsWithDigit, envKeyError("1PASSWORD"))
        assertEquals(EnvKeyError.Invalid, envKeyError("lower"))
    }
}
