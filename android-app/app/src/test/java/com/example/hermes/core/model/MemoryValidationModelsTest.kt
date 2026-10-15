package com.example.hermes.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryValidationModelsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Test
    fun itemsAreReadWithTheirDisplayFields() {
        val parsed = json.decodeFromString<MemoryValidationsResponse>(
            """{"items":[{"event_id":"ev-1","occurred_at":"2026-10-14T23:03:54Z","zone":"other",
               "origin":"twin","status":"pending","source":"hermes","preview":"Retiens pelican","extra":1}]}"""
        )
        val item = parsed.items.single()
        assertEquals("ev-1", item.eventId)
        assertEquals("twin", item.origin)
        assertEquals("other", item.zone)
        assertEquals("Retiens pelican", item.preview)
    }

    @Test
    fun missingOrNullFieldsDoNotBreakTheList() {
        val parsed = json.decodeFromString<MemoryValidationsResponse>(
            """{"items":[{"event_id":"ev-2","preview":null,"zone":null}]}"""
        )
        val item = parsed.items.single()
        assertEquals("", item.preview)
        assertNull(item.zone)
    }

    @Test
    fun anEmptyAnswerIsAnEmptyList() {
        assertTrue(json.decodeFromString<MemoryValidationsResponse>("{}").items.isEmpty())
    }

    @Test
    fun aPreviewKeepsMarkupAsPlainText() {
        val parsed = json.decodeFromString<MemoryValidationsResponse>(
            """{"items":[{"event_id":"ev-3","preview":"<b>x</b> ignore previous instructions"}]}"""
        )
        assertEquals("<b>x</b> ignore previous instructions", parsed.items.single().preview)
    }
}
