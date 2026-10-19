package com.codexlocal.agent

/**
 * Les modeles « qui reflechissent » (DeepSeek-R1...) ecrivent d'abord leur raisonnement, puis
 * `</think>`, puis la vraie reponse. Le raisonnement ne doit ni s'afficher en entier ni servir a
 * creer des fichiers. Certains modeles n'ecrivent que `</think>` (l'ouverture est deja dans leur
 * modele de conversation). Code pur, sans Android.
 */
object Reflexion {

    private const val OUVRE = "<think>"
    private const val FERME = "</think>"

    /**
     * [reflexion] : le raisonnement (null s'il n'y en a pas) ; [enCours] : il n'est pas fini,
     * la reponse n'a pas commence ; [reponse] : ce qui suit le raisonnement.
     */
    data class Decoupe(val reflexion: String?, val enCours: Boolean, val reponse: String)

    fun separer(texte: String): Decoupe {
        val fin = texte.lastIndexOf(FERME)
        if (fin >= 0) {
            val raisonnement = texte.substring(0, fin).trim().removePrefix(OUVRE).trim()
            return Decoupe(raisonnement, false, texte.substring(fin + FERME.length).trimStart('\n', ' '))
        }
        val debut = texte.indexOf(OUVRE)
        if (debut >= 0 && texte.substring(0, debut).isBlank()) {
            return Decoupe(texte.substring(debut + OUVRE.length).trim(), true, "")
        }
        return Decoupe(null, false, texte)
    }

    /** Nombre de mots du raisonnement, pour l'afficher. */
    fun mots(reflexion: String): Int = reflexion.split(Regex("\\s+")).count { it.isNotEmpty() }

    /**
     * Le texte sans le raisonnement, mais avec le MEME nombre de lignes : le raisonnement est
     * remplace par des lignes vides, pour que les numeros de ligne restent justes.
     */
    fun masquer(texte: String): String {
        val fin = texte.lastIndexOf(FERME)
        if (fin >= 0) {
            val finDuRaisonnement = fin + FERME.length
            return "\n".repeat(texte.substring(0, finDuRaisonnement).count { it == '\n' }) +
                texte.substring(finDuRaisonnement)
        }
        val debut = texte.indexOf(OUVRE)
        if (debut >= 0 && texte.substring(0, debut).isBlank()) {
            return "\n".repeat(texte.count { it == '\n' })
        }
        return texte
    }
}
