package com.codexlocal.session

/** Choix de la taille de memoire de conversation du modele local. */
object ContexteLocal {

    /** Taille habituelle, sure sur la plupart des telephones. */
    const val BASE = 8_192

    /** Grande memoire : le modele se souvient deux fois plus longtemps. */
    const val GRANDE = 16_384

    private const val GIO = 1024L * 1024 * 1024

    /** Marge de memoire libre (hors modele) a partir de laquelle on prend la grande memoire. */
    private const val MARGE_GRANDE = 7L * GIO / 2

    /**
     * 16 384 jetons si le telephone a assez de memoire libre apres avoir charge le modele,
     * sinon 8 192. Le moteur plafonne de toute facon ce chiffre a ce que le modele sait faire,
     * et le reduit tout seul si la memoire manque finalement.
     */
    fun tailleSouhaitee(ramDisponible: Long, tailleModele: Long): Int =
        if (ramDisponible - tailleModele >= MARGE_GRANDE) GRANDE else BASE
}
