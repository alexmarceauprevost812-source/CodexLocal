package com.codexlocal.rea

import org.json.JSONObject
import java.net.URI

/** Protocol frames stay separate from generated project-file actions. */
object ReaProtocol {
    const val MAX_RESPONSE = 1_048_576
    const val MAX_CALLS = 6
    val tools = setOf(
        "inspect_artifact", "inspect_apk_signature", "analyze_javascript_application",
        "inspect_analysis_view", "get_evidence_bundle", "list_unknowns"
    )

    fun endpoint(value: String): String {
        val uri = URI(value.trim())
        val host = uri.host?.lowercase() ?: error("Adresse REA invalide.")
        require(uri.userInfo == null && uri.rawQuery == null && uri.fragment == null) {
            "L'adresse REA ne doit contenir ni identifiants, ni paramètres."
        }
        val loopback = host in setOf("localhost", "127.0.0.1", "[::1]", "::1")
        require(uri.scheme == "https" || (uri.scheme == "http" && loopback)) {
            "Utilise HTTPS, ou un tunnel SSH vers http://127.0.0.1:8766/mcp."
        }
        require(uri.path == "/mcp") { "L'adresse REA doit se terminer par /mcp." }
        return uri.toASCIIString()
    }

    fun call(text: String): JSONObject? {
        if (!text.contains("<rea>")) return null
        val match = Regex("<rea>([\\s\\S]*?)</rea>").findAll(text).toList()
        require(match.size == 1 && match[0].value == text.trim()) {
            "Une demande REA doit contenir un seul bloc <rea> JSON, sans autre action."
        }
        val obj = JSONObject(match.single().groupValues[1])
        require(obj.keys().asSequence().toSet() == setOf("name", "arguments")) {
            "Demande REA invalide : name et arguments requis."
        }
        require(obj.getString("name") in tools && obj.opt("arguments") is JSONObject) {
            "Outil REA non autorisé ou arguments invalides."
        }
        return obj
    }

    fun response(body: String, id: Int): JSONObject {
        require(body.length <= MAX_RESPONSE) { "Réponse REA trop volumineuse." }
        if (body.trimStart().startsWith("{")) return checked(JSONObject(body), id)
        for (obj in events(body)) if (obj.opt("id") == id) return checked(obj, id)
        error("Aucune réponse REA correspondant à la demande.")
    }

    fun hasResponse(body: String, id: Int): Boolean = events(body).any { it.opt("id") == id }

    private fun events(body: String): List<JSONObject> = body.replace("\r\n", "\n")
        .split("\n\n").dropLast(1).mapNotNull { frame ->
            val data = frame.lineSequence().filter { it.startsWith("data:") }
                .map { it.removePrefix("data:").trimStart() }.joinToString("\n")
            if (data.isBlank()) null else runCatching { JSONObject(data) }.getOrNull()
        }

    private fun checked(obj: JSONObject, id: Int): JSONObject {
        require(obj.opt("id") == id) { "Identifiant de réponse REA incorrect." }
        if (obj.has("error")) error("REA : " + obj.getJSONObject("error").optString("message"))
        return obj.getJSONObject("result")
    }
}
