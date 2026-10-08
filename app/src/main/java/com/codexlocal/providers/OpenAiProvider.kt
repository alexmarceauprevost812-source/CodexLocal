package com.codexlocal.providers

import com.codexlocal.AppConfig
import com.codexlocal.Message
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tout service exposant /chat/completions a la OpenAI :
 * OpenAI, Groq, OpenRouter (modeles ":free"), DeepSeek, Mistral,
 * Together, Cerebras, Ollama en local (http://IP:11434/v1)...
 */
class OpenAiProvider : ChatProvider {

    override fun stream(history: List<Message>, cfg: AppConfig): Flow<String> {
        val url = cfg.baseUrl.trimEnd('/') + "/chat/completions"

        val messages = JSONArray()
        if (cfg.systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", cfg.systemPrompt))
        }
        history.forEach {
            if (it.content.isNotBlank()) {
                messages.put(JSONObject().put("role", it.role).put("content", it.content))
            }
        }

        val body = JSONObject()
            .put("model", cfg.model)
            .put("messages", messages)
            .put("temperature", cfg.temperature.toDouble())
            .put("max_tokens", cfg.maxTokens)
            .put("stream", true)
            .toString()

        val headers = HashMap<String, String>()
        if (cfg.apiKey.isNotBlank()) headers["Authorization"] = "Bearer ${cfg.apiKey}"

        return sse(url, headers, body) { json ->
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("content")
                ?.takeIf { it.isNotEmpty() && it != "null" }
        }
    }
}
