package com.example.hermes.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.hermes.ui.components.AvatarSpec
import com.example.hermes.ui.components.centeredSquare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private val Context.avatarStore by preferencesDataStore(name = "agent_avatars")

/**
 * Agent avatars, on this phone only (Hermes WebUI keeps none per profile). The choice is a small JSON
 * per profile id; an image is a 256 × 256 PNG in the app's private files.
 */
class AvatarStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir get() = File(context.filesDir, "avatars").apply { mkdirs() }

    val avatars: Flow<Map<String, AvatarSpec>> = context.avatarStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val id = key.name.removePrefix(PREFIX).takeIf { key.name.startsWith(PREFIX) } ?: return@mapNotNull null
            runCatching { id to json.decodeFromString<AvatarSpec>(value as String) }.getOrNull()
        }.toMap()
    }

    fun imageFile(name: String): File = File(dir, name)

    suspend fun save(profileId: String, spec: AvatarSpec) {
        context.avatarStore.edit { it[stringPreferencesKey(PREFIX + profileId)] = json.encodeToString(AvatarSpec.serializer(), spec) }
    }

    /** Back to the defaults: drops the choice and the image. */
    suspend fun reset(profileId: String) = withContext(Dispatchers.IO) {
        context.avatarStore.edit { it.remove(stringPreferencesKey(PREFIX + profileId)) }
        imageFile(fileNameFor(profileId)).delete()
    }

    /** Reads the picked image, keeps its centred square at 256 px and stores it; returns the file name, or null. */
    suspend fun importImage(profileId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            // Decode at about twice the target to save memory on large photos.
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE * 2) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            val square = centeredSquare(decoded.width, decoded.height)
            val cropped = Bitmap.createBitmap(decoded, square.left, square.top, square.size, square.size)
            val scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
            val name = fileNameFor(profileId)
            imageFile(name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            name
        } catch (e: Exception) {
            Log.w("AvatarStore", "event=avatar_import_failed error=${e.javaClass.simpleName}")
            null
        }
    }

    private fun fileNameFor(profileId: String) = "${profileId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}.png"

    private companion object {
        const val PREFIX = "avatar_"
        const val SIZE = 256
    }
}
