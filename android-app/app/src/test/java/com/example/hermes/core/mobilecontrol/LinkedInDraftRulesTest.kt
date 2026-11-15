package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test

class LinkedInDraftRulesTest {
    @Test fun onlyJohnInALinkedinSessionCanPrepareTheDraft() {
        assertNull(LinkedInDraftRules.rejection("john", "com.linkedin.android", "/workspace/photo.png", "Texte"))
        assertEquals("PROFILE_DENIED", LinkedInDraftRules.rejection("phone-agent", "com.linkedin.android", "/workspace/a.png", ""))
        assertEquals("APP_NOT_ALLOWED", LinkedInDraftRules.rejection("john", "com.android.settings", "/workspace/a.png", ""))
    }
    @Test fun urlsEscapesAndOversizedCaptionsAreRejected() {
        for (path in listOf("https://evil.test/a.png", "../a.png", "/workspace/../private.png", "")) {
            assertEquals("INVALID_ARGUMENTS", LinkedInDraftRules.rejection("john", "com.linkedin.android", path, ""))
        }
        assertEquals("INVALID_ARGUMENTS", LinkedInDraftRules.rejection("john", "com.linkedin.android", "/workspace/a.png", "x".repeat(3001)))
    }
    @Test fun imageDimensionsMustBeBoundedBeforeDecoding() {
        assertTrue(LinkedInDraftRules.validImage("image/png", 1024, 1024))
        assertFalse(LinkedInDraftRules.validImage("text/html", 100, 100))
        assertFalse(LinkedInDraftRules.validImage("image/jpeg", 100000, 100000))
        assertFalse(LinkedInDraftRules.validImage("image/png", -1, -1))
    }
    @Test fun sharingSelectsThePostActivityAndNeverPrivateMessaging() {
        val post = "com.linkedin.android.publishing.sharing.SharingDeepLinkActivity"
        val message = "com.linkedin.android.publishing.sharing.MessagingDeepLinkActivity"
        assertEquals(post, LinkedInDraftRules.postActivity(listOf(message, post)))
        assertNull(LinkedInDraftRules.postActivity(listOf(message)))
    }
    @Test fun onlyTheKnownLinkedinComposerFieldCanReceiveTheCaption() {
        val field = "com.linkedin.android:id/share_compose_text_input_entities"
        assertTrue(LinkedInDraftRules.captionField("com.linkedin.android", field, true, true))
        assertFalse(LinkedInDraftRules.captionField("com.other", field, true, true))
        assertFalse(LinkedInDraftRules.captionField("com.linkedin.android", "com.linkedin.android:id/search", true, true))
        assertFalse(LinkedInDraftRules.captionField("com.linkedin.android", field, true, false))
    }
}
