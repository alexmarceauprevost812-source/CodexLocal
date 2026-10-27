package com.codexlocal.rea

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaClientTest {
    @Test fun initializesDiscoversCallsAndClosesTheSameSession() = runBlocking {
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val failures = Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            try {
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                val obj = if (body.isBlank()) null else JSONObject(body)
                val method = obj?.optString("method") ?: exchange.requestMethod
                seen.add(method)
                if (method != "initialize" && exchange.requestHeaders.getFirst("Mcp-Session-Id") != "test-session") failures.add("Session manquante")
                if (exchange.requestHeaders.getFirst("Authorization") != "Bearer test-token") failures.add("Jeton manquant")
                exchange.responseHeaders.add("Content-Type", "application/json")
                if (method == "notifications/initialized") exchange.sendResponseHeaders(202, -1)
                else if (method == "DELETE") exchange.sendResponseHeaders(200, -1)
                else {
                    val result = when (method) {
                        "initialize" -> { exchange.responseHeaders.add("Mcp-Session-Id", "test-session"); JSONObject().put("protocolVersion", "2025-03-26") }
                        "tools/list" -> JSONObject("""{"tools":[{"name":"inspect_artifact","inputSchema":{"type":"object"}},{"name":"execute_command","inputSchema":{"type":"object"}}]}""")
                        else -> JSONObject("""{"isError":false,"content":[{"type":"text","text":"preuve"}]}""")
                    }
                    val reply = JSONObject().put("jsonrpc", "2.0").put("id", obj!!.getInt("id")).put("result", result).toString().toByteArray()
                    exchange.sendResponseHeaders(200, reply.size.toLong()); exchange.responseBody.write(reply)
                }
            } finally { exchange.close() }
        }
        server.start()
        try {
            val client = ReaClient("http://127.0.0.1:${server.address.port}/mcp", "test-token")
            try {
                val prompt = client.connect()
                assertTrue(prompt.contains("inspect_artifact"))
                assertFalse(prompt.contains("execute_command"))
                assertTrue(client.call(JSONObject("""{"name":"inspect_artifact","arguments":{}}""")).contains("preuve"))
                assertTrue(runCatching { client.call(JSONObject("""{"name":"execute_command","arguments":{}}""")) }.isFailure)
            } finally { client.close() }
            assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/call", "DELETE"), seen)
            assertEquals(emptyList<String>(), failures)
        } finally { server.stop(0) }
    }
}
