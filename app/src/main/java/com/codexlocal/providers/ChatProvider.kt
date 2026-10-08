package com.codexlocal.providers

import com.codexlocal.AppConfig
import com.codexlocal.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Un fournisseur d'IA distant qui repond en streaming. */
interface ChatProvider {
    fun stream(history: List<Message>, cfg: AppConfig): Flow<String>
}

/**
 * Appel HTTP POST + lecture d'un flux SSE (Server-Sent Events).
 * `parse` recoit chaque objet JSON d'un evenement "data:" et renvoie le
 * morceau de texte a afficher (ou null pour ignorer l'evenement).
 */
internal fun sse(
    url: String,
    headers: Map<String, String>,
    jsonBody: String,
    parse: (JSONObject) -> String?
): Flow<String> = flow {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 30_000
        readTimeout = 300_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "text/event-stream")
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    try {
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
            it.write(jsonBody)
            it.flush()
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val body = conn.errorStream?.bufferedReader()?.use { r -> r.readText() } ?: ""
            throw IllegalStateException("HTTP $code — ${body.take(400)}")
        }
        val input = conn.inputStream ?: error("Reponse vide")
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.substring(5).trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                val piece = parse(json)
                if (!piece.isNullOrEmpty()) emit(piece)
            }
        }
    } finally {
        conn.disconnect()
    }
}.flowOn(Dispatchers.IO)
