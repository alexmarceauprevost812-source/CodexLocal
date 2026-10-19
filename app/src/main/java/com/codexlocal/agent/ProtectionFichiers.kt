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

    /** Mots qui disent qu'on veut retirer du contenu ou tout refaire : la reduction est alors voulue. */
    private val REDUCTION_VOULUE = Regex(
        """\b(?:supprim|enlev|retir|effac|vid(?:e|er|ez)\b|simplifi|raccourci|reduis|reduit|reduire|""" +
            """reduction|allege|nettoy|nettoi|epur|recommenc|a zero|de zero|from scratch|""" +
            """(?:refais|refait|refaire|reecris|reecrire|remplace|remplacer)\s+tout)"""
    )

    /** Vrai si la demande de l'utilisateur veut retirer du contenu ou tout refaire. */
    fun reductionVoulue(demande: String): Boolean =
        REDUCTION_VOULUE.containsMatchIn(DemandeCreation.normaliser(demande))

    data class Candidat(val chemin: String, val taille: Long)

    /**
     * Dans quel ordre montrer les fichiers quand la place manque : ceux que la demande nomme, puis
     * `index.html` (la page principale), puis les plus petits.
     */
    fun ordreApercu(candidats: List<Candidat>, demande: String): List<Candidat> {
        val texte = DemandeCreation.normaliser(demande)
        fun nomme(chemin: String): Boolean {
            val c = DemandeCreation.normaliser(chemin)
            val nom = c.substringAfterLast('/')
            return texte.contains(c) || (nom.length >= 3 && texte.contains(nom))
        }
        fun rang(c: Candidat) = when {
            nomme(c.chemin) -> 0
            c.chemin.equals("index.html", ignoreCase = true) -> 1
            else -> 2
        }
        return candidats.sortedWith(compareBy({ rang(it) }, { it.taille }, { it.chemin }))
    }
}
