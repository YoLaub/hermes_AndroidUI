package com.example.hermes.core.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    @Volatile
    var sessionCookie: String? = null

    @Volatile
    var activeProfile: String? = null

    var onSessionCookieUpdated: ((String?) -> Unit)? = null
    var onProfileCookieUpdated: ((String) -> Unit)? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // Build Cookie header
        val cookieParts = mutableListOf<String>()
        sessionCookie?.let {
            cookieParts.add("hermes_session=$it")
        }
        activeProfile?.let {
            cookieParts.add("hermes_profile=$it")
        }

        if (cookieParts.isNotEmpty()) {
            requestBuilder.header("Cookie", cookieParts.joinToString("; "))
        }

        val response = chain.proceed(requestBuilder.build())

        // Extract Set-Cookie headers
        val setCookies = response.headers("Set-Cookie")
        for (header in setCookies) {
            if (header.contains("hermes_session=")) {
                val value = header.substringAfter("hermes_session=").substringBefore(";")
                if (value.isNotEmpty()) {
                    sessionCookie = value
                    onSessionCookieUpdated?.invoke(value)
                } else if (header.contains("Max-Age=0")) {
                    sessionCookie = null
                    onSessionCookieUpdated?.invoke(null)
                }
            }
            if (header.contains("hermes_profile=")) {
                val value = header.substringAfter("hermes_profile=").substringBefore(";")
                if (value.isNotEmpty()) {
                    activeProfile = value
                    onProfileCookieUpdated?.invoke(value)
                }
            }
        }

        return response
    }
}
