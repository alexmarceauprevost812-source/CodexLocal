package com.codexlocal.bureau

import com.codexlocal.project.FichierProjet
import com.codexlocal.project.Projet

/** Une case de la grille du Bureau. */
sealed interface ElementBureau {

    /** Titre de rubrique sur toute la largeur. */
    data class Entete(val texte: String) : ElementBureau

    /** Petit texte sur toute la largeur (« Aucun projet… »). */
    data class Info(val texte: String) : ElementBureau

    /** Une entree de menu (Chat, Studio, Reglages…). */
    data class Menu(val action: ActionBureau) : ElementBureau

    data class ProjetTuile(val projet: Projet, val favori: Boolean) : ElementBureau

    data class FichierTuile(val projet: Projet, val fichier: FichierProjet) : ElementBureau
}

/** Compose la grille du Bureau : menu, favoris, puis les projets. */
object ConstructeurBureau {

    fun construire(
        projets: List<Projet>,
        favoris: Set<String>,
        trouver: (Projet, String) -> FichierProjet?,
        titres: Titres = Titres()
    ): List<ElementBureau> {
        val liste = ArrayList<ElementBureau>()
        ActionBureau.values().forEach { liste.add(ElementBureau.Menu(it)) }

        // Favoris : les projets et fichiers encore presents, dans un ordre stable.
        val tuilesFavorites = ArrayList<ElementBureau>()
        for (cle in favoris.sorted()) {
            when (val cible = Favoris.decoder(cle)) {
                is Favoris.Cible.DeProjet -> projets.firstOrNull { it.uri == cible.projetUri }
                    ?.let { tuilesFavorites.add(ElementBureau.ProjetTuile(it, true)) }
                is Favoris.Cible.DeFichier -> projets.firstOrNull { it.uri == cible.projetUri }
                    ?.let { projet ->
                        trouver(projet, cible.chemin)
                            ?.let { tuilesFavorites.add(ElementBureau.FichierTuile(projet, it)) }
                    }
                null -> Unit
            }
        }
        if (tuilesFavorites.isNotEmpty()) {
            liste.add(ElementBureau.Entete(titres.favoris))
            liste.addAll(tuilesFavorites)
        }

        liste.add(ElementBureau.Entete(titres.projets))
        if (projets.isEmpty()) {
            liste.add(ElementBureau.Info(titres.aucunProjet))
        } else {
            projets.forEach {
                liste.add(ElementBureau.ProjetTuile(it, Favoris.cleProjet(it.uri) in favoris))
            }
        }
        return liste
    }

    /** Les textes des rubriques (la vraie activite les prend dans strings.xml). */
    class Titres(
        val favoris: String = "⭐ Favoris",
        val projets: String = "📁 Projets",
        val aucunProjet: String = "Aucun projet."
    )
}
