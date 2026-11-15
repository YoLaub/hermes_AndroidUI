package com.example.hermes.core.mobilecontrol

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.example.hermes.core.network.DocumentsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Fetches the image with the phone's own WebUI session, then grants LinkedIn access to this one cache file. */
class LinkedInDraft(private val context: Context, private val api: DocumentsApi) {
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
    fun open(prepared: Prepared, caption: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", prepared.file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = prepared.mime
            setPackage("com.linkedin.android")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData.newUri(context.contentResolver, "Image du brouillon", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
