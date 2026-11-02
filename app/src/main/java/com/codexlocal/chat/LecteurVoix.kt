package com.codexlocal.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * 🔊 Moteur de voix (APK perso) : lit à voix haute les réponses, les nouvelles et les articles,
 * avec une voix québécoise (français du Canada) du téléphone, hors ligne quand elle est installée.
 */
object LecteurVoix {

    private var tts: TextToSpeech? = null
    private var pret = false
    private var enAttente: String? = null
    private lateinit var appli: Context

    private val QUEBEC = Locale.CANADA_FRENCH

    private fun prefs() = appli.getSharedPreferences("outils", Context.MODE_PRIVATE)

    private fun demarrer(c: Context) {
        if (tts != null) return
        appli = c.applicationContext
        tts = TextToSpeech(appli) { statut ->
            pret = statut == TextToSpeech.SUCCESS
            if (pret) {
                choisirVoix()
                enAttente?.let { parler(it) }
                enAttente = null
            }
        }
    }

    /** Voix fr-CA choisie (ou la meilleure disponible), sinon français. */
    private fun choisirVoix() {
        val moteur = tts ?: return
        val voulue = prefs().getString("voix", null)
        val voix = voixQuebec()
        val choisie = voix.firstOrNull { it.name == voulue }
            ?: voix.filter { !it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
            ?: voix.maxByOrNull { it.quality }
        if (choisie != null) moteur.voice = choisie
        else if (moteur.setLanguage(QUEBEC) < TextToSpeech.LANG_AVAILABLE) moteur.setLanguage(Locale.FRENCH)
        moteur.setSpeechRate(prefs().getFloat("vitesseVoix", 1.0f))
    }

    /** Les voix en français du Canada installées sur le téléphone. */
    fun voixQuebec(): List<Voice> = runCatching {
        tts?.voices.orEmpty().filter { it.locale.language == "fr" && it.locale.country.equals("CA", true) }
            .sortedByDescending { it.quality }
    }.getOrDefault(emptyList())

    fun choisir(c: Context, nom: String) {
        demarrer(c)
        prefs().edit().putString("voix", nom).apply()
        if (pret) choisirVoix()
    }

    fun vitesse(c: Context, v: Float) {
        demarrer(c)
        prefs().edit().putFloat("vitesseVoix", v).apply()
        tts?.setSpeechRate(v)
    }

    /** Prépare le moteur (les voix sont listées une fois prêt). */
    fun preparer(c: Context) = demarrer(c)

    fun lire(c: Context, texte: String) {
        demarrer(c)
        val propre = nettoyer(texte)
        if (propre.isBlank()) return
        if (pret) parler(propre) else enAttente = propre
    }

    fun arreter() { tts?.stop() }

    fun parle(): Boolean = tts?.isSpeaking == true

    private fun parler(texte: String) {
        val moteur = tts ?: return
        moteur.stop()
        // Le moteur limite la longueur d'une phrase : on découpe par paragraphes.
        texte.split(Regex("\\n{1,}")).flatMap { it.chunked(3500) }.filter { it.isNotBlank() }
            .forEachIndexed { i, morceau ->
                moteur.speak(morceau, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "lecture$i")
            }
    }

    /** Retire le Markdown, les liens, les images et le raisonnement pour une lecture naturelle. */
    internal fun nettoyer(texte: String): String = texte
        .replace(Regex("<think>.*?(</think>|$)", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), " (bloc de code) ")
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("[*_`#>|━•▁▂▃▄▅▆▇█]"), "")
        .replace(Regex("[ \\t]+"), " ")
        .trim()
}
