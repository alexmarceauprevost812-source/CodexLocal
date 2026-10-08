package com.codexlocal.providers

import com.codexlocal.AppConfig
import com.codexlocal.Message
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/** API Messages d'Anthropic (Claude). */
class AnthropicProvider : ChatProvider {

    override fun stream(history: List<Message>, cfg: AppConfig): Flow<String> {
        val url = cfg.baseUrl.trimEnd('/') + "/messages"

        val messages = JSONArray()
        history.forEach {
            if (it.content.isNotBlank()) {
                messages.put(JSONObject().put("role", it.role).put("content", it.content))
            }
        }

        val body = JSONObject()
            .put("model", cfg.model)
            .put("max_tokens", cfg.maxTokens)
            .put("temperature", cfg.temperature.toDouble())
            .put("stream", true)
            .put("messages", messages)
            .apply {
                if (cfg.systemPrompt.isNotBlank()) put("system", cfg.systemPrompt)
            }
            .toString()

        val headers = mapOf(
            "x-api-key" to cfg.apiKey,
            "anthropic-version" to "2023-06-01"
        )

        return sse(url, headers, body) { json ->
            if (json.optString("type") == "content_block_delta") {
                json.optJSONObject("delta")?.optString("text")?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }
    }
}
