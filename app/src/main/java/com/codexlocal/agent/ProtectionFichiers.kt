package com.codexlocal.agent

/**
 * Ce qu'on montre a une IA qui ne peut pas relire les fichiers elle-meme (l'equipe) :
 * `texte` est a coller dans sa demande, `connus` donne l'empreinte de chaque fichier montre en
 * entier (voir [FichiersConnus]).
 */
class ApercuFichiers(val texte: String, val connus: Map<String, String>) {
    companion object {
        val VIDE = ApercuFichiers("", emptyMap())
    }
}

/** Petites regles de prudence autour de l'ecriture des fichiers. Code pur, sans Android. */
object ProtectionFichiers {

    /** En dessous de cette taille, un fichier peut raccourcir librement. */
    const val TAILLE_MIN_REDUCTION = 600

    /**
     * Vrai quand la nouvelle version d'un fichier fait moins de 40 % de l'ancienne : l'IA a
     * probablement oublie une grande partie du fichier (elle n'a ecrit que ce qu'on venait de lui
     * demander) et on perdrait presque tout.
     */
    fun reduitTrop(ancien: String, nouveau: String): Boolean =
        ancien.length >= TAILLE_MIN_REDUCTION && nouveau.length * 5 < ancien.length * 2

    /** Tout refaire : la reduction est voulue pour tous les fichiers. */
    private val REDUCTION_GLOBALE = Regex(
        """\b(?:recommenc|a zero|de zero|from scratch|""" +
            """(?:refais|refait|refaire|reecris|reecrire|remplace|remplacer)\s+tout|""" +
            """tout\s+(?:refaire|reecrire|remplacer))"""
    )

    /** Retirer du contenu : voulu pour le fichier dont parle la meme phrase. */
    private val REDUCTION_LOCALE = Regex(
        """\b(?:supprim|enlev|retir|effac|vid(?:e|er|ez)\b|simplifi|raccourci|reduis|reduit|reduire|""" +
            """reduction|allege|nettoy|nettoi|epur)"""
    )

    /** Un nom de fichier dans la phrase : « index.html », « css/style.css ». */
    private val NOM_DE_FICHIER = Regex("""[\w./-]+\.[a-z][a-z0-9]{0,4}\b""")

    /** Les parties de la demande : coupees aux . ! ? ; suivis d'un espace, aux virgules et aux « puis », « et »... */
    private val PARTIES = Regex(
        """[.!?;]+(?=\s|$)|[,\n]|\b(?:puis|ensuite|apres|avant|mais|sauf|et)\b"""
    )

    private fun nomme(texteNormalise: String, chemin: String): Boolean {
        val c = DemandeCreation.normaliser(chemin)
        val nom = c.substringAfterLast('/')
        return texteNormalise.contains(c) || (nom.length >= 3 && texteNormalise.contains(nom))
    }

    /**
     * Vrai si la demande de l'utilisateur veut que le fichier [chemin] perde du contenu : elle dit
     * de tout refaire (« recommence a zero »), ou de retirer quelque chose dans une phrase qui
     * nomme ce fichier ou aucun fichier (« supprime le menu »). « Vide README.md puis corrige
     * index.html » ne le permet que pour README.md.
     */
    fun reductionVoulue(demande: String, chemin: String): Boolean {
        val texte = DemandeCreation.normaliser(demande)
        if (REDUCTION_GLOBALE.containsMatchIn(texte)) return true
        return texte.split(PARTIES).any { partie ->
            REDUCTION_LOCALE.containsMatchIn(partie) &&
                (nomme(partie, chemin) || !NOM_DE_FICHIER.containsMatchIn(partie))
        }
    }

    data class Candidat(val chemin: String, val taille: Long)

    /**
     * Dans quel ordre montrer les fichiers quand la place manque : ceux que la demande nomme, puis
     * `index.html` (la page principale), puis les plus petits.
     */
    fun ordreApercu(candidats: List<Candidat>, demande: String): List<Candidat> {
        val texte = DemandeCreation.normaliser(demande)
        fun rang(c: Candidat) = when {
            nomme(texte, c.chemin) -> 0
            c.chemin.equals("index.html", ignoreCase = true) -> 1
            else -> 2
        }
        return candidats.sortedWith(compareBy({ rang(it) }, { it.taille }, { it.chemin }))
    }
}
