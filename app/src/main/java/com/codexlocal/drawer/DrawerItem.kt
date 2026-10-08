package com.codexlocal.drawer

import com.codexlocal.project.Langage

/** Les entrees possibles du menu lateral. */
sealed class DrawerItem {

    /** Titre de rubrique, avec une action facultative ("＋"). */
    data class Section(
        val titre: String,
        val action: String? = null,
        val cleAction: String? = null
    ) : DrawerItem()

    data class LigneSession(
        val id: String,
        val titre: String,
        val sous: String,
        val actif: Boolean
    ) : DrawerItem()

    data class LigneProjet(
        val uri: String,
        val nom: String,
        val sous: String,
        val actif: Boolean
    ) : DrawerItem()

    data class LigneFichier(
        val nom: String,
        val chemin: String,
        val uri: String,
        val langage: Langage
    ) : DrawerItem()

    /** Petit texte d'information (chargement, dossier vide…). */
    data class Info(val texte: String) : DrawerItem()

    /** Action "Ouvrir un dossier" quand aucun projet n'est selectionne. */
    data class Bouton(val libelle: String, val cleAction: String) : DrawerItem()
}
