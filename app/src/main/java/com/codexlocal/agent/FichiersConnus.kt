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
 * On garde aussi le numero du message de la conversation qui a apporte cette connaissance : quand
 * ce message sort de la memoire de l'IA (conversation trop longue), le fichier est oublie aussi
 * ([oublierAvant]).
 *
 * La liste est propre a une conversation ET a un projet ([pour]), et se vide quand l'IA perd la
 * memoire de la conversation ([oublier]). Code pur, sans dependance Android.
 */
class FichiersConnus {

    private class Connu(val empreinte: String, val position: Int)

    private var cle: String? = null
    private val connus = HashMap<String, Connu>()

    /** Annonce la conversation et le projet en cours ; repart de zero quand ils changent. */
    fun pour(cle: String) {
        if (cle != this.cle) {
            this.cle = cle
            connus.clear()
        }
    }

    /** L'IA n'a plus la conversation en memoire : elle ne connait plus aucun fichier. */
    fun oublier() = connus.clear()

    /** L'IA ne se souvient plus des messages avant le numero [position] : ses fichiers sont oublies. */
    fun oublierAvant(position: Int) {
        connus.values.removeAll { it.position < position }
    }

    /**
     * Le modele local a remis sa memoire a zero (chargement, nouvelle session, incident).
     * [dernier] : numero du message qu'on lui envoie. Avant sa reponse ce message-la lui arrive
     * encore ; apres, il a pu tout perdre.
     */
    fun memoireVidee(dernier: Int, apresReponse: Boolean) {
        if (apresReponse) oublier() else oublierAvant(dernier)
    }

    /**
     * Le modele local a retire de vieux messages de sa memoire (conversation trop longue). Avant sa
     * reponse, le message qu'on lui envoie ([dernier]) est intact : ce sont les plus vieux qui partent.
     * Pendant sa reponse, le retrait a pu entamer ce message aussi.
     */
    fun memoireReduite(dernier: Int, apresReponse: Boolean) {
        oublierAvant(if (apresReponse) dernier + 1 else dernier)
    }

    /**
     * Chemin -> [empreinte] du contenu que l'IA connait maintenant, apporte par le message numero
     * [position] de la conversation.
     */
    fun ajouter(nouveaux: Map<String, String>, position: Int) {
        for ((chemin, empreinte) in nouveaux) connus[chemin] = Connu(empreinte, position)
    }

    /** Chemin -> empreinte, a donner a [AgentProjet.appliquer]. */
    fun instantane(): Map<String, String> = connus.mapValues { it.value.empreinte }

    companion object {
        /** Identifiant du contenu d'un fichier : change des que le texte change. */
        fun empreinte(texte: String): String =
            MessageDigest.getInstance("SHA-256").digest(texte.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
