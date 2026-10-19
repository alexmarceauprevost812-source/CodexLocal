package com.codexlocal.agent

import java.text.Normalizer

/**
 * Devine si un message demande de CREER ou MODIFIER quelque chose (un site, un fichier, un
 * programme) plutot que de poser une question. Sert a relancer l'IA quand elle a seulement
 * parle au lieu d'ecrire les fichiers. Code pur, sans Android.
 */
object DemandeCreation {

    private val VERBES = Regex(
        """\b(fais|fait|faire|fabrique|fabriquer|cree|crees|creer|construis|construire|genere|generer|""" +
            """ecris|ecrire|developpe|developper|programme|programmer|realise|realiser|ajoute|ajouter|""" +
            """modifie|modifier|corrige|corriger|change|changer|refais|refaire|mets|mettre|monte|invente)\b"""
    )

    /** Un message qui commence ainsi est une question, meme s'il contient un verbe comme « faire ». */
    private val QUESTION = Regex(
        """^(c'est quoi|qu'est|quel|quelle|quels|quelles|pourquoi|comment|est-ce|qui |ou |combien|""" +
            """explique|expliquer|dis-moi|dis moi|que |quoi |a quoi|peux-tu m'expliquer|peux tu m'expliquer)"""
    )

    fun estUneDemande(message: String): Boolean {
        val texte = normaliser(message)
        if (texte.isBlank()) return false
        if (QUESTION.containsMatchIn(texte)) return false
        return VERBES.containsMatchIn(texte)
    }

    private fun normaliser(s: String): String =
        Normalizer.normalize(s.lowercase().replace('’', '\''), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()
}
