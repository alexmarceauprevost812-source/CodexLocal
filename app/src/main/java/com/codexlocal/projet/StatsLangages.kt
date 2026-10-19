package com.codexlocal.projet

import com.codexlocal.project.FichierProjet
import com.codexlocal.project.Langages
import kotlin.math.roundToInt

/** La barre des langages d'un projet, comme sur GitHub. */
object StatsLangages {

    data class Part(val nom: String, val couleur: Int, val fraction: Float) {
        val pourcent: Int get() = (fraction * 100).roundToInt().coerceAtLeast(1)
    }

    private val ignores = setOf("Fichier", "Texte", "Journal")

    /** Parts par langage, de la plus grande a la plus petite, selon la taille des fichiers. */
    fun calculer(fichiers: List<FichierProjet>): List<Part> {
        val poids = LinkedHashMap<String, Pair<Int, Long>>()
        for (f in fichiers) {
            val langage = Langages.pour(f.nom)
            if (langage.nom in ignores) continue
            val (couleur, total) = poids[langage.nom] ?: (langage.couleur to 0L)
            poids[langage.nom] = couleur to total + f.taille.coerceAtLeast(1L)
        }
        val somme = poids.values.sumOf { it.second }
        if (somme <= 0L) return emptyList()
        return poids.entries
            .map { (nom, v) -> Part(nom, v.first, v.second.toFloat() / somme) }
            .sortedByDescending { it.fraction }
    }
}
