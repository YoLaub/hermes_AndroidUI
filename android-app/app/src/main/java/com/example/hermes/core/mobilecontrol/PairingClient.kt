package com.example.hermes.core.mobilecontrol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HttpReply(val code: Int, val body: String)

/** The one network call pairing needs, behind an interface so the logic is unit-testable. */
interface PairingTransport {
    @Throws(IOException::class)
    suspend fun postJson(url: String, body: String, headers: Map<String, String>): HttpReply
}

class OkHttpPairingTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : PairingTransport {
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): HttpReply =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
            headers.forEach { (k, v) -> builder.addHeader(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                HttpReply(resp.code, resp.body?.string().orEmpty())
            }
        }
}

enum class PairingFailure { INVALID_CODE, ALREADY_REGISTERED, REGISTRATION_CONFLICT, RATE_LIMITED, SERVER_ERROR, UNREACHABLE, BAD_RELAY_URL, BAD_RESPONSE }

sealed interface PairingResult {
    data class Success(val deviceToken: String) : PairingResult
    data class Failure(val reason: PairingFailure, val message: String) : PairingResult
}

@Serializable
private data class PairVerifyRequest(
    val code: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("current_device_token") val currentDeviceToken: String? = null
)

@Serializable
private data class PairVerifyResponse(
    val ok: Boolean = false,
    @SerialName("device_token") val deviceToken: String? = null
)

/**
 * Pairs this phone with the relay: the relay verifies the one-time code and issues the
 * device token. The phone must never invent its own token (the relay would not know it).
 * Messages never contain the code or any token.
 */
class PairingClient(
    private val transport: PairingTransport,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
) {
    suspend fun pair(
        relayUrl: String,
        code: String,
        deviceId: String,
        deviceName: String,
        currentDeviceToken: String? = null
    ): PairingResult {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) {
            return fail(PairingFailure.INVALID_CODE, "Saisissez le code d'appairage.")
        }
        val base = relayUrl.trim().trimEnd('/')
        if (!(base.startsWith("https://") || base.startsWith("http://"))) {
            return fail(PairingFailure.BAD_RELAY_URL, "URL du relais invalide : elle doit commencer par https:// ou http://.")
        }

        val body = json.encodeToString(
            PairVerifyRequest(cleanCode, deviceId, deviceName, currentDeviceToken?.takeIf { it.isNotBlank() })
        )
        val reply = try {
            transport.postJson("$base/api/pair/verify", body, emptyMap())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return fail(PairingFailure.UNREACHABLE, "Relais injoignable : vérifiez l'URL et la connexion réseau.")
        }

        return when (reply.code) {
            200 -> parseSuccess(reply.body)
            400 -> fail(PairingFailure.INVALID_CODE, "Code d'appairage invalide ou expiré. Demandez-en un nouveau.")
            403 -> fail(
                PairingFailure.ALREADY_REGISTERED,
                "Ce téléphone est déjà enregistré sur le relais. Faites supprimer son enregistrement côté relais, puis réessayez avec un nouveau code."
            )
            409 -> fail(PairingFailure.REGISTRATION_CONFLICT, "Le relais n'a pas pu enregistrer cet appareil. Réessayez.")
            429 -> fail(PairingFailure.RATE_LIMITED, "Trop de tentatives d'appairage. Patientez quelques minutes.")
            else -> fail(PairingFailure.SERVER_ERROR, "Le relais a répondu une erreur (HTTP ${reply.code}).")
        }
    }

    private fun parseSuccess(body: String): PairingResult {
        val parsed = try {
            json.decodeFromString<PairVerifyResponse>(body)
        } catch (e: Exception) {
            null
        }
        val token = parsed?.deviceToken
        if (parsed == null || !parsed.ok || token.isNullOrBlank()) {
            return fail(PairingFailure.BAD_RESPONSE, "Réponse inattendue du relais : appairage non confirmé.")
        }
        return PairingResult.Success(token)
    }

    private fun fail(reason: PairingFailure, message: String) = PairingResult.Failure(reason, message)
}
