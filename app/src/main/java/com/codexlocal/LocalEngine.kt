package com.codexlocal

import android.content.Context
import com.codexlocal.engine.AiChat
import com.codexlocal.engine.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Enveloppe autour du moteur llama.cpp (module :lib).
 *
 * Particularite importante : le moteur garde l'historique de la conversation
 * dans son cache KV. On ne lui envoie donc que le dernier message de
 * l'utilisateur, et "nouvelle conversation" se fait via resetConversation()
 * sans recharger le modele.
 */
object LocalEngine {

    private var engine: InferenceEngine? = null

    @Volatile
    var loadedPath: String? = null
        private set

    private var systemPromptSent = false
    private var appliedTemperature = Float.NaN

    fun engine(context: Context): InferenceEngine =
        engine ?: AiChat.getInferenceEngine(context.applicationContext).also { engine = it }

    private suspend fun awaitReady(e: InferenceEngine) {
        val state = e.state.first {
            it !is InferenceEngine.State.Uninitialized && it !is InferenceEngine.State.Initializing
        }
        if (state is InferenceEngine.State.Error) throw state.exception
    }

    suspend fun load(
        context: Context,
        modelPath: String,
        cfg: AppConfig,
        onStatus: (String) -> Unit
    ) {
        val e = engine(context)
        awaitReady(e)
        if (isLoadedFor(modelPath)) return

        onStatus("Chargement du modele…")
        e.loadModel(modelPath)
        e.setSamplingParams(cfg.temperature, 0.95f, 40)
        e.setSystemPrompt(cfg.systemPrompt)
        appliedTemperature = cfg.temperature
        loadedPath = modelPath
        systemPromptSent = true
    }

    suspend fun resetConversation(context: Context, cfg: AppConfig) {
        val e = engine ?: return
        e.resetConversation()
        e.setSamplingParams(cfg.temperature, 0.95f, 40)
        e.setSystemPrompt(cfg.systemPrompt)
        appliedTemperature = cfg.temperature
        systemPromptSent = true
    }

    /** Reconstruit le sampler seulement si la temperature a change. */
    suspend fun applySampling(cfg: AppConfig) {
        val e = engine ?: return
        if (appliedTemperature == cfg.temperature) return
        e.setSamplingParams(cfg.temperature, 0.95f, 40)
        appliedTemperature = cfg.temperature
    }

    fun unload() {
        val e = engine ?: return
        runCatching { e.cleanUp() }
        loadedPath = null
        systemPromptSent = false
        appliedTemperature = Float.NaN
    }

    fun isLoadedFor(path: String): Boolean = path.isNotBlank() && loadedPath == path && systemPromptSent

    fun stream(message: String, maxTokens: Int): Flow<String> {
        val e = engine ?: error("Moteur local non initialise")
        return e.sendUserPrompt(message, maxTokens)
    }
}
