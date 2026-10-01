package com.example.hermes.core.network

import com.example.hermes.core.model.OpenBaoHealth
import com.example.hermes.core.model.OpenBaoHealthResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenBaoClient(
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(baseUrl: String): Result<OpenBaoHealth> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val request = Request.Builder()
            .url("$cleanUrl/v1/sys/health")
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                // OpenBao /sys/health returns:
                // 200: initialized, unsealed, and active
                // 429: unsealed and standby
                // 472: disaster recovery mode replication secondary and active
                // 473: performance standby
                // 501: not initialized
                // 503: sealed
                if (response.code in listOf(200, 429, 472, 473, 501, 503)) {
                    val parsed = json.decodeFromString<OpenBaoHealthResponse>(body)
                    Result.success(
                        OpenBaoHealth(
                            initialized = parsed.initialized,
                            sealed = parsed.sealed,
                            standby = parsed.standby,
                            version = parsed.version ?: ""
                        )
                    )
                } else {
                    Result.failure(IOException("HTTP ${response.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSecrets(
        baseUrl: String,
        token: String,
        mount: String,
        path: String
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val cleanMount = mount.trim().trim('/')
        val cleanPath = path.trim().trim('/')

        // 1. Try KV v2: /v1/{mount}/data/{path}
        val v2Url = "$cleanUrl/v1/$cleanMount/data/$cleanPath"
        val request = Request.Builder()
            .url(v2Url)
            .header("X-Vault-Token", token.trim())
            .get()
            .build()

        var tryFallback = false
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val root = json.parseToJsonElement(body).jsonObject
                    val dataObj = root["data"]?.jsonObject?.get("data")?.jsonObject
                    if (dataObj != null) {
                        val map = dataObj.mapValues { (_, v) ->
                            if (v is JsonPrimitive) v.content else v.toString()
                        }
                        return@withContext Result.success(map)
                    }
                } else if (response.code == 404) {
                    tryFallback = true
                } else {
                    val err = extractErrorMessage(body) ?: "HTTP ${response.code}"
                    return@withContext Result.failure(IOException(err))
                }
            }
        } catch (e: Exception) {
            tryFallback = true
        }

        if (tryFallback) {
            // 2. Try KV v1: /v1/{mount}/{path}
            val v1Url = "$cleanUrl/v1/$cleanMount/$cleanPath"
            val v1Request = Request.Builder()
                .url(v1Url)
                .header("X-Vault-Token", token.trim())
                .get()
                .build()

            try {
                okHttpClient.newCall(v1Request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val root = json.parseToJsonElement(body).jsonObject
                        val dataObj = root["data"]?.jsonObject ?: root
                        val map = dataObj.mapValues { (_, v) ->
                            if (v is JsonPrimitive) v.content else v.toString()
                        }
                        return@withContext Result.success(map)
                    } else if (response.code == 404) {
                        // Path simply doesn't exist yet, return empty map!
                        return@withContext Result.success(emptyMap())
                    } else {
                        val err = extractErrorMessage(body) ?: "HTTP ${response.code}"
                        return@withContext Result.failure(IOException(err))
                    }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        Result.success(emptyMap())
    }

    suspend fun saveSecretKey(
        baseUrl: String,
        token: String,
        mount: String,
        path: String,
        key: String,
        value: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val existing = getSecrets(baseUrl, token, mount, path).getOrElse { emptyMap() }
        val updated = existing.toMutableMap()
        // Ensure TEST_CONNECTION=ok is always preserved for gateway startup checks
        if (updated["TEST_CONNECTION"] != "ok") {
            updated["TEST_CONNECTION"] = "ok"
        }
        val formattedKey = key.trim().uppercase()
        updated[formattedKey] = value.trim()
        writeAllSecrets(baseUrl, token, mount, path, updated)
    }

    suspend fun deleteSecretKey(
        baseUrl: String,
        token: String,
        mount: String,
        path: String,
        key: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val formattedKey = key.trim().uppercase()
        if (formattedKey == "TEST_CONNECTION") {
            return@withContext Result.failure(
                IllegalArgumentException("TEST_CONNECTION=ok est obligatoire pour le démarrage de la gateway et ne peut pas être supprimé.")
            )
        }
        val existing = getSecrets(baseUrl, token, mount, path).getOrElse { emptyMap() }
        val updated = existing.toMutableMap()
        // Ensure TEST_CONNECTION=ok is maintained
        if (updated["TEST_CONNECTION"] != "ok") {
            updated["TEST_CONNECTION"] = "ok"
        }
        updated.remove(formattedKey)
        writeAllSecrets(baseUrl, token, mount, path, updated)
    }

    private suspend fun writeAllSecrets(
        baseUrl: String,
        token: String,
        mount: String,
        path: String,
        secrets: Map<String, String>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val cleanMount = mount.trim().trim('/')
        val cleanPath = path.trim().trim('/')

        // Try KV v2: /v1/{mount}/data/{path} with {"data": {...}}
        val v2Url = "$cleanUrl/v1/$cleanMount/data/$cleanPath"
        val v2Payload = buildJsonObject {
            put("data", buildJsonObject {
                secrets.forEach { (k, v) -> put(k, v) }
            })
        }
        val v2Request = Request.Builder()
            .url(v2Url)
            .header("X-Vault-Token", token.trim())
            .post(v2Payload.toString().toRequestBody(jsonMediaType))
            .build()

        var tryFallback = false
        try {
            okHttpClient.newCall(v2Request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    return@withContext Result.success(true)
                } else if (response.code == 404) {
                    tryFallback = true
                } else {
                    val err = extractErrorMessage(body) ?: "HTTP ${response.code}"
                    return@withContext Result.failure(IOException(err))
                }
            }
        } catch (e: Exception) {
            tryFallback = true
        }

        if (tryFallback) {
            // Try KV v1: /v1/{mount}/{path} with {...}
            val v1Url = "$cleanUrl/v1/$cleanMount/$cleanPath"
            val v1Payload = buildJsonObject {
                secrets.forEach { (k, v) -> put(k, v) }
            }
            val v1Request = Request.Builder()
                .url(v1Url)
                .header("X-Vault-Token", token.trim())
                .post(v1Payload.toString().toRequestBody(jsonMediaType))
                .build()

            try {
                okHttpClient.newCall(v1Request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        return@withContext Result.success(true)
                    } else {
                        val err = extractErrorMessage(body) ?: "HTTP ${response.code}"
                        return@withContext Result.failure(IOException(err))
                    }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        Result.success(true)
    }

    private fun extractErrorMessage(body: String): String? {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val errors = root["errors"]?.jsonArray
            errors?.firstOrNull()?.jsonPrimitive?.content ?: root["error"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}
