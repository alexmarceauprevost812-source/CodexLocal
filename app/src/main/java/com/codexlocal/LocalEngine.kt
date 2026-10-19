package com.codexlocal

import android.app.ActivityManager
import android.content.Context
import com.codexlocal.engine.AiChat
import com.codexlocal.engine.InferenceEngine
import com.codexlocal.session.ContexteLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.File

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

    /**
     * Vrai tant que la memoire du modele ne contient que les consignes de depart (apres un chargement,
     * un changement de session ou de mode). Le premier message envoye dans cet etat est precede
     * d'un rappel de la conversation affichee, pour que le fil ne se perde pas.
     */
    @Volatile
    var conversationVide: Boolean = true
        private set

    /** Dernier nombre de remises a zero forcees (incident de calcul) vu cote moteur. */
    private var recuperationsVues = 0

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
        conversationVide = true
    }

    /** Memoire vive libre sur le telephone, en octets (0 si on ne peut pas la lire). */
    private fun memoireDisponible(context: Context): Long = runCatching {
        val service = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also { service.getMemoryInfo(it) }.availMem
    }.getOrDefault(0L)

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
            // Plus la memoire du modele est grande, plus la session peut durer sans rien oublier.
            e.setContextSize(
                ContexteLocal.tailleSouhaitee(memoireDisponible(context), File(modelPath).length())
            )
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
        conversationVide = true
    }

    suspend fun resetConversation(context: Context, cfg: AppConfig) {
        val e = engine ?: return
        e.resetConversation()
        e.setSamplingParams(cfg.temperature, 0.95f, 40)
        e.setSystemPrompt(cfg.systemPrompt)
        appliedTemperature = cfg.temperature
        systemPromptSent = true
        activeSystemPrompt = cfg.systemPrompt
        conversationVide = true
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
        conversationVide = true
    }

    /** Part de la memoire du modele deja utilisee, en pourcentage ; null si aucun modele n'est charge. */
    fun memoireUtilisee(): Int? {
        val e = engine ?: return null
        val total = e.contextCapacity()
        if (total <= 0) return null
        return (e.contextUsed() * 100L / total).toInt().coerceIn(0, 100)
    }

    fun isLoadedFor(path: String): Boolean = path.isNotBlank() && loadedPath == path && systemPromptSent

    fun stream(message: String, maxTokens: Int): Flow<String> {
        val e = engine ?: error("Moteur local non initialise")
        // Des que le message est lu (ou la reponse interrompue), la memoire du modele n'est plus vide.
        // Si le moteur a du la remettre a zero apres un incident, elle l'est de nouveau : le prochain
        // message sera precede d'un rappel.
        return e.sendUserPrompt(message, maxTokens)
            .onEach { conversationVide = false }
            .onCompletion { cause ->
                val recuperations = e.recoveryCount()
                if (recuperations != recuperationsVues) {
                    recuperationsVues = recuperations
                    conversationVide = true
                } else if (cause == null || cause is CancellationException) {
                    conversationVide = false
                }
            }
    }
}
