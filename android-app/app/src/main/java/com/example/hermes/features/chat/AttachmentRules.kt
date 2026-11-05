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
