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

    @Test fun agentLoopAcceptsPlanTextAndSeveralCallsPerTurn() {
        val a = "<rea>{\"name\":\"inspect_artifact\",\"arguments\":{\"path\":\"/tmp/app\"}}</rea>"
        val b = "<rea>{\"name\":\"list_unknowns\",\"arguments\":{}}</rea>"
        val d = ReaProtocol.demande("Je commence par l'artefact.\n$a\n$b")
        assertEquals(listOf("inspect_artifact", "list_unknowns"), d.appels.map { it.getString("name") })
        assertTrue(d.erreurs.isEmpty())
        assertTrue(ReaProtocol.demande("Voici mon explication.").vide)
    }

    @Test fun invalidCallsBecomeFeedbackInsteadOfExceptions() {
        val ok = "<rea>{\"name\":\"inspect_artifact\",\"arguments\":{}}</rea>"
        val inconnu = ReaProtocol.demande("<rea>{\"name\":\"execute_command\",\"arguments\":{}}</rea>")
        assertTrue(inconnu.appels.isEmpty()); assertEquals(1, inconnu.erreurs.size)
        assertEquals(1, ReaProtocol.demande("<rea>{broken}</rea>").erreurs.size)
        assertEquals(1, ReaProtocol.demande("<rea>{\"name\":\"list_unknowns\",\"arguments\":[]}</rea>").erreurs.size)
        assertEquals(1, ReaProtocol.demande("<rea>{\"name\":\"list_unknowns\"}</rea>").erreurs.size)
        val partiel = ReaProtocol.demande(ok + "<rea>{\"name\":\"list_unknowns\",\"arguments\":{}}")
        assertEquals(1, partiel.appels.size); assertEquals(1, partiel.erreurs.size)
        val trop = ReaProtocol.demande(ok.repeat(ReaProtocol.MAX_PER_TURN + 2))
        assertEquals(ReaProtocol.MAX_PER_TURN, trop.appels.size); assertEquals(2, trop.erreurs.size)
    }

    @Test fun instructionMentionsBudgetAndCatalogue() {
        val texte = ReaProtocol.consigne("[CATALOGUE]")
        assertTrue(texte.contains("[CATALOGUE]"))
        assertTrue(texte.contains(ReaProtocol.MAX_CALLS.toString()))
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
