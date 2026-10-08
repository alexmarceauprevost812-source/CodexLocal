package com.codexlocal.editor

/**
 * Compte les lignes ajoutees et supprimees entre deux versions d'un fichier.
 *
 * Jusqu'a 2000 lignes on calcule la vraie plus longue sous-sequence commune
 * (diff exact, insensible a l'ordre des modifications) ; au-dela on bascule sur
 * une comparaison par frequences, lineaire et instantanee.
 */
object DiffStats {

    data class Resultat(val ajoutees: Int, val supprimees: Int) {
        val inchange: Boolean get() = ajoutees == 0 && supprimees == 0

        fun resume(): String = "+$ajoutees  −$supprimees"
    }

    private const val LIMITE_EXACTE = 2000

    fun comparer(avant: String, apres: String): Resultat {
        if (avant == apres) return Resultat(0, 0)
        val a = avant.lines()
        val b = apres.lines()
        return if (a.size <= LIMITE_EXACTE && b.size <= LIMITE_EXACTE) {
            exact(a, b)
        } else {
            frequences(a, b)
        }
    }

    /** Diff exact via LCS, avec deux lignes de memoire seulement. */
    private fun exact(a: List<String>, b: List<String>): Resultat {
        val n = a.size
        val m = b.size
        var precedente = IntArray(m + 1)
        var courante = IntArray(m + 1)

        for (i in 1..n) {
            for (j in 1..m) {
                courante[j] = if (a[i - 1] == b[j - 1]) {
                    precedente[j - 1] + 1
                } else {
                    maxOf(precedente[j], courante[j - 1])
                }
            }
            val tampon = precedente
            precedente = courante
            courante = tampon
            java.util.Arrays.fill(courante, 0)
        }
        val commun = precedente[m]
        return Resultat(b.size - commun, a.size - commun)
    }

    /** Repli lineaire : nombre de lignes presentes en plus / en moins. */
    private fun frequences(a: List<String>, b: List<String>): Resultat {
        val compteA = a.groupingBy { it }.eachCount()
        val compteB = b.groupingBy { it }.eachCount()
        var ajoutees = 0
        var supprimees = 0
        (compteA.keys + compteB.keys).forEach { ligne ->
            val ca = compteA[ligne] ?: 0
            val cb = compteB[ligne] ?: 0
            if (cb > ca) ajoutees += cb - ca
            if (ca > cb) supprimees += ca - cb
        }
        return Resultat(ajoutees, supprimees)
    }
}
