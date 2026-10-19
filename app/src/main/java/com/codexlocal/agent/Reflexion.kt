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

    /**
     * Le raisonnement se termine au PREMIER `</think>` : un `</think>` ecrit plus loin dans la
     * reponse (le code d'un fichier qui parle de cette balise, par exemple) n'en fait pas partie.
     * Un `</think>` CITE (entre guillemets, colle a une ponctuation, au milieu d'une phrase) n'est
     * pas une fin de raisonnement. On ne cherche volontairement pas si la balise est dans un bloc
     * de code : un raisonnement qui cite le format des fichiers ou ecrit un brouillon de code ne
     * doit jamais faire passer sa fin pour du code (il creerait des fichiers).
     */
    private fun finDuRaisonnement(texte: String): Int {
        val ouvert = texte.trimStart().startsWith(OUVRE)
        var depuis = 0
        while (true) {
            val i = texte.indexOf(FERME, depuis)
            if (i < 0) return -1
            if (!citee(texte, i, dansUnePhrase = !ouvert)) return i
            depuis = i + FERME.length
        }
    }

    /**
     * Le `</think>` en [i] est cite dans une phrase (« la balise `</think>` termine... ») et non
     * une vraie fin de raisonnement : colle a un guillemet, a une apostrophe inversee ou a une
     * ponctuation (une vraie fin est suivie d'un retour a la ligne), ou entoure
     * d'espaces au milieu d'une ligne qui a du texte des deux cotes.
     */
    private fun citee(texte: String, i: Int, dansUnePhrase: Boolean): Boolean {
        val apres = i + FERME.length
        val avant = texte.getOrNull(i - 1)
        val suite = texte.getOrNull(apres)
        if (avant != null && avant in "`\"'\u00AB\u201C") return true
        if (suite != null && suite in "`\"'\u00BB\u201D.,;:!?)]}") return true
        if (!dansUnePhrase) return false
        val debutLigne = texte.lastIndexOf('\n', i - 1) + 1
        val finLigne = texte.indexOf('\n', apres).let { if (it < 0) texte.length else it }
        val gauche = texte.substring(debutLigne, i)
        val droite = texte.substring(apres, finLigne)
        return gauche.endsWith(" ") && droite.startsWith(" ") && gauche.isNotBlank() && droite.isNotBlank()
    }

    fun separer(texte: String): Decoupe {
        val fin = finDuRaisonnement(texte)
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
        val fin = finDuRaisonnement(texte)
        if (fin >= 0) {
            val apres = fin + FERME.length
            return "\n".repeat(texte.substring(0, apres).count { it == '\n' }) + texte.substring(apres)
        }
        val debut = texte.indexOf(OUVRE)
        if (debut >= 0 && texte.substring(0, debut).isBlank()) {
            return "\n".repeat(texte.count { it == '\n' })
        }
        return texte
    }
}
