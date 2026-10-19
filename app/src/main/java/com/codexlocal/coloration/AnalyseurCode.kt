package com.codexlocal.coloration

/**
 * Point d'entree de la coloration : [analyser] lit un texte et dit quels morceaux sont des mots-cles,
 * des chaines, des fonctions, des couleurs... Il ne depend pas d'Android : c'est
 * [com.codexlocal.SyntaxHighlighter] qui transforme les [Jeton] en couleurs a l'ecran.
 */
object AnalyseurCode {

    /** Au-dela, on ne colore pas : l'affichage reste instantane. */
    const val LIMITE = 60_000

    /**
     * Les jetons de [code] pour le langage [langage] (nom, alias ou extension : « kotlin », « kt »,
     * « .py », « html »...). Un langage inconnu ou vide est lu comme du code a la C. Triés, sans chevauchement.
     */
    fun analyser(code: String, langage: String): List<Jeton> {
        if (code.isEmpty() || code.length > LIMITE) return emptyList()
        return try {
            analyserBrut(code, Profils.normaliser(langage))
        } catch (e: RuntimeException) {
            // Un morceau de code surprenant ne doit jamais faire planter l'ecran : pas de couleur, c'est tout.
            emptyList()
        }
    }

    /** Comme [analyser] mais laisse remonter les erreurs (les tests s'en servent pour les repérer). */
    internal fun analyserBrut(code: String, nom: String): List<Jeton> =
        sansCouperLesEmojis(code, lire(code, nom))

    private fun lire(code: String, nom: String): List<Jeton> = when (nom) {
        "plain" -> emptyList()
        "html" -> BalayeurBalisage(code, couleurAlphaEnPremier = false).analyser()
        "xml" -> BalayeurBalisage(code, couleurAlphaEnPremier = true).analyser()
        "css" -> BalayeurCss(code).analyser()
        "yaml" -> BalayeurDonnees(code, BalayeurDonnees.Mode.YAML).analyser()
        "toml" -> BalayeurDonnees(code, BalayeurDonnees.Mode.TOML).analyser()
        "ini" -> BalayeurDonnees(code, BalayeurDonnees.Mode.INI).analyser()
        "markdown" -> BalayeurMarkdown(code).analyser()
        else -> BalayeurCode(code, Profils.pour(nom)).analyser()
    }

    /**
     * Un emoji occupe deux « caracteres » dans un texte Android. Un jeton qui s'arreterait entre les deux
     * couperait l'emoji en deux : on rogne d'un cran de ce cote-la (le jeton disparait s'il n'en reste rien).
     */
    private fun sansCouperLesEmojis(code: String, jetons: List<Jeton>): List<Jeton> {
        fun coupe(position: Int) = position > 0 && position < code.length &&
            Character.isHighSurrogate(code[position - 1]) && Character.isLowSurrogate(code[position])

        if (jetons.none { coupe(it.debut) || coupe(it.fin) }) return jetons
        val sortie = ArrayList<Jeton>(jetons.size)
        for (j in jetons) {
            val debut = if (coupe(j.debut)) j.debut + 1 else j.debut
            val fin = if (coupe(j.fin)) j.fin - 1 else j.fin
            if (fin > debut) sortie.add(if (debut == j.debut && fin == j.fin) j else Jeton(debut, fin, j.genre, j.couleur))
        }
        return sortie
    }
}
