package com.example.hermes.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The agenda shows the agents' scheduled jobs as the WebUI lists them. */
class CronsApiTest {

    private fun capturing(into: MutableList<Request>, body: String): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            into.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()

    @Test
    fun scheduledJobsAreListed() = runBlocking {
        val seen = mutableListOf<Request>()
        val body = """{"jobs":[
            {"id":"2f403514ac7a","name":"extraire-titre","prompt":"Ouvre example.org",
             "schedule":{"kind":"once","expression":"2026-10-20T12:25:13+00:00"},"schedule_display":"once at 12:25",
             "enabled":true,"state":"scheduled","next_run_at":"2026-10-20T12:25:13+00:00","profile":"memtest"},
            {"id":"a1","prompt":"Résumé","schedule":{"kind":"cron","expression":"0 8 * * *"},
             "enabled":false,"state":"paused","next_run_at":null,"profile":null}
        ]}"""
        val client = HermesApiClient(AuthInterceptor(), okHttpClient = capturing(seen, body))

        val jobs = client.getCrons("https://hermes.example").getOrThrow().jobs

        assertEquals("https://hermes.example/api/crons", seen.single().url.toString())
        assertEquals("extraire-titre", jobs[0].name)
        assertEquals("2026-10-20T12:25:13+00:00", jobs[0].nextRunAt)
        assertEquals("memtest", jobs[0].profile)
        assertEquals("once at 12:25", jobs[0].scheduleDisplay)
        assertNull(jobs[1].name)
        assertNull(jobs[1].nextRunAt)
        assertNull(jobs[1].profile)
        assertEquals(false, jobs[1].enabled)
    }
}
