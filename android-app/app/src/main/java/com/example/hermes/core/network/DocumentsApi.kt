package com.example.hermes.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.OutputStream

/** Overrides only the profile of one request; it never changes the signed-in user's current profile. */
data class RequestProfile(val name: String)

@Serializable data class DocumentSpace(val id: String, val name: String, val profile: String = "")
@Serializable data class DocumentSpaces(val spaces: List<DocumentSpace> = emptyList())
@Serializable data class DocumentEntry(val name: String, @SerialName("is_dir") val isDir: Boolean, val size: Long = 0)
@Serializable data class DocumentEntries(val entries: List<DocumentEntry> = emptyList(), val path: String = ".")
@Serializable data class DocumentText(val content: String, val revision: String)
@Serializable data class DocumentRevision(val revision: String)

/** The profile is carried on every operation, including writes and downloads; no mutable profile switch is needed. */
class DocumentsApi(client: OkHttpClient) {
    // Do not forward the WebUI cookie to any redirect target.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }
    private fun request(base: String, profile: String, action: String, space: String = "", path: String = "."): Request.Builder =
        Request.Builder().url("${base.trimEnd('/')}/api/documents".toHttpUrl().newBuilder()
            .addQueryParameter("action", action).addQueryParameter("space", space).addQueryParameter("path", path).build())
            .tag(RequestProfile::class.java, RequestProfile(profile))

    private suspend inline fun <reified T> get(base: String, profile: String, action: String, space: String = "", path: String = "."): T =
        withContext(Dispatchers.IO) {
            client.newCall(request(base, profile, action, space, path).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
                json.decodeFromString<T>(response.body?.string() ?: throw IOException("Réponse vide"))
            }
        }

    suspend fun spaces(base: String, profile: String) = get<DocumentSpaces>(base, profile, "spaces").spaces
    suspend fun list(base: String, profile: String, space: String, path: String) = get<DocumentEntries>(base, profile, "list", space, path).entries
    suspend fun read(base: String, profile: String, space: String, path: String) = get<DocumentText>(base, profile, "read", space, path)
    suspend fun revision(base: String, profile: String, space: String, path: String) = get<DocumentRevision>(base, profile, "revision", space, path).revision

    suspend fun change(base: String, profile: String, action: String, space: String, path: String, fields: Map<String, String> = emptyMap()) =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(fields + mapOf("action" to action, "space" to space, "path" to path))
                .toRequestBody("application/json".toMediaType())
            client.newCall(request(base, profile, action).post(body).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
            }
        }

    suspend fun downloadImage(base: String, profile: String, absolutePath: String, output: OutputStream) = withContext(Dispatchers.IO) {
        client.newCall(request(base, profile, "image", path = absolutePath).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Image inaccessible (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Image vide")
            val limit = 10L * 1024 * 1024
            if (body.contentLength() > limit) throw IOException("Image trop volumineuse")
            body.byteStream().use { input ->
                val buffer = ByteArray(8192); var total = 0L
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    total += n; if (total > limit) throw IOException("Image trop volumineuse")
                    output.write(buffer, 0, n)
                }
                if (total == 0L) throw IOException("Image vide")
            }
        }
    }

    /** The caller owns the output stream (Storage Access Framework); bytes are never loaded all at once. */
    suspend fun download(base: String, profile: String, space: String, path: String, output: OutputStream) = withContext(Dispatchers.IO) {
        client.newCall(request(base, profile, "download", space, path).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Téléchargement refusé (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Réponse vide")
            body.byteStream().use { it.copyTo(output) }
        }
    }
}
