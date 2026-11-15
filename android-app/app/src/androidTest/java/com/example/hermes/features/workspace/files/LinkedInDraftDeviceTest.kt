package com.example.hermes.features.workspace.files

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hermes.core.mobilecontrol.LinkedInDraft
import com.example.hermes.core.network.AuthInterceptor
import com.example.hermes.core.network.DocumentsApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue

/** Opens a draft with an isolated fixture image; no click on any publish action. */
class LinkedInDraftDeviceTest {
    @Test fun testImageReachesLinkedinComposer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imagePath = InstrumentationRegistry.getArguments().getString("draftImagePath")
        assumeTrue("Needs an isolated workspace fixture image", !imagePath.isNullOrBlank())
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val sender = LinkedInDraft(context, DocumentsApi(OkHttpClient.Builder().addInterceptor(AuthInterceptor()).build())) { automation.rootInActiveWindow }
        val prepared = sender.prepare("http://127.0.0.1:8879", imagePath!!)
        assertEquals("image/png", prepared.mime)
        assertTrue(prepared.file.name.endsWith(".png"))
        val caption = "TEST LOCAL — ne pas publier"
        withContext(Dispatchers.Main) { sender.open(prepared, caption) }
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
        var packageName = ""
        var hasPublish = false
        var hasCaption = false
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            packageName = root?.packageName?.toString().orEmpty()
            if (packageName == "com.linkedin.android") {
                hasPublish = listOf("Publier", "Post").any { root.findAccessibilityNodeInfosByText(it).isNotEmpty() }
                hasCaption = root.findAccessibilityNodeInfosByText(caption).isNotEmpty()
                if (hasPublish) break
            }
            android.os.SystemClock.sleep(250)
        }
        println("LinkedIn composer: package=$packageName publishButton=$hasPublish captionVisible=$hasCaption")
        assertEquals("com.linkedin.android", packageName)
        assertTrue("LinkedIn must present its composer; never press Publish", hasPublish)
        assertTrue("The caption must reach the draft", hasCaption)
    }
}
