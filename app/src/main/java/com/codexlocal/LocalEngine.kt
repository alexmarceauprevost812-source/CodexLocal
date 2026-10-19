package com.codexlocal

import android.content.Context
import com.codexlocal.engine.AiChat
import com.codexlocal.engine.InferenceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
    private var activeSystemPrompt: String? = null

    fun engine(context: Context): InferenceEngine =
        engine ?: AiChat.getInferenceEngine(context.applicationContext).also { engine = it }

    private suspend fun awaitReady(e: InferenceEngine) {
        val state = e.state.first {
            it !is InferenceEngine.State.Uninitialized && it !is InferenceEngine.State.Initializing
        }
        // Un echec precedent (chargement ou generation) ne doit pas bloquer le moteur
        // pour toujours : on le remet a zero et on repart.
        if (state is InferenceEngine.State.Error) reinitialiser(e)
    }

    /** Sort le moteur de l'etat "Erreur" et oublie ce qui etait charge. */
    private suspend fun reinitialiser(e: InferenceEngine) {
        withContext(Dispatchers.Default) { runCatching { e.cleanUp() } }
        loadedPath = null
        systemPromptSent = false
        activeSystemPrompt = null
        appliedTemperature = Float.NaN
    }

    suspend fun load(
        context: Context,
        modelPath: String,
        cfg: AppConfig,
        repartirDeZero: Boolean = false,
        onStatus: (String) -> Unit
    ) {
        val e = engine(context)
        awaitReady(e)
        if (isLoadedFor(modelPath)) {
            // Un changement de mode ou de role doit aussi modifier le prompt du moteur local.
            if (repartirDeZero || activeSystemPrompt != cfg.systemPrompt) {
                resetConversation(context, cfg)
            }
            return
        }

        // Changement de modele : on decharge l'ancien (sinon le moteur refuse d'en charger un autre).
        if (loadedPath != null) withContext(Dispatchers.Default) { unload() }

        onStatus("Chargement du modele…")
        try {
            e.loadModel(modelPath)
            e.setSamplingParams(cfg.temperature, 0.95f, 40)
            e.setSystemPrompt(cfg.systemPrompt)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            // Le prochain essai (autre modele, par exemple) doit repartir d'un moteur sain.
            reinitialiser(e)
            throw ex
        }
        appliedTemperature = cfg.temperature
        loadedPath = modelPath
        systemPromptSent = true
        activeSystemPrompt = cfg.systemPrompt
    }

    suspend fun resetConversation(context: Context, cfg: AppConfig) {
        val e = engine ?: return
        e.resetConversation()
        e.setSamplingParams(cfg.temperature, 0.95f, 40)
        e.setSystemPrompt(cfg.systemPrompt)
        appliedTemperature = cfg.temperature
        systemPromptSent = true
        activeSystemPrompt = cfg.systemPrompt
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
        activeSystemPrompt = null
        appliedTemperature = Float.NaN
    }

    fun isLoadedFor(path: String): Boolean = path.isNotBlank() && loadedPath == path && systemPromptSent

    fun stream(message: String, maxTokens: Int): Flow<String> {
        val e = engine ?: error("Moteur local non initialise")
        return e.sendUserPrompt(message, maxTokens)
    }
}
