package com.codexlocal.session

import com.codexlocal.Message

/**
 * Rappel des echanges precedents pour le modele local.
 *
 * Le modele local garde la conversation dans sa memoire, mais cette memoire repart a zero quand on
 * change de session, qu'on rouvre l'appli ou qu'on change de mode. Pour que la discussion affichee
 * continue vraiment, le premier message envoye apres une remise a zero est precede d'un court rappel
 * des derniers echanges (les plus recents d'abord gardes, chacun raccourci).
 */
object RappelConversation {

    /** Taille maximale du rappel, en caracteres (environ 1 800 jetons). */
    const val BUDGET_PAR_DEFAUT = 6_000

    /** Taille maximale gardee de chaque message du rappel. */
    const val MAX_PAR_MESSAGE = 900

    const val DEBUT = "[Rappel des échanges précédents de cette conversation. " +
        "Sers-t'en comme contexte, sans le recopier ni y répondre.]"
    const val FIN = "[Fin du rappel. Voici le nouveau message de l'utilisateur :]"

    /**
     * Rappel construit a partir des messages qui precedent celui qu'on envoie, ou "" s'il n'y a rien
     * d'utile a rappeler.
     */
    fun construire(avant: List<Message>, budget: Int = BUDGET_PAR_DEFAUT): String {
        val lignes = ArrayDeque<String>()
        var reste = budget
        for (m in avant.asReversed()) {
            val texte = nettoyer(m.content)
            if (texte.isEmpty()) continue
            val ligne = (if (m.isUser) "Utilisateur : " else "Assistant : ") + raccourcir(texte, MAX_PAR_MESSAGE)
            // On garde au moins le message le plus recent, puis on s'arrete quand la place manque.
            if (lignes.isNotEmpty() && ligne.length > reste) break
            lignes.addFirst(ligne)
            reste -= ligne.length
            if (reste <= 0) break
        }
        if (lignes.isEmpty()) return ""
        return DEBUT + "\n" + lignes.joinToString("\n") + "\n" + FIN
    }

    /** Texte sur une seule ligne ; "" pour les messages vides ou qui ne sont qu'une erreur. */
    private fun nettoyer(contenu: String): String {
        val texte = contenu.trim().replace(Regex("\\s+"), " ")
        return if (texte.isEmpty() || texte.startsWith("[erreur]")) "" else texte
    }

    /** Garde le debut et la fin d'un message trop long. */
    fun raccourcir(texte: String, max: Int): String {
        if (texte.length <= max) return texte
        val marque = " […] "
        val debut = (max - marque.length) * 2 / 3
        val fin = max - marque.length - debut
        return texte.take(debut) + marque + texte.takeLast(fin)
    }
}
