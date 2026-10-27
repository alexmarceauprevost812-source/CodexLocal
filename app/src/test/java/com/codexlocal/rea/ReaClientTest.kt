package com.codexlocal.rea

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaClientTest {
    @Test fun initializesDiscoversCallsAndClosesTheSameSession() = runBlocking {
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val failures = Collections.synchronizedList(mutableListOf<String>())
        // Android's compilation classpath omits the JDK-only jdk.httpserver module.
        val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            try {
              repeat(5) {
               server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val request = input.readLine()
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = input.readLine() ?: error("En-tête incomplet")
                    if (line.isEmpty()) break
                    headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                }
                val chars = CharArray(headers["content-length"]?.toInt() ?: 0)
                var n = 0
                while (n < chars.size) {
                    val read = input.read(chars, n, chars.size - n)
                    check(read > 0) { "Corps HTTP incomplet" }
                    n += read
                }
                val body = String(chars)
                val obj = if (body.isBlank()) null else JSONObject(body)
                val method = obj?.optString("method") ?: request.substringBefore(' ')
                seen.add(method)
                if (method != "initialize" && headers["mcp-session-id"] != "test-session") failures.add("Session manquante")
                if (headers["authorization"] != "Bearer test-token") failures.add("Jeton manquant")
                val status = if (method == "notifications/initialized") 202 else 200
                val reply = if (method == "notifications/initialized" || method == "DELETE") ByteArray(0) else {
                    val result = when (method) {
                        "initialize" -> JSONObject().put("protocolVersion", "2025-03-26")
                        "tools/list" -> JSONObject("""{"tools":[{"name":"inspect_artifact","inputSchema":{"type":"object"}},{"name":"execute_command","inputSchema":{"type":"object"}}]}""")
                        else -> JSONObject("""{"isError":false,"content":[{"type":"text","text":"preuve"}]}""")
                    }
                    JSONObject().put("jsonrpc", "2.0").put("id", obj!!.getInt("id")).put("result", result).toString().toByteArray(Charsets.UTF_8)
                }
                val session = if (method == "initialize") "Mcp-Session-Id: test-session\r\n" else ""
                val output = socket.getOutputStream()
                output.write(("HTTP/1.1 $status OK\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${reply.size}\r\nConnection: close\r\n${session}\r\n").toByteArray(Charsets.US_ASCII))
                output.write(reply); output.flush()
               }
              }
            } catch (e: Exception) { if (!server.isClosed) failures.add(e.toString()) }
        }.apply { isDaemon = true; start() }
        try {
            val client = ReaClient("http://127.0.0.1:${server.localPort}/mcp", "test-token")
            try {
                val prompt = client.connect()
                assertTrue(prompt.contains("inspect_artifact"))
                assertFalse(prompt.contains("execute_command"))
                assertTrue(client.call(JSONObject("""{"name":"inspect_artifact","arguments":{}}""")).contains("preuve"))
                assertTrue(runCatching { client.call(JSONObject("""{"name":"execute_command","arguments":{}}""")) }.isFailure)
            } finally { client.close() }
            assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/call", "DELETE"), seen)
            assertEquals(emptyList<String>(), failures)
        } finally { server.close(); worker.join(3000) }
    }
}
