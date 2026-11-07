package com.example.hermes.features.chat

import com.example.hermes.core.model.ChatAttachment
import com.example.hermes.core.model.UploadResponse

/** Mirrors the WebUI default (HERMES_WEBUI_MAX_UPLOAD_MB = 20). */
const val MAX_ATTACHMENT_BYTES: Long = 20L * 1024 * 1024

fun attachmentSizeError(size: Long): String? =
    if (size > MAX_ATTACHMENT_BYTES) "Fichier trop volumineux (max 20 Mo)" else null

fun resolveAttachmentMime(mime: String?): String =
    mime?.takeIf { it.isNotBlank() } ?: "application/octet-stream"

fun UploadResponse.toChatAttachment() = ChatAttachment(
    filename = filename,
    path = path,
    mime = mime,
    size = size,
    isImage = isImage
)

private const val ATTACHED_FILES_MARKER = "\n\n[Attached files:"

/**
 * The server only embeds images natively; for any other file the agent learns the path from the
 * message text. The WebUI client appends this suffix, so the app must too (images by name, others by path).
 */
fun messageWithAttachments(text: String, attachments: List<ChatAttachment>): String {
    if (attachments.isEmpty()) return text
    val refs = attachments.joinToString(", ") { if (it.isImage) it.filename else it.path.ifBlank { it.filename } }
    return if (text.isBlank()) "I've uploaded ${attachments.size} file(s): $refs"
    else "$text$ATTACHED_FILES_MARKER $refs]"
}

/** Hides the suffix added by [messageWithAttachments] when showing saved history. */
fun stripAttachmentSuffix(content: String): String {
    val i = content.indexOf(ATTACHED_FILES_MARKER)
    return if (i >= 0 && content.trimEnd().endsWith("]")) content.substring(0, i) else content
}
