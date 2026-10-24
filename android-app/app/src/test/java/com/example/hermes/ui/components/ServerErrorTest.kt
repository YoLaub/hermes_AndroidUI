package com.example.hermes.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerErrorTest {
    @Test
    fun `no status means the server was not reached`() {
        assertEquals(ServerErrorKind.Network, serverErrorKind(null))
    }

    @Test
    fun `auth statuses are told apart`() {
        assertEquals(ServerErrorKind.Unauthorized, serverErrorKind(401))
        assertEquals(ServerErrorKind.Forbidden, serverErrorKind(403))
    }

    @Test
    fun `a 422 is a refused request`() {
        assertEquals(ServerErrorKind.Rejected, serverErrorKind(422))
        assertEquals(ServerErrorKind.Rejected, serverErrorKind(400))
    }

    @Test
    fun `gateway statuses mean an upstream service is down`() {
        assertEquals(ServerErrorKind.Upstream, serverErrorKind(502))
        assertEquals(ServerErrorKind.Upstream, serverErrorKind(503))
        assertEquals(ServerErrorKind.Upstream, serverErrorKind(504))
    }

    @Test
    fun `other statuses fall into broad classes`() {
        assertEquals(ServerErrorKind.NotFound, serverErrorKind(404))
        assertEquals(ServerErrorKind.Server, serverErrorKind(500))
        assertEquals(ServerErrorKind.Other, serverErrorKind(409))
    }

    @Test
    fun `the status is read from the start of a raw message`() {
        assertEquals(422, httpStatusOf("HTTP 422: label too long"))
        assertEquals(502, httpStatusOf("502 Bad Gateway"))
        assertEquals(null, httpStatusOf("Software caused connection abort"))
    }
}
