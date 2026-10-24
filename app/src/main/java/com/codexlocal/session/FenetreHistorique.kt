package com.codexlocal.session

import com.codexlocal.Message

/**
 * Historique envoye aux modeles en ligne.
 *
 * Chaque requete renvoie toute la conversation. Dans une tres longue session elle finirait par depasser
 * ce que le modele accepte (et par couter cher) : on garde les messages les plus recents qui tiennent dans
 * le budget, sans jamais couper un message en deux. La conversation affichee, elle, reste entiere.
 */
object FenetreHistorique {

    /** Environ 30 000 jetons : large pour de longues discussions, loin des limites des modeles. */
    const val MAX_CARACTERES = 120_000

    /** Numero du premier message envoye : tout ce qui precede est oublie de l'IA en ligne. */
    fun debut(messages: List<Message>, maxCaracteres: Int = MAX_CARACTERES): Int =
        messages.size - fenetrer(messages, maxCaracteres).size

    fun fenetrer(messages: List<Message>, maxCaracteres: Int = MAX_CARACTERES): List<Message> {
        if (messages.size <= 1) return messages
        var total = 0
        var debut = messages.size
        for (i in messages.indices.reversed()) {
            val taille = messages[i].content.length
            // Le dernier message part toujours, meme s'il est a lui seul au-dessus du budget.
            if (debut < messages.size && total + taille > maxCaracteres) break
            total += taille
            debut = i
        }
        if (debut == 0) return messages
        // Les fournisseurs attendent une conversation qui commence par l'utilisateur.
        while (debut < messages.size - 1 && !messages[debut].isUser) debut++
        return messages.subList(debut, messages.size).toList()
    }
}
