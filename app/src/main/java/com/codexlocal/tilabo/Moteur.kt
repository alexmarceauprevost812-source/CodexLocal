package com.codexlocal.tilabo

import android.app.ActivityManager
import android.content.Context
import com.codexlocal.engine.AiChat
import com.codexlocal.engine.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Le moteur llama.cpp (module :lib, le meme que CodexLocal) qui fait rouler les .gguf
 * directement dans l'appli.
 *
 * Le moteur garde la conversation dans sa memoire (cache KV) : on ne lui envoie que le
 * nouveau message quand la conversation recue est la suite de celle qu'il connait deja.
 * Sinon (autre conversation, message modifie), on repart de zero avec un rappel du fil.
 */
object Moteur {

    data class Msg(val role: String, val contenu: String)

    const val CONSIGNE_DEFAUT =
        "Tu es l'IA locale du Ti-Labo, un laboratoire de developpeur. Reponds en francais, " +
            "clairement. Pour du code, donne des blocs de code complets."

    private var engine: InferenceEngine? = null
    private val verrou = Mutex()

    @Volatile var chemin: String? = null
        private set
    @Volatile var enChargement: String? = null
        private set
    @Volatile var derniereErreur: String? = null
        private set

    private var consigneActive: String? = null
    private var temperatureActive = Float.NaN
    /** La conversation (sans la consigne) que le moteur a deja en memoire. */
    private var connus: List<Msg> = emptyList()

    val nom: String? get() = (chemin ?: dernierChemin)?.let { File(it).name }

    private fun engine(ctx: Context): InferenceEngine =
        engine ?: AiChat.getInferenceEngine(ctx.applicationContext).also { engine = it }

    private suspend fun pret(e: InferenceEngine) {
        val s = e.state.first {
            it !is InferenceEngine.State.Uninitialized && it !is InferenceEngine.State.Initializing
        }
        if (s is InferenceEngine.State.Error) {
            withContext(Dispatchers.Default) { runCatching { e.cleanUp() } }
            oublier()
        }
    }

    private fun oublier() {
        chemin = null
        consigneActive = null
        temperatureActive = Float.NaN
        connus = emptyList()
    }

    private fun memoireLibre(ctx: Context): Long = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
    }.getOrDefault(0L)

    /** Plus il reste de memoire apres le modele, plus la conversation peut etre longue. */
    private fun tailleContexte(ctx: Context, fichier: File): Int {
        val reste = memoireLibre(ctx) - fichier.length()
        val go = 1_000_000_000L
        return when {
            reste > 6 * go -> 16384
            reste > 3 * go -> 8192
            else -> 4096
        }
    }

    /** Le dernier .gguf choisi : recharge tout seul apres que CodexLocal a pris le moteur. */
    @Volatile var dernierChemin: String? = null
        private set

    /**
     * Le moteur est partage avec la jasette de CodexLocal (meme appli, meme llama.cpp) :
     * un seul modele en memoire. Quand le Ti-Labo s'en sert, CodexLocal libere le sien.
     */
    private fun prendreLeMoteur() {
        if (com.codexlocal.LocalEngine.loadedPath != null) com.codexlocal.LocalEngine.unload()
    }

    /** Appele par CodexLocal avant de charger son propre modele. */
    suspend fun liberer() = verrou.withLock {
        val e = engine ?: return@withLock
        if (chemin != null) withContext(Dispatchers.Default) { runCatching { e.cleanUp() } }
        oublier()
    }

    /** Charge un .gguf (attend la fin du chargement). Renvoie le nom du fichier. */
    suspend fun charger(ctx: Context, cheminModele: String): String = verrou.withLock { chargerInterne(ctx, cheminModele) }

    private suspend fun chargerInterne(ctx: Context, cheminModele: String): String {
        prendreLeMoteur()
        val f = File(cheminModele)
        require(f.name.endsWith(".gguf", ignoreCase = true)) { "Ce n'est pas un fichier .gguf" }
        require(f.isFile) { "Fichier introuvable : ${f.path}" }
        require(f.canRead()) {
            "Fichier illisible : ${f.name}. Donne l'acces « Tous les fichiers » au Ti-Labo."
        }
        val e = engine(ctx)
        pret(e)
        if (chemin == f.path) return f.name
        if (chemin != null) withContext(Dispatchers.Default) { runCatching { e.cleanUp() } }
        oublier()
        enChargement = f.name
        derniereErreur = null
        try {
            e.setContextSize(tailleContexte(ctx, f))
            e.loadModel(f.path)
            e.setSamplingParams(0.7f, 0.95f, 40)
            temperatureActive = 0.7f
            e.setSystemPrompt(CONSIGNE_DEFAUT)
            consigneActive = CONSIGNE_DEFAUT
            chemin = f.path
            dernierChemin = f.path
            return f.name
        } catch (ex: Exception) {
            derniereErreur = ex.message ?: ex.javaClass.simpleName
            withContext(Dispatchers.Default) { runCatching { e.cleanUp() } }
            oublier()
            throw ex
        } finally {
            enChargement = null
        }
    }

    suspend fun decharger() = verrou.withLock {
        dernierChemin = null
        val e = engine ?: return@withLock
        if (chemin != null) withContext(Dispatchers.Default) { runCatching { e.cleanUp() } }
        oublier()
    }

    fun memoire(): Pair<Int, Int> {
        val e = engine ?: return 0 to 0
        return runCatching { e.contextUsed() to e.contextCapacity() }.getOrDefault(0 to 0)
    }

    /**
     * Repond a une conversation complete (format OpenAI). Emet le texte au fur et a mesure.
     */
    fun repondre(
        ctx: Context,
        messages: List<Msg>,
        temperature: Float?,
        maxJetons: Int?
    ): Flow<String> = flow {
        verrou.withLock {
            val e = engine(ctx)
            // CodexLocal a pu prendre le moteur entre-temps : on recharge le dernier .gguf du Ti-Labo
            if (com.codexlocal.LocalEngine.loadedPath != null || chemin == null) {
                val dernier = dernierChemin ?: chemin
                check(dernier != null) { "Aucune IA chargee. Choisis un .gguf dans IA locales." }
                if (com.codexlocal.LocalEngine.loadedPath != null) { prendreLeMoteur(); oublier() }
                if (chemin == null) chargerInterne(ctx, dernier)
            }
            pret(e)
            check(chemin != null) { "Le moteur a du etre remis a zero : recharge l'IA." }

            val consigne = messages.filter { it.role == "system" }
                .joinToString("\n\n") { it.contenu }.ifBlank { CONSIGNE_DEFAUT }
            val fil = messages.filter { it.role == "user" || it.role == "assistant" }
            require(fil.isNotEmpty() && fil.last().role == "user") { "Le dernier message doit venir de l'utilisateur" }
            val avant = fil.dropLast(1)
            val question = fil.last().contenu.ifBlank { "…" }

            val temp = temperature ?: 0.7f
            if (temp != temperatureActive) {
                e.setSamplingParams(temp, 0.95f, 40)
                temperatureActive = temp
            }

            val suite = consigne == consigneActive && avant == connus
            val envoi = if (suite) {
                question
            } else {
                e.resetConversation()
                e.setSystemPrompt(consigne)
                consigneActive = consigne
                connus = emptyList()
                if (avant.isEmpty()) question else rappel(avant, question)
            }

            val reponse = StringBuilder()
            try {
                e.sendUserPrompt(envoi, maxJetons?.coerceIn(16, 8192) ?: 2048).collect {
                    reponse.append(it)
                    emit(it)
                }
                connus = avant + Msg("user", question) + Msg("assistant", reponse.toString())
            } catch (ex: Exception) {
                // Conversation incertaine : la prochaine demande repartira de zero avec un rappel.
                connus = listOf(Msg("?", "?"))
                throw ex
            }
        }
    }

    /**
     * Le client renverra la derniere reponse sous une autre forme (ex. appels d'outils remis en JSON) :
     * on retient cette forme pour reconnaitre la suite de la conversation sans repartir de zero.
     */
    fun corrigerDerniereReponse(texte: String) {
        val c = connus
        if (c.isNotEmpty() && c.last().role == "assistant") connus = c.dropLast(1) + Msg("assistant", texte)
    }

    private fun rappel(avant: List<Msg>, question: String): String {
        val b = StringBuilder("Voici notre conversation jusqu'ici :\n\n")
        // Les derniers echanges suffisent ; le moteur coupe au milieu si c'est trop long.
        for (m in avant.takeLast(12)) {
            b.append(if (m.role == "user") "Moi : " else "Toi : ")
            b.append(m.contenu.take(6000)).append("\n\n")
        }
        b.append("Mon nouveau message :\n").append(question)
        return b.toString()
    }
}
