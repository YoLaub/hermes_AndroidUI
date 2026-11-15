package com.example.hermes.core.mobilecontrol

object LinkedInDraftRules {
    const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    fun rejection(profile: String, target: String, path: String?, caption: String): String? = when {
        profile != "john" -> "PROFILE_DENIED"
        target != "com.linkedin.android" -> "APP_NOT_ALLOWED"
        path.isNullOrBlank() || !path.startsWith('/') || path.length > 4096 || '\u0000' in path || ".." in path.split('/') ||
            caption.length > 3000 || '\u0000' in caption -> "INVALID_ARGUMENTS"
        else -> null
    }
    fun validImage(mime: String?, width: Int, height: Int): Boolean =
        mime in setOf("image/png", "image/jpeg") && width > 0 && height > 0 && width.toLong() * height <= 25_000_000
}
