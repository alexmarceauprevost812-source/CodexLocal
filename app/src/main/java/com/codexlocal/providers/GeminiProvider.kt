package com.codexlocal.providers

import com.codexlocal.AppConfig
import com.codexlocal.Message
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/** API Google Gemini (AI Studio, palier gratuit disponible). */
class GeminiProvider : ChatProvider {

    override fun stream(history: List<Message>, cfg: AppConfig): Flow<String> {
        val base = cfg.baseUrl.trimEnd('/')
        val url = "$base/models/${cfg.model}:streamGenerateContent?alt=sse&key=${cfg.apiKey}"

        val contents = JSONArray()
        history.forEach {
            if (it.content.isNotBlank()) {
                contents.put(
                    JSONObject()
                        .put("role", if (it.isUser) "user" else "model")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", it.content))
                        )
                )
            }
        }

        val body = JSONObject()
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", cfg.temperature.toDouble())
                    .put("maxOutputTokens", cfg.maxTokens)
            )
            .apply {
                if (cfg.systemPrompt.isNotBlank()) {
                    put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", cfg.systemPrompt))
                        )
                    )
                }
            }
            .toString()

        return sse(url, emptyMap(), body) { json ->
            json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.takeIf { it.isNotEmpty() }
        }
    }
}
