package com.example.hermes.core.mobilecontrol

object LinkedInDraftRules {
    /** Known exported ACTION_SEND post entry point. Unknown versions fall back to Android's chooser. */
    fun postActivity(candidates: List<String>): String? = candidates.singleOrNull {
        it == "com.linkedin.android.publishing.sharing.SharingDeepLinkActivity"
    }
    const val COMPOSER_FIELD = "com.linkedin.android:id/share_compose_text_input_entities"
    fun captionField(packageName: String?, viewId: String?, editable: Boolean, visible: Boolean): Boolean =
        packageName == "com.linkedin.android" && viewId == COMPOSER_FIELD && editable && visible
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
