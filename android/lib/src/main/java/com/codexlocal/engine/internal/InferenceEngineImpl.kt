package com.codexlocal.engine.internal

import android.content.Context
import android.util.Log
import com.codexlocal.engine.InferenceEngine
import com.codexlocal.engine.PromptRejectedException
import com.codexlocal.engine.UnsupportedArchitectureException
import com.codexlocal.engine.internal.InferenceEngineImpl.Companion.getInstance
import dalvik.annotation.optimization.FastNative
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * JNI wrapper for the llama.cpp library providing Android-friendly access to large language models.
 *
 * This class implements a singleton pattern for managing the lifecycle of a single LLM instance.
 * All operations are executed on a dedicated single-threaded dispatcher to ensure thread safety
 * with the underlying C++ native code.
 *
 * The typical usage flow is:
 * 1. Get instance via [getInstance]
 * 2. Load a model with [loadModel]
 * 3. Send prompts with [sendUserPrompt]
 * 4. Generate responses as token streams
 * 5. Perform [cleanUp] when done with a model
 * 6. Properly [destroy] when completely done
 *
 * State transitions are managed automatically and validated at each operation.
 *
 * @see ai_chat.cpp for the native implementation details
 */
internal class InferenceEngineImpl private constructor(
    private val nativeLibDir: String
) : InferenceEngine {

    companion object {
        private val TAG = InferenceEngineImpl::class.java.simpleName

        /** Memoire de conversation par defaut (celle du moteur natif), en jetons. */
        const val DEFAULT_CONTEXT_TOKENS = 8192

        @Volatile
        private var instance: InferenceEngine? = null

        /**
         * Create or obtain [InferenceEngineImpl]'s single instance.
         *
         * @param Context for obtaining native library directory
         * @throws IllegalArgumentException if native library path is invalid
         * @throws UnsatisfiedLinkError if library failed to load
         */
        internal fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                require(nativeLibDir.isNotBlank()) { "Expected a valid native library path!" }

                try {
                    Log.i(TAG, "Instantiating InferenceEngineImpl,,,")
                    InferenceEngineImpl(nativeLibDir).also { instance = it }
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Failed to load native library from $nativeLibDir", e)
                    throw e
                }
            }
    }

    /**
     * JNI methods
     * @see ai_chat.cpp
     */
    @FastNative
    private external fun init(nativeLibDir: String)

    @FastNative
    private external fun load(modelPath: String): Int

    @FastNative
    private external fun prepare(): Int

    @FastNative
    private external fun nativeSetSamplingParams(temperature: Float, topP: Float, topK: Int)

    @FastNative
    private external fun nativeResetConversation()

    @FastNative
    private external fun nativeSetContextSize(tokens: Int)

    @FastNative
    private external fun nativeContextUsed(): Int

    @FastNative
    private external fun nativeContextSize(): Int

    @FastNative
    private external fun nativeRecoveryCount(): Int

    @FastNative
    private external fun systemInfo(): String

    @FastNative
    private external fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String

    @FastNative
    private external fun processSystemPrompt(systemPrompt: String): Int

    @FastNative
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int

    @FastNative
    private external fun generateNextToken(): String?

    @FastNative
    private external fun unload()

    @FastNative
    private external fun shutdown()

    private val _state =
        MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Uninitialized)
    override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

    private var _readyForSystemPrompt = false
    @Volatile
    private var _cancelGeneration = false

    /** Taille de memoire de conversation voulue, transmise au moteur juste avant sa preparation. */
    @Volatile
    private var _wantedContext = DEFAULT_CONTEXT_TOKENS

    /** Vrai tant qu'un modele est charge cote natif (pour ne le liberer qu'une fois). */
    @Volatile
    private var _modelLoaded = false

    /** Un vrai fichier GGUF commence par les quatre octets "GGUF". */
    private fun verifierEnTeteGguf(fichier: File) {
        val entete = ByteArray(4)
        val lus = fichier.inputStream().use { it.read(entete) }
        if (lus != 4 || String(entete, Charsets.US_ASCII) != "GGUF") {
            throw IOException(
                "« ${fichier.name} » n'est pas un modèle GGUF valide " +
                    "(${fichier.length() / 1_048_576} Mo) : téléchargement interrompu ou mauvais fichier. " +
                    "Supprime-le puis télécharge-le de nouveau."
            )
        }
    }

    /**
     * Single-threaded coroutine dispatcher & scope for LLama asynchronous operations
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    init {
        llamaScope.launch {
            try {
                check(_state.value is InferenceEngine.State.Uninitialized) {
                    "Cannot load native library in ${_state.value.javaClass.simpleName}!"
                }
                _state.value = InferenceEngine.State.Initializing
                Log.i(TAG, "Loading native library...")
                System.loadLibrary("codexlocal")
                init(nativeLibDir)
                _state.value = InferenceEngine.State.Initialized
                Log.i(TAG, "Native library loaded! System info: \n${systemInfo()}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load native library", e)
                throw e
            }
        }
    }

    /**
     * Load the LLM
     */
    override suspend fun loadModel(pathToModel: String) =
        withContext(llamaDispatcher) {
            check(_state.value is InferenceEngine.State.Initialized) {
                "Cannot load model in ${_state.value.javaClass.simpleName}!"
            }

            try {
                Log.i(TAG, "Checking access to model file... \n$pathToModel")
                val fichier = File(pathToModel)
                require(fichier.exists()) { "Modèle introuvable : ${fichier.name}" }
                require(fichier.isFile) { "Ce n'est pas un fichier de modèle : ${fichier.name}" }
                require(fichier.canRead()) { "Modèle illisible : ${fichier.name}" }
                verifierEnTeteGguf(fichier)
                val taille = "${fichier.length() / 1_048_576} Mo"

                Log.i(TAG, "Loading model... \n$pathToModel")
                _readyForSystemPrompt = false
                _state.value = InferenceEngine.State.LoadingModel
                load(pathToModel).let {
                    if (it != 0) {
                        throw UnsupportedArchitectureException(
                            "Le moteur n'a pas pu charger « ${fichier.name} » ($taille). " +
                                "Causes probables : fichier incomplet (télécharge-le de nouveau), " +
                                "modèle trop récent pour ce moteur, ou mémoire insuffisante. " +
                                "Dans Réglages, choisis un autre modèle ou un plus petit."
                        )
                    }
                }
                nativeSetContextSize(_wantedContext)
                prepare().let {
                    if (it != 0) {
                        // Le modele est deja en memoire : on le libere pour ne pas le garder en vain.
                        unload()
                        throw IOException(
                            "Mémoire insuffisante pour préparer « ${fichier.name} » ($taille). " +
                                "Ferme d'autres applications ou choisis un modèle plus petit."
                        )
                    }
                }
                Log.i(TAG, "Model loaded!")
                _modelLoaded = true
                _readyForSystemPrompt = true

                _cancelGeneration = false
                _state.value = InferenceEngine.State.ModelReady
            } catch (e: Exception) {
                Log.e(TAG, (e.message ?: "Error loading model") + "\n" + pathToModel, e)
                _state.value = InferenceEngine.State.Error(e)
                throw e
            }
        }

    /**
     * Process the plain text system prompt
     *
     * TODO-han.yin: return error code if system prompt not correct processed?
     */
    override suspend fun setSystemPrompt(prompt: String) =
        withContext(llamaDispatcher) {
            require(prompt.isNotBlank()) { "Cannot process empty system prompt!" }
            check(_readyForSystemPrompt) { "System prompt must be set ** RIGHT AFTER ** model loaded!" }
            check(_state.value is InferenceEngine.State.ModelReady) {
                "Cannot process system prompt in ${_state.value.javaClass.simpleName}!"
            }

            Log.i(TAG, "Sending system prompt...")
            _readyForSystemPrompt = false
            _state.value = InferenceEngine.State.ProcessingSystemPrompt
            processSystemPrompt(prompt).let { result ->
                if (result != 0) {
                    RuntimeException("Failed to process system prompt: $result").also {
                        _state.value = InferenceEngine.State.Error(it)
                        throw it
                    }
                }
            }
            Log.i(TAG, "System prompt processed! Awaiting user prompt...")
            _state.value = InferenceEngine.State.ModelReady
        }

    override fun setContextSize(tokens: Int) {
        _wantedContext = tokens.coerceAtLeast(DEFAULT_CONTEXT_TOKENS / 2)
    }

    /** Les compteurs natifs n'existent qu'une fois la bibliotheque chargee. */
    private fun nativeReady() = _state.value !is InferenceEngine.State.Uninitialized &&
        _state.value !is InferenceEngine.State.Initializing

    override fun contextUsed(): Int = if (nativeReady() && _modelLoaded) nativeContextUsed() else 0

    override fun contextCapacity(): Int = if (nativeReady() && _modelLoaded) nativeContextSize() else 0

    override fun recoveryCount(): Int = if (nativeReady()) nativeRecoveryCount() else 0

    /**
     * Regle l'echantillonnage cote natif.
     */
    override suspend fun setSamplingParams(temperature: Float, topP: Float, topK: Int) =
        withContext(llamaDispatcher) {
            nativeSetSamplingParams(temperature, topP, topK)
        }

    /**
     * Repart de zero : cache KV vide, nouveau prompt systeme autorise.
     */
    override suspend fun resetConversation() =
        withContext(llamaDispatcher) {
            nativeResetConversation()
            _readyForSystemPrompt = true
            _state.value = InferenceEngine.State.ModelReady
        }

    /**
     * Send plain text user prompt to LLM, which starts generating tokens in a [Flow]
     */
    override fun sendUserPrompt(
        message: String,
        predictLength: Int,
    ): Flow<String> = flow {
        require(message.isNotEmpty()) { "User prompt discarded due to being empty!" }
        check(_state.value is InferenceEngine.State.ModelReady) {
            "User prompt discarded due to: ${_state.value.javaClass.simpleName}"
        }

        try {
            Log.i(TAG, "Sending user prompt...")
            _readyForSystemPrompt = false
            _state.value = InferenceEngine.State.ProcessingUserPrompt

            processUserPrompt(message, predictLength).let { result ->
                if (result != 0) {
                    Log.e(TAG, "Failed to process user prompt: $result")
                    // Le moteur a remis sa memoire en ordre : il reste utilisable, on ne le bloque pas.
                    throw PromptRejectedException(result, messageRefuse(result))
                }
            }

            Log.i(TAG, "User prompt processed. Generating assistant prompt...")
            _state.value = InferenceEngine.State.Generating
            while (!_cancelGeneration) {
                generateNextToken()?.let { utf8token ->
                    if (utf8token.isNotEmpty()) emit(utf8token)
                } ?: break
            }
            if (_cancelGeneration) {
                Log.i(TAG, "Assistant generation aborted per requested.")
            } else {
                Log.i(TAG, "Assistant generation complete. Awaiting user prompt...")
            }
            _state.value = InferenceEngine.State.ModelReady
        } catch (e: CancellationException) {
            Log.i(TAG, "Assistant generation's flow collection cancelled.")
            _state.value = InferenceEngine.State.ModelReady
            throw e
        } catch (e: PromptRejectedException) {
            _state.value = InferenceEngine.State.ModelReady
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during generation!", e)
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }.flowOn(llamaDispatcher)

    private fun messageRefuse(code: Int) = when (code) {
        1 -> "Les consignes de départ prennent trop de place dans la mémoire du modèle pour lire ce message. " +
            "Raccourcis-les dans Réglages ou choisis un modèle avec une plus grande mémoire."
        3 -> "Impossible de libérer assez de mémoire pour ce message. Raccourcis-le puis réessaie : " +
            "la conversation reste ouverte."
        else -> "Le moteur n'a pas réussi à lire ce message (erreur de calcul, code $code). " +
            "La conversation reste ouverte : réessaie."
    }

    /**
     * Benchmark the model
     */
    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String =
        withContext(llamaDispatcher) {
            check(_state.value is InferenceEngine.State.ModelReady) {
                "Benchmark request discarded due to: $state"
            }
            Log.i(TAG, "Start benchmark (pp: $pp, tg: $tg, pl: $pl, nr: $nr)")
            _readyForSystemPrompt = false   // Just to be safe
            _state.value = InferenceEngine.State.Benchmarking
            benchModel(pp, tg, pl, nr).also {
                _state.value = InferenceEngine.State.ModelReady
            }
        }

    /**
     * Unloads the model and frees resources, or reset error states
     */
    override fun cleanUp() {
        _cancelGeneration = true
        runBlocking(llamaDispatcher) {
            when (val state = _state.value) {
                is InferenceEngine.State.ModelReady -> {
                    Log.i(TAG, "Unloading model and free resources...")
                    _readyForSystemPrompt = false
                    _state.value = InferenceEngine.State.UnloadingModel

                    unload()
                    _modelLoaded = false

                    _state.value = InferenceEngine.State.Initialized
                    Log.i(TAG, "Model unloaded!")
                    Unit
                }

                is InferenceEngine.State.Error -> {
                    Log.i(TAG, "Resetting error states...")
                    // Un echec en cours de route (ex. generation) laisse le modele en memoire.
                    if (_modelLoaded) {
                        unload()
                        _modelLoaded = false
                    }
                    _readyForSystemPrompt = false
                    _state.value = InferenceEngine.State.Initialized
                    Log.i(TAG, "States reset!")
                    Unit
                }

                else -> throw IllegalStateException("Cannot unload model in ${state.javaClass.simpleName}")
            }
        }
    }

    /**
     * Cancel all ongoing coroutines and free GGML backends
     */
    override fun destroy() {
        _cancelGeneration = true
        runBlocking(llamaDispatcher) {
            _readyForSystemPrompt = false
            when(_state.value) {
                is InferenceEngine.State.Uninitialized -> {}
                is InferenceEngine.State.Initialized -> shutdown()
                else -> { unload(); shutdown() }
            }
        }
        llamaScope.cancel()
    }
}
