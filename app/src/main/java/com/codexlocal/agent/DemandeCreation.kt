package com.codexlocal.agent

import java.text.Normalizer

/**
 * Devine si un message demande de CREER ou MODIFIER quelque chose (un site, un fichier, un
 * programme) plutot que de poser une question. Sert a relancer l'IA quand elle a seulement
 * parle au lieu d'ecrire les fichiers. Code pur, sans Android.
 */
object DemandeCreation {

    private const val IMPERATIFS =
        "fais|fabrique|cree|construis|genere|ecris|developpe|programme|realise|ajoute|rajoute|" +
            "modifie|corrige|change|refais|mets|monte|invente|complete|remplace"

    private const val INFINITIFS =
        "faire|fabriquer|creer|construire|generer|ecrire|developper|programmer|realiser|ajouter|" +
            "rajouter|modifier|corriger|changer|refaire|mettre|monter|inventer|completer|remplacer"

    /**
     * Un ordre : le verbe est au debut du message ou d'une phrase (« Fais-moi un site », « ok,
     * ajoute un bouton »). Les mots comme « fait » ou « programme » au milieu d'une phrase
     * (« mon programme fait quoi ? ») ne comptent pas, ni « fais-tu ... ? ».
     */
    private val ORDRE = Regex(
        """(?:^|[.!?;:,\n]\s*|\b(?:et|puis|ensuite|alors|donc|maintenant|ok|salut|bonjour|bon|stp|svp|plait)\s+)""" +
            """(?:$IMPERATIFS)\b(?!-tu\b|-t-|-il\b|-elle\b|-on\b|-vous\b)"""
    )

    /** Une demande polie ou un souhait : « peux-tu me faire », « je voudrais ajouter », « il faut corriger ». */
    private val SOUHAIT = Regex(
        """\b(?:peux[- ]?tu|pourrais[- ]?tu|tu peux|tu pourrais|pouvez[- ]vous|veux[- ]tu|j'?veux|""" +
            """je veux|je voudrais|j'aimerais|j'aimerai|je souhaite|il faut|il faudrait|on veut|""" +
            """on doit|je dois|besoin de|besoin d'|essaie de|essaye de)""" +
            """(?:\s+|(?<=d'))""" +
            """(?:(?:(?:me|nous|moi|bien|aussi|maintenant|encore|donc)\s+)|m')*(?:$INFINITIFS)\b"""
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
        return ORDRE.containsMatchIn(texte) || SOUHAIT.containsMatchIn(texte)
    }

    private fun normaliser(s: String): String =
        Normalizer.normalize(s.lowercase().replace('’', '\''), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()
}
