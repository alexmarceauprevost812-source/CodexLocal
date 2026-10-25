package com.codexlocal.agent

/**
 * Repere un petit modele qui « radote » : la meme ligne (ou le meme groupe de lignes)
 * qui revient plusieurs fois de suite, sans fin.
 *
 * Exemple vu sur un telephone :
 *   EXACT DEPLOYAGE : 0 # N'oubliez pas de definir la date
 *   EXACT DEPLOYAGE : 0 # N'oubliez pas de definir la date
 *   EXACT DEPLOYAGE : 0 # N'oubliez pas de definir la date
 *   ...
 *
 * Le code normal repete aussi des lignes courtes (« } », « </div> ») : on ne compte donc que
 * les repetitions assez longues pour ne pas couper une vraie reponse.
 */
object DetecteurBoucle {

    /** Nombre de fois qu'un meme groupe doit revenir d'affilee. */
    const val REPETITIONS = 3

    /** Taille max (en lignes) du groupe qui se repete. */
    const val GROUPE_MAX = 6

    /** Un groupe trop court (en caracteres) n'est pas considere comme une boucle. */
    const val CARACTERES_MIN = 30

    /**
     * Renvoie la longueur du texte a garder (tout ce qui precede les repetitions en trop),
     * ou null si le texte ne boucle pas.
     */
    fun boucle(texte: String): Int? {
        // On ne regarde que les lignes terminees : la derniere peut etre en cours d'ecriture.
        val fin = texte.lastIndexOf('\n')
        if (fin <= 0) return null
        val lignes = texte.substring(0, fin).split('\n')
        val utiles = ArrayList<Pair<String, Int>>() // (ligne nettoyee, position de fin dans le texte)
        var pos = 0
        for (l in lignes) {
            pos += l.length + 1
            val propre = l.trim()
            if (propre.isNotEmpty()) utiles += propre to pos
        }
        for (taille in 1..GROUPE_MAX) {
            val besoin = taille * REPETITIONS
            if (utiles.size < besoin) break
            val dernier = utiles.subList(utiles.size - taille, utiles.size).map { it.first }
            if (dernier.sumOf { it.length } < CARACTERES_MIN) continue
            var identique = true
            for (r in 1 until REPETITIONS) {
                val debut = utiles.size - taille * (r + 1)
                val groupe = utiles.subList(debut, debut + taille).map { it.first }
                if (groupe != dernier) { identique = false; break }
            }
            if (identique) {
                // On garde la premiere occurrence du groupe, on coupe les copies.
                val premiereFin = utiles[utiles.size - taille * (REPETITIONS - 1) - 1].second
                return premiereFin.coerceAtMost(texte.length)
            }
        }
        return null
    }
}
