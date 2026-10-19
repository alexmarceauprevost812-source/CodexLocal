package com.codexlocal.agent

import java.security.MessageDigest

/**
 * Les fichiers du projet dont l'IA connait le contenu EXACT dans la conversation en cours : ceux
 * qu'elle a lus en entier et ceux qu'elle a ecrits elle-meme. Seuls ceux-la peuvent etre remplaces
 * sans danger ; un autre fichier, elle l'ecrirait de memoire et ferait disparaitre ce qu'il contient.
 *
 * Pour chaque fichier on garde l'[empreinte] de ce que l'IA a vu : si le fichier a change depuis
 * (modifie a la main, annulation...), l'IA ne le connait plus et il redevient protege.
 *
 * La liste est propre a une conversation ET a un projet ([pour]), et se vide quand l'IA perd la
 * memoire de la conversation ([oublier]). Code pur, sans dependance Android.
 */
class FichiersConnus {

    private var cle: String? = null
    private val empreintes = HashMap<String, String>()

    /** Annonce la conversation et le projet en cours ; repart de zero quand ils changent. */
    fun pour(cle: String) {
        if (cle != this.cle) {
            this.cle = cle
            empreintes.clear()
        }
    }

    /** L'IA n'a plus la conversation en memoire : elle ne connait plus aucun fichier. */
    fun oublier() = empreintes.clear()

    /** Chemin -> [empreinte] du contenu que l'IA connait maintenant. */
    fun ajouter(nouveaux: Map<String, String>) {
        empreintes.putAll(nouveaux)
    }

    /** Une copie, a donner a [AgentProjet.appliquer]. */
    fun instantane(): Map<String, String> = HashMap(empreintes)

    companion object {
        /** Identifiant du contenu d'un fichier : change des que le texte change. */
        fun empreinte(texte: String): String =
            MessageDigest.getInstance("SHA-256").digest(texte.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
