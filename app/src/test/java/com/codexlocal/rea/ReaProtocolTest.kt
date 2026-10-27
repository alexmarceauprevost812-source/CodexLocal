package com.codexlocal.rea

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaProtocolTest {
    @Test fun acceptsOnlyEncryptedOrLoopbackConnections() {
        assertEquals("http://127.0.0.1:8766/mcp", ReaProtocol.endpoint("http://127.0.0.1:8766/mcp"))
        assertEquals("https://rea.example/mcp", ReaProtocol.endpoint("https://rea.example/mcp"))
        for (bad in listOf("http://192.168.1.10:8766/mcp", "http://127.0.0.1.evil/mcp",
            "https://user:secret@rea.example/mcp", "https://rea.example/mcp?token=secret",
            "file:///mcp", "https://rea.example/other")) {
            assertTrue(bad, runCatching { ReaProtocol.endpoint(bad) }.isFailure)
        }
    }

    @Test fun toolRequestsCannotBeMixedWithFileWritesOrUnknownTools() {
        val valid = "<rea>{\"name\":\"inspect_artifact\",\"arguments\":{\"path\":\"/tmp/app\"}}</rea>"
        assertEquals("inspect_artifact", ReaProtocol.call(valid)!!.getString("name"))
        assertNull(ReaProtocol.call("Voici mon explication."))
        assertTrue(runCatching { ReaProtocol.call(valid + "\n=== FICHIER: x ===\nhello\n=== FIN ===") }.isFailure)
        assertTrue(runCatching { ReaProtocol.call(valid + valid) }.isFailure)
        assertTrue(runCatching { ReaProtocol.call(valid.replace("inspect_artifact", "execute_command")) }.isFailure)
        assertTrue(runCatching { ReaProtocol.call("<rea>{broken}") }.isFailure)
    }

    @Test fun jsonAndSseHandleNotificationsMultilineAndIncompleteFrames() {
        assertEquals(1, ReaProtocol.response("{\"id\":7,\"result\":{\"ok\":1}}", 7).getInt("ok"))
        val stream = "data: {\"method\":\"notifications/progress\"}\r\n\r\n" +
            "event: message\r\ndata: {\"id\":7,\r\ndata: \"result\":{\"ok\":2}}\r\n\r\n"
        assertTrue(ReaProtocol.hasResponse(stream, 7))
        assertEquals(2, ReaProtocol.response(stream, 7).getInt("ok"))
        assertFalse(ReaProtocol.hasResponse(stream.trimEnd(), 7))
        assertTrue(runCatching { ReaProtocol.response("{\"id\":8,\"result\":{}}", 7) }.isFailure)
        val error = "data: {\"id\":7,\"error\":{\"message\":\"schema invalide\"}}\n\n"
        assertTrue(ReaProtocol.hasResponse(error, 7))
        assertTrue(runCatching { ReaProtocol.response(error, 7) }.exceptionOrNull()!!.message!!.contains("schema invalide"))
    }
}
