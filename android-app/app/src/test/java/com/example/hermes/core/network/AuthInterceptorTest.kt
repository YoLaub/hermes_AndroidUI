package com.example.hermes.core.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AuthInterceptorTest {

    @Test
    fun testCookiesInjectedIntoOutgoingRequest() {
        val interceptor = AuthInterceptor()
        interceptor.sessionCookie = "token123.sig456"
        interceptor.activeProfile = "mario"

        var interceptedRequest: Request? = null

        val chain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder().url("http://localhost:8000/api/test").build()

            override fun proceed(request: Request): Response {
                interceptedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            override fun connection(): Connection? = null
            override fun call(): Call = throw NotImplementedError()
            override fun connectTimeoutMillis(): Int = 0
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun readTimeoutMillis(): Int = 0
            override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun writeTimeoutMillis(): Int = 0
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        }

        interceptor.intercept(chain)

        assertNotNull(interceptedRequest)
        val cookieHeader = interceptedRequest?.header("Cookie")
        assertNotNull(cookieHeader)
        assertTrue(cookieHeader!!.contains("hermes_session=token123.sig456"))
        assertTrue(cookieHeader.contains("hermes_profile=mario"))
    }

    @Test
    fun testSetCookieCapturedFromResponse() {
        val interceptor = AuthInterceptor()
        var updatedSession: String? = null
        var updatedProfile: String? = null

        interceptor.onSessionCookieUpdated = { updatedSession = it }
        interceptor.onProfileCookieUpdated = { updatedProfile = it }

        val chain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder().url("http://localhost:8000/api/auth/login").build()

            override fun proceed(request: Request): Response {
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .addHeader("Set-Cookie", "hermes_session=newtoken.newsig; Path=/; HttpOnly; SameSite=Lax")
                    .addHeader("Set-Cookie", "hermes_profile=gaston; Path=/; HttpOnly; SameSite=Lax")
                    .body("""{"ok": true}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            override fun connection(): Connection? = null
            override fun call(): Call = throw NotImplementedError()
            override fun connectTimeoutMillis(): Int = 0
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun readTimeoutMillis(): Int = 0
            override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun writeTimeoutMillis(): Int = 0
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        }

        interceptor.intercept(chain)

        assertEquals("newtoken.newsig", interceptor.sessionCookie)
        assertEquals("newtoken.newsig", updatedSession)
        assertEquals("gaston", interceptor.activeProfile)
        assertEquals("gaston", updatedProfile)
    }

    @Test
    fun testMaxAgeZeroClearsSessionCookie() {
        val interceptor = AuthInterceptor()
        interceptor.sessionCookie = "existing.token"
        var updatedSession: String? = "dummy"

        interceptor.onSessionCookieUpdated = { updatedSession = it }

        val chain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder().url("http://localhost:8000/api/auth/logout").build()

            override fun proceed(request: Request): Response {
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .addHeader("Set-Cookie", "hermes_session=; Path=/; Max-Age=0")
                    .body("""{"ok": true}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }

            override fun connection(): Connection? = null
            override fun call(): Call = throw NotImplementedError()
            override fun connectTimeoutMillis(): Int = 0
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun readTimeoutMillis(): Int = 0
            override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun writeTimeoutMillis(): Int = 0
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        }

        interceptor.intercept(chain)

        assertNull(interceptor.sessionCookie)
        assertNull(updatedSession)
    }
}
