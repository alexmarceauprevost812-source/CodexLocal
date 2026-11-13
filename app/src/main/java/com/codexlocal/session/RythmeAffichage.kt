package com.codexlocal.session

/**
 * Rythme de rafraîchissement d'une réponse en cours d'écriture.
 *
 * Chaque rafraîchissement refait tout le message (Markdown + couleurs + mise en page) sur le fil
 * d'affichage. Sur un long fichier de code, ce travail peut durer plus longtemps que l'intervalle :
 * l'écran ne répond plus, puis Android propose de fermer l'appli. On mesure donc le coût réel du
 * dernier rendu et on laisse toujours à l'écran bien plus de temps libre que de temps occupé.
 */
object RythmeAffichage {
    /** Le rendu n'occupe jamais plus d'environ 1/6 du temps du fil d'affichage. */
    const val FACTEUR = 6

    /** Au pire, le texte avance toutes les 3 secondes (la réponse finale est toujours affichée). */
    const val MAX_MS = 3_000L

    fun intervalle(longueur: Int, perso: Boolean, coutRenduMs: Long): Long {
        val base = if (perso) (33L + longueur / 80).coerceAtMost(260L)
        else (80L + longueur / 40).coerceAtMost(500L)
        return maxOf(base, coutRenduMs.coerceAtLeast(0) * FACTEUR).coerceAtMost(MAX_MS)
    }
}
