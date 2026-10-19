package com.codexlocal.agent

import com.codexlocal.studio.StudioParser

/** Ce que l'IA demande de faire dans le projet. */
sealed interface ActionFichier {

    /**
     * Creer ou remplacer un fichier. `complet` est faux quand le bloc n'a pas ete ferme par
     * `=== FIN ===` : la reponse a pu etre coupee avant la fin du fichier.
     */
    data class Ecrire(val chemin: String, val contenu: String, val complet: Boolean = true) :
        ActionFichier

    /** Lire un fichier existant : son contenu est renvoye a l'IA au tour suivant. */
    data class Lire(val chemin: String) : ActionFichier
}

/**
 * Lecture des reponses de l'IA. Code pur, sans dependance Android.
 *
 * Format :
 * ```
 * === FICHIER: src/main.py ===
 * (contenu complet)
 * === FIN ===
 * === LIRE: README.md ===
 * ```
 */
object ActionsFichiers {

    const val MAX_ECRITURES = 20
    const val MAX_LECTURES = 4

    private val marqueurEcrire =
        Regex("""^\s*={2,}\s*FICHIER\s*:\s*(.+?)\s*=*\s*$""", RegexOption.IGNORE_CASE)
    private val marqueurLire =
        Regex("""^\s*={2,}\s*LIRE\s*:\s*(.+?)\s*=*\s*$""", RegexOption.IGNORE_CASE)
    private val marqueurFin =
        Regex("""^\s*={2,}\s*FIN\s*={2,}\s*$""", RegexOption.IGNORE_CASE)

    fun extraire(reponse: String): List<ActionFichier> {
        val resultat = ArrayList<ActionFichier>()
        var chemin: String? = null
        val lignes = ArrayList<String>()
        var ecritures = 0
        var lectures = 0

        fun fermer(complet: Boolean) {
            val c = chemin ?: return
            if (c.isNotBlank() && ecritures < MAX_ECRITURES) {
                resultat.add(ActionFichier.Ecrire(c, contenuFichier(lignes), complet))
                ecritures++
            }
            chemin = null
            lignes.clear()
        }

        for (ligne in reponse.lines()) {
            val ecrire = marqueurEcrire.matchEntire(ligne)
            val lire = marqueurLire.matchEntire(ligne)
            when {
                ecrire != null -> {
                    fermer(true)
                    chemin = StudioParser.nettoyerChemin(ecrire.groupValues[1])
                }
                lire != null -> {
                    fermer(true)
                    val c = StudioParser.nettoyerChemin(lire.groupValues[1])
                    val dejaDemande = resultat.any { it is ActionFichier.Lire && it.chemin == c }
                    if (c.isNotBlank() && lectures < MAX_LECTURES && !dejaDemande) {
                        resultat.add(ActionFichier.Lire(c))
                        lectures++
                    }
                }
                marqueurFin.matches(ligne) -> fermer(true)
                chemin != null -> lignes.add(ligne)
            }
        }
        fermer(false)
        return resultat
    }

    /**
     * Le contenu d'un fichier : lignes vides aux extremites retirees, et clôture ```
     * eventuelle enlevee (le texte qui suivrait la cloture est du commentaire, pas du fichier).
     */
    internal fun contenuFichier(lignes: List<String>): String {
        var l = lignes.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        if (l.isNotEmpty() && l.first().trimStart().startsWith("```")) {
            val fin = l.indexOfLast { it.trim() == "```" }
            l = if (fin > 0) l.subList(1, fin) else l.drop(1)
        }
        return if (l.isEmpty()) "" else l.joinToString("\n") + "\n"
    }
}
