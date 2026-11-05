package com.example.hermes.features.chat

import com.example.hermes.core.model.ChatAttachment
import com.example.hermes.core.model.UploadResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentRulesTest {
    @Test
    fun `upload response becomes a chat attachment keeping server path`() {
        val upload = UploadResponse(
            filename = "logs.txt",
            path = "/home/u/.hermes/attachments/s1/logs.txt",
            size = 45120,
            mime = "text/plain",
            isImage = false
        )
        assertEquals(
            ChatAttachment("logs.txt", "/home/u/.hermes/attachments/s1/logs.txt", "text/plain", 45120, false),
            upload.toChatAttachment()
        )
    }

    @Test
    fun `files above the server limit are rejected before upload`() {
        assertNull(attachmentSizeError(MAX_ATTACHMENT_BYTES))
        assertNotNull(attachmentSizeError(MAX_ATTACHMENT_BYTES + 1))
    }

    @Test
    fun `a missing mime type falls back to octet-stream`() {
        assertEquals("application/octet-stream", resolveAttachmentMime(null))
        assertEquals("image/png", resolveAttachmentMime("image/png"))
    }
}
