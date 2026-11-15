package com.example.hermes.core.mobilecontrol

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.ClipData
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.example.hermes.core.network.DocumentsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Fetches the image with the phone's own WebUI session, then grants LinkedIn access to this one cache file. */
class LinkedInDraft(
    private val context: Context,
    private val api: DocumentsApi,
    private val composerRoot: () -> AccessibilityNodeInfo? = { com.example.hermes.core.accessibility.HermesAccessibilityService.getInstance()?.rootInActiveWindow },
) {
    data class Prepared(val file: File, val mime: String)

    suspend fun prepare(base: String, absolutePath: String): Prepared = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "linkedin-drafts").apply { mkdirs() }
        directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        val file = File.createTempFile("draft-", ".image", directory)
        try {
            file.outputStream().use { api.downloadImage(base, "john", absolutePath, it) }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            require(LinkedInDraftRules.validImage(options.outMimeType, options.outWidth, options.outHeight)) { "Image JPEG/PNG invalide ou dimensions trop grandes" }
            val named = File(directory, file.nameWithoutExtension + if (options.outMimeType == "image/png") ".png" else ".jpg")
            check(file.renameTo(named)) { "Impossible de préparer le fichier image" }
            Prepared(named, options.outMimeType)
        } catch (e: Exception) { file.delete(); throw e }
    }

    /** Called only after control has ended. LinkedIn presents the actual image/caption for manual publication. */
    suspend fun open(prepared: Prepared, caption: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", prepared.file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = prepared.mime
            setPackage("com.linkedin.android")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData.newUri(context.contentResolver, "Image du brouillon", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.exported && it.activityInfo.packageName == "com.linkedin.android" }
        LinkedInDraftRules.postActivity(handlers.map { it.activityInfo.name })?.let { activity ->
            intent.component = ComponentName("com.linkedin.android", activity)
        }
        context.startActivity(intent)
        if (caption.isNotEmpty()) {
            // LinkedIn may ignore EXTRA_TEXT with an image. Fill only its known post field, after control has ended.
            repeat(32) {
                val root = composerRoot()
                if (root?.packageName?.toString() == "com.linkedin.android") {
                    val fields = root.findAccessibilityNodeInfosByViewId(LinkedInDraftRules.COMPOSER_FIELD)
                        .filter { LinkedInDraftRules.captionField(it.packageName?.toString(), it.viewIdResourceName, it.isEditable, it.isVisibleToUser) }
                    if (fields.size == 1) {
                        val field = fields.single()
                        if (field.text?.toString() == caption) return
                        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, caption) }
                        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                            field.refresh()
                            if (field.text?.toString() == caption) return
                        }
                    }
                }
                delay(250)
            }
            error("Brouillon ouvert, mais le texte n’a pas pu être rempli. Complétez-le manuellement dans LinkedIn.")
        }
    }
}
