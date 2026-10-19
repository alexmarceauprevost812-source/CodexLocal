package com.codexlocal.agent

import android.content.Context
import com.codexlocal.project.Langages
import com.codexlocal.project.ImagesProjet
import com.codexlocal.project.ProjectStore
import com.codexlocal.project.Projet

/** Execute dans le projet ce que l'IA demande : creer, ecrire, lire. */
object AgentProjet {

    const val MAX_CARACTERES_ECRITURE = 200_000
    const val MAX_CARACTERES_LECTURE = 12_000
    private const val MAX_FICHIERS_CONTEXTE = 60

    /**
     * Applique les actions. Un fichier existant est remplace (l'ancien contenu est garde dans
     * les [Annulation]) ; un bloc possiblement coupe est ignore plutot qu'ecrit a moitie.
     * Aucune exception : chaque probleme devient une ligne dans `ignores`.
     *
     * [connus] : ce que l'IA connait des fichiers (chemin -> empreinte du contenu qu'elle a lu ou
     * ecrit, voir [FichiersConnus]). Quand il est donne, un fichier existant dont le contenu actuel
     * n'est pas exactement celui-la n'est JAMAIS remplace : l'IA l'ecrivait de memoire et aurait
     * perdu ce qu'il contient. Il reste tel quel, apparait dans `proteges` et son contenu est
     * ajoute aux `lectures` pour que l'IA le revoie et recommence en connaissance de cause.
     * `null` : aucune protection.
     */
    fun appliquer(
        context: Context,
        projet: Projet,
        actions: List<ActionFichier>,
        ignorerIncomplets: Boolean,
        connus: Map<String, String>? = null
    ): Bilan {
        val crees = ArrayList<String>()
        val modifies = ArrayList<String>()
        val ignores = ArrayList<String>()
        val lectures = ArrayList<LectureFichier>()
        val annulations = ArrayList<Annulation>()
        val proteges = ArrayList<String>()
        val ecrits = LinkedHashMap<String, String>()

        for (action in actions) {
            when (action) {
                is ActionFichier.Ecrire -> try {
                    when {
                        !action.complet && ignorerIncomplets ->
                            ignores.add("${action.chemin} (réponse coupée : fichier incomplet, demande-le de nouveau)")
                        action.contenu.isBlank() ->
                            ignores.add("${action.chemin} (contenu vide)")
                        action.contenu.length > MAX_CARACTERES_ECRITURE ->
                            ignores.add("${action.chemin} (fichier trop gros)")
                        else -> ecrire(
                            context, projet, action, connus, crees, modifies, proteges, lectures, annulations, ecrits
                        )
                    }
                } catch (e: Exception) {
                    // Fonction non suspendante : on peut tout attraper, un probleme sur un
                    // fichier ne doit pas empecher les suivants.
                    ignores.add("${action.chemin} : ${e.message ?: e.toString()}")
                }
                is ActionFichier.Lire -> {
                    val lue = lire(context, projet, action.chemin)
                    if (lectures.none { it.chemin == lue.chemin }) lectures.add(lue)
                }
                is ActionFichier.SansNom -> ignores.add(texteSansNom(action))
            }
        }
        return Bilan(crees, modifies, ignores, lectures, annulations, proteges, ecrits)
    }

    /** Pourquoi un gros bloc de code sans nom de fichier n'a rien cree. */
    fun texteSansNom(action: ActionFichier.SansNom): String =
        "un bloc de code ${action.langage} (${action.lignes} lignes) sans nom de fichier : " +
            "rien n'a été créé. Demande-lui d'écrire toujours le nom du fichier avant le code."

    private fun ecrire(
        context: Context,
        projet: Projet,
        action: ActionFichier.Ecrire,
        connus: Map<String, String>?,
        crees: MutableList<String>,
        modifies: MutableList<String>,
        proteges: MutableList<String>,
        lectures: MutableList<LectureFichier>,
        annulations: MutableList<Annulation>,
        ecrits: MutableMap<String, String>
    ) {
        val existant = ProjectStore.trouverFichier(context, projet, action.chemin)
        if (existant == null) {
            val fichier = ProjectStore.creerFichier(context, projet, action.chemin, action.contenu)
            crees.add(fichier.chemin)
            annulations.add(Annulation(fichier, null))
            ecrits[fichier.chemin] = FichiersConnus.empreinte(action.contenu)
            return
        }
        require(Langages.estTexte(existant.nom)) { "ce n'est pas un fichier texte" }
        val ancien = ProjectStore.lireTexte(context, existant)
        // L'IA connait le fichier s'il est tel qu'elle l'a lu ou ecrit (ecrit plus haut dans la meme
        // reponse compris). Change depuis (modifie a la main, annule...) : elle ne le connait plus.
        val connu = connus == null ||
            existant.chemin in crees || existant.chemin in modifies ||
            connus[existant.chemin] == FichiersConnus.empreinte(ancien)
        if (!connu) {
            if (existant.chemin !in proteges) proteges.add(existant.chemin)
            if (lectures.none { it.chemin == existant.chemin }) {
                lectures.add(lire(context, projet, existant.chemin))
            }
            return
        }
        ecrits[existant.chemin] = FichiersConnus.empreinte(action.contenu)
        if (ancien == action.contenu) return
        ProjectStore.ecrireTexte(context, existant, action.contenu)
        modifies.add(existant.chemin)
        annulations.add(Annulation(existant, ancien))
    }

    private fun lire(context: Context, projet: Projet, chemin: String): LectureFichier = try {
        val fichier = ProjectStore.trouverFichier(context, projet, chemin)
        if (fichier == null) {
            LectureFichier(chemin, null, "fichier introuvable")
        } else {
            val texte = ProjectStore.lireTexte(context, fichier)
            val tronque = texte.length > MAX_CARACTERES_LECTURE
            val coupe = if (tronque) {
                texte.take(MAX_CARACTERES_LECTURE) + "\n… (fichier tronqué)"
            } else {
                texte
            }
            LectureFichier(fichier.chemin, coupe, null, tronque)
        }
    } catch (e: Exception) {
        LectureFichier(chemin, null, e.message ?: "lecture impossible")
    }

    /** Remet le projet comme avant. Renvoie le nombre de fichiers retablis ou supprimes. */
    fun annuler(context: Context, annulations: List<Annulation>): Int {
        var fait = 0
        for (a in annulations.asReversed()) {
            val ok = try {
                if (a.ancien == null) {
                    ProjectStore.supprimerFichier(context, a.fichier)
                } else {
                    ProjectStore.ecrireTexte(context, a.fichier, a.ancien)
                    true
                }
            } catch (e: Exception) {
                false
            }
            if (ok) fait++
        }
        return fait
    }

    /** Ligne de contexte envoyee a l'IA : nom du projet et fichiers qu'il contient. */
    fun contexteProjet(context: Context, projet: Projet): String {
        val fichiers = runCatching { ProjectStore.listerFichiers(context, projet) }
            .getOrDefault(emptyList())
        val images = runCatching {
            ProjectStore.instantane(context, projet).keys.filter { ImagesProjet.estImage(it) }
        }.getOrDefault(emptyList())
        if (fichiers.isEmpty() && images.isEmpty()) return "[Projet « ${projet.nom} » : encore vide]"
        val liste = fichiers.take(MAX_FICHIERS_CONTEXTE).joinToString(", ") { it.chemin }
        val reste = fichiers.size - MAX_FICHIERS_CONTEXTE
        val texteImages = if (images.isEmpty()) "" else
            " — images : " + images.take(MAX_FICHIERS_CONTEXTE).joinToString(", ") { it }
        return "[Projet « ${projet.nom} » — fichiers : ${liste.ifEmpty { "aucun texte" }}" +
            (if (reste > 0) " (+$reste autres)" else "") + texteImages + "]"
    }
}
