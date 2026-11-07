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

    private val pdf = ChatAttachment("devis.pdf", "/h/.hermes/attachments/s1/devis.pdf", "application/pdf", 10, false)
    private val jpg = ChatAttachment("photo.jpg", "/h/.hermes/attachments/s1/photo.jpg", "image/jpeg", 10, true)

    @Test
    fun `a non-image attachment path is appended so the agent can open it`() {
        assertEquals(
            "Résume ce devis\n\n[Attached files: /h/.hermes/attachments/s1/devis.pdf]",
            messageWithAttachments("Résume ce devis", listOf(pdf))
        )
    }

    @Test
    fun `images are listed by name, like the WebUI`() {
        assertEquals(
            "Regarde\n\n[Attached files: photo.jpg, /h/.hermes/attachments/s1/devis.pdf]",
            messageWithAttachments("Regarde", listOf(jpg, pdf))
        )
    }

    @Test
    fun `a message with only attachments gets a default sentence`() {
        assertEquals(
            "I've uploaded 1 file(s): /h/.hermes/attachments/s1/devis.pdf",
            messageWithAttachments("", listOf(pdf))
        )
    }

    @Test
    fun `no attachments leaves the text untouched`() {
        assertEquals("salut", messageWithAttachments("salut", emptyList()))
    }

    @Test
    fun `the attachment suffix is hidden when history is displayed`() {
        assertEquals("Résume ce devis", stripAttachmentSuffix("Résume ce devis\n\n[Attached files: /p/devis.pdf]"))
        assertEquals("salut", stripAttachmentSuffix("salut"))
    }
}
