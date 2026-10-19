package com.codexlocal.bureau

import android.content.Context

/**
 * Favoris du Bureau : des projets et des fichiers marques d'une etoile (appui long).
 * Chaque favori est une cle texte ; elles sont gardees dans les preferences de l'appli.
 */
class Favoris(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun toutes(): Set<String> = prefs.getStringSet(CLE, emptySet())?.toSet() ?: emptySet()

    fun estFavori(cle: String): Boolean = cle in toutes()

    /** Ajoute ou retire le favori ; renvoie vrai s'il est favori apres l'appel. */
    fun basculer(cle: String): Boolean {
        val actuels = toutes().toMutableSet()
        val maintenant = if (cle in actuels) {
            actuels.remove(cle); false
        } else {
            actuels.add(cle); true
        }
        prefs.edit().putStringSet(CLE, actuels).apply()
        return maintenant
    }

    /** Ce que designe une cle de favori. */
    sealed interface Cible {
        data class DeProjet(val projetUri: String) : Cible
        data class DeFichier(val projetUri: String, val chemin: String) : Cible
    }

    companion object {
        private const val PREFS = "codexlocal_favoris"
        private const val CLE = "liste"
        private const val SEP = "\t"

        fun cleProjet(projetUri: String) = "P$SEP$projetUri"

        fun cleFichier(projetUri: String, chemin: String) = "F$SEP$projetUri$SEP$chemin"

        /** Lit une cle ; null si elle est abimee. */
        fun decoder(cle: String): Cible? {
            val morceaux = cle.split(SEP, limit = 3)
            return when {
                morceaux.size == 2 && morceaux[0] == "P" && morceaux[1].isNotEmpty() ->
                    Cible.DeProjet(morceaux[1])
                morceaux.size == 3 && morceaux[0] == "F" &&
                    morceaux[1].isNotEmpty() && morceaux[2].isNotEmpty() ->
                    Cible.DeFichier(morceaux[1], morceaux[2])
                else -> null
            }
        }
    }
}
