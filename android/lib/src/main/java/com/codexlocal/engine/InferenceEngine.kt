package com.codexlocal.engine

import com.codexlocal.engine.InferenceEngine.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the core LLM inference operations.
 */
interface InferenceEngine {
    /**
     * Current state of the inference engine
     */
    val state: StateFlow<State>

    /**
     * Load a model from the given path.
     *
     * @throws UnsupportedArchitectureException if model architecture not supported
     */
    suspend fun loadModel(pathToModel: String)

    /**
     * Sends a system prompt to the loaded model
     */
    suspend fun setSystemPrompt(systemPrompt: String)

    /**
     * Regle l'echantillonnage (temperature / top-p / top-k).
     * Peut etre appele a tout moment apres le chargement du modele.
     */
    suspend fun setSamplingParams(temperature: Float, topP: Float, topK: Int)

    /**
     * Vide l'historique de conversation (cache KV) sans recharger le modele.
     * Il faut ensuite renvoyer le prompt systeme via [setSystemPrompt].
     */
    suspend fun resetConversation()

    /**
     * Taille de memoire de conversation voulue, en jetons. A regler avant [loadModel] :
     * le moteur la plafonne a ce que le modele sait faire et la reduit si la memoire du telephone manque.
     */
    fun setContextSize(tokens: Int)

    /** Jetons deja utilises dans la memoire du modele, ou 0 si aucun modele n'est charge. */
    fun contextUsed(): Int

    /** Jetons que la memoire du modele peut contenir, ou 0 si aucun modele n'est charge. */
    fun contextCapacity(): Int

    /**
     * Nombre de fois ou la memoire du modele a du etre remise au point de depart apres un incident
     * de calcul. Quand ce nombre change, le modele a oublie la conversation en cours.
     */
    fun recoveryCount(): Int

    /**
     * Sends a user prompt to the loaded model and returns a Flow of generated tokens.
     *
     * @throws PromptRejectedException si le moteur n'a pas pu lire le message : le moteur reste
     * utilisable et la conversation continue.
     */
    fun sendUserPrompt(message: String, predictLength: Int = DEFAULT_PREDICT_LENGTH): Flow<String>

    /**
     * Runs a benchmark with the specified parameters.
     */
    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String

    /**
     * Unloads the currently loaded model.
     */
    fun cleanUp()

    /**
     * Cleans up resources when the engine is no longer needed.
     */
    fun destroy()

    /**
     * States of the inference engine
     */
    sealed class State {
        object Uninitialized : State()
        object Initializing : State()
        object Initialized : State()

        object LoadingModel : State()
        object UnloadingModel : State()
        object ModelReady : State()

        object Benchmarking : State()
        object ProcessingSystemPrompt : State()
        object ProcessingUserPrompt : State()

        object Generating : State()

        data class Error(val exception: Exception) : State()
    }

    companion object {
        const val DEFAULT_PREDICT_LENGTH = 1024
    }
}

val State.isUninterruptible
    get() = this is State.Initializing ||
        this is State.LoadingModel ||
        this is State.UnloadingModel ||
        this is State.Benchmarking ||
        this is State.ProcessingSystemPrompt ||
        this is State.ProcessingUserPrompt

val State.isModelLoaded: Boolean
    get() = this is State.ModelReady ||
        this is State.Benchmarking ||
        this is State.ProcessingSystemPrompt ||
        this is State.ProcessingUserPrompt ||
        this is State.Generating

/**
 * Le moteur natif n'a pas pu charger le modele. Malgre son nom historique, la cause
 * peut etre un fichier incomplet, une architecture trop recente ou un manque de memoire :
 * le message precise ce qu'il faut verifier.
 */
class UnsupportedArchitectureException(message: String? = null) : Exception(message)

/**
 * Le moteur n'a pas pu lire un message, mais il est reste sain : la memoire du modele a ete
 * ramenee au prompt systeme et on peut envoyer le message suivant sans changer de session.
 */
class PromptRejectedException(val code: Int, message: String) : Exception(message)
