package com.codexlocal.coloration

/** Le role d'un morceau de code : c'est lui qui decide de la couleur. */
enum class Genre {
    COMMENTAIRE, TEXTE, ECHAPPEMENT, NOMBRE, MOT_CLE, CONSTANTE, TYPE, FONCTION, DECLARATION,
    PROPRIETE, INTEGRE, ANNOTATION, VARIABLE, OPERATEUR, PONCTUATION,
    PAREN_1, PAREN_2, PAREN_3,
    BALISE, ATTRIBUT, CLE, SELECTEUR, TITRE, LIEN,
    /** Une couleur ecrite dans le code (#FF9A3D, rgb(...), 0xFF...) : [Jeton.couleur] la donne. */
    COULEUR
}

/** Un morceau [debut, fin) du texte analyse. Les jetons sont tries et ne se chevauchent jamais. */
class Jeton(val debut: Int, val fin: Int, val genre: Genre, val couleur: Int = 0) {
    override fun toString() = "$genre[$debut,$fin)"
}

/** Liste de jetons en construction : ignore les morceaux vides et fusionne les voisins identiques. */
internal class Sortie {
    val liste = ArrayList<Jeton>()

    fun ajouter(debut: Int, fin: Int, genre: Genre, couleur: Int = 0) {
        if (fin <= debut) return
        val dernier = liste.lastOrNull()
        if (dernier != null) {
            check(debut >= dernier.fin) { "jetons qui se chevauchent : $dernier puis $genre[$debut,$fin)" }
            if (dernier.fin == debut && dernier.genre == genre && couleur == 0 &&
                (genre == Genre.TEXTE || genre == Genre.COMMENTAIRE || genre == Genre.OPERATEUR)
            ) {
                liste[liste.lastIndex] = Jeton(dernier.debut, fin, genre)
                return
            }
        }
        liste.add(Jeton(debut, fin, genre, couleur))
    }
}
