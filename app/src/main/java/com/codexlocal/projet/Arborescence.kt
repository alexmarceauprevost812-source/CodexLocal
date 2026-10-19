package com.codexlocal.projet

import com.codexlocal.project.FichierProjet

/**
 * Navigation dans un projet comme sur GitHub : on voit d'abord les dossiers, puis les
 * fichiers du dossier courant, et on entre dans un dossier d'un appui.
 */
object Arborescence {

    data class Dossier(val nom: String, val chemin: String, val elements: Int)

    data class Contenu(val dossiers: List<Dossier>, val fichiers: List<FichierProjet>)

    /** « src/utils » -> « src » ; « src » -> « » (la racine). */
    fun parent(chemin: String): String = chemin.substringBeforeLast('/', "")

    /** Les etapes du chemin avec leur chemin complet : « a/b » -> [(a, a), (b, a/b)]. */
    fun segments(dossier: String): List<Pair<String, String>> {
        if (dossier.isEmpty()) return emptyList()
        val resultat = ArrayList<Pair<String, String>>()
        var cumul = ""
        for (nom in dossier.split('/').filter { it.isNotEmpty() }) {
            cumul = if (cumul.isEmpty()) nom else "$cumul/$nom"
            resultat.add(nom to cumul)
        }
        return resultat
    }

    /**
     * Contenu direct d'un dossier. `dossiers` vient de la liste des dossiers du projet
     * (y compris les vides) ; les dossiers sous-entendus par les fichiers sont ajoutes.
     */
    fun contenu(dossier: String, fichiers: List<FichierProjet>, dossiers: List<String>): Contenu {
        val tous = LinkedHashSet<String>()
        for (d in dossiers) ajouterAvecParents(tous, d)
        for (f in fichiers) ajouterAvecParents(tous, parent(f.chemin))

        val sousDossiers = tous
            .filter { it.isNotEmpty() && parent(it) == dossier }
            .sortedBy { it.substringAfterLast('/').lowercase() }
            .map { chemin ->
                val enfants = tous.count { it.isNotEmpty() && parent(it) == chemin } +
                    fichiers.count { parent(it.chemin) == chemin }
                Dossier(chemin.substringAfterLast('/'), chemin, enfants)
            }
        val fichiersDirects = fichiers
            .filter { parent(it.chemin) == dossier }
            .sortedBy { it.nom.lowercase() }
        return Contenu(sousDossiers, fichiersDirects)
    }

    private fun ajouterAvecParents(ensemble: MutableSet<String>, chemin: String) {
        var courant = chemin
        while (courant.isNotEmpty()) {
            ensemble.add(courant)
            courant = parent(courant)
        }
    }
}
