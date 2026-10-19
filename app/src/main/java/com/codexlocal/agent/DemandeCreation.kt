package com.codexlocal.agent

import java.text.Normalizer

/**
 * Devine si un message demande de CREER ou MODIFIER quelque chose (un site, un fichier, un
 * programme) plutot que de poser une question. Sert a relancer l'IA quand elle a seulement
 * parle au lieu d'ecrire les fichiers. Code pur, sans Android.
 */
object DemandeCreation {

    /** Des verbes qui ne sont jamais un nom : « Fais-moi un site », « Ajoute un bouton ». */
    private const val IMPERATIFS_SURS =
        "fais|fabrique|cree|construis|genere|ecris|developpe|realise|ajoute|rajoute|" +
            "modifie|corrige|refais|invente|remplace"

    /**
     * Des mots qui sont aussi des noms (« Programme TV », « Mets preferes », « le change ») :
     * ce sont des ordres seulement s'ils sont suivis d'un article ou d'un pronom (« Mets un fond noir »).
     */
    private const val IMPERATIFS_AMBIGUS = "mets|programme|change|monte|complete"

    private const val ARTICLES =
        "un|une|des|le|la|les|l'|ce|cet|cette|ces|mon|ma|mes|ton|ta|tes|son|sa|ses|" +
            "notre|nos|moi|nous|tout|toute|tous"

    private const val DEBUT =
        """(?:^|[.!?;:,\n]\s*|\b(?:et|puis|ensuite|alors|donc|maintenant|ok|salut|bonjour|bon|stp|svp|plait)\s+)"""

    /** « Fais-moi voir le fichier », « fais-moi savoir » : on veut regarder ou savoir, pas creer. */
    private const val PAS_POUR_VOIR =
        """(?!(?:-moi|-nous|-lui)?\s+(?:voir|savoir|lire|comprendre|apprendre|decouvrir|visiter|penser)\b)"""

    private const val INFINITIFS =
        "faire|fabriquer|creer|construire|generer|ecrire|developper|programmer|realiser|ajouter|" +
            "rajouter|modifier|corriger|changer|refaire|mettre|monter|inventer|completer|remplacer"

    /**
     * Un ordre : le verbe est au debut du message ou d'une phrase (« Fais-moi un site », « ok,
     * ajoute un bouton »). Les mots comme « fait » ou « programme » au milieu d'une phrase
     * (« mon programme fait quoi ? ») ne comptent pas, ni « fais-tu ... ? ».
     */
    private val ORDRE = Regex(
        DEBUT + """(?:$IMPERATIFS_SURS)\b(?!-tu\b|-t-|-il\b|-elle\b|-on\b|-vous\b)$PAS_POUR_VOIR"""
    )

    private val ORDRE_AMBIGU = Regex(
        DEBUT + """(?:$IMPERATIFS_AMBIGUS)(?:-moi|-nous)?\s+(?:$ARTICLES)\b"""
    )

    /** Une demande polie ou un souhait : « peux-tu me faire », « je voudrais ajouter », « il faut corriger ». */
    private val SOUHAIT = Regex(
        """\b(?:peux[- ]?tu|pourrais[- ]?tu|tu peux|tu pourrais|pouvez[- ]vous|veux[- ]tu|j'?veux|""" +
            """je veux|je voudrais|j'aimerais|j'aimerai|je souhaite|il faut|il faudrait|on veut|""" +
            """on doit|je dois|besoin de|besoin d'|essaie de|essaye de)""" +
            """(?:\s+|(?<=d'))""" +
            """(?:(?:(?:me|nous|moi|bien|aussi|maintenant|encore|donc)\s+)|m')*(?:$INFINITIFS)\b$PAS_POUR_VOIR"""
    )

    /** Un message qui commence ainsi est une question, meme s'il contient un verbe comme « faire ». */
    private val QUESTION = Regex(
        """^(c'est quoi|qu'est|quel|quelle|quels|quelles|pourquoi|comment|est-ce|qui |ou |combien|""" +
            """explique|expliquer|dis-moi|dis moi|que |quoi |a quoi|peux-tu m'expliquer|peux tu m'expliquer)"""
    )

    /** Les phrases : coupees aux . ! ? ; suivis d'un espace (un nom comme index.html reste entier) et aux retours a la ligne. */
    private val PHRASES = Regex("""[.!?;]+(?=\s|$)|\n+""")

    /**
     * Vrai si UNE phrase du message est un ordre ou une demande de creation. Une phrase qui
     * commence comme une question n'en est pas une, mais elle ne cache pas les phrases suivantes
     * (« Comment ca marche ? Puis corrige le bug »).
     */
    fun estUneDemande(message: String): Boolean {
        val texte = normaliser(message)
        if (texte.isBlank()) return false
        return texte.split(PHRASES).map { it.trim() }.filter { it.isNotEmpty() }.any(::phraseDemandeCreation)
    }

    private fun phraseDemandeCreation(phrase: String): Boolean {
        if (QUESTION.containsMatchIn(phrase)) return false
        return ORDRE.containsMatchIn(phrase) || ORDRE_AMBIGU.containsMatchIn(phrase) ||
            SOUHAIT.containsMatchIn(phrase)
    }

    private fun normaliser(s: String): String =
        Normalizer.normalize(s.lowercase().replace('’', '\''), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()
}
