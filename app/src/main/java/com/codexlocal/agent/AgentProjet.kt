package com.codexlocal.agent

import android.content.Context
import com.codexlocal.project.FichierProjet
import com.codexlocal.project.Langages
import com.codexlocal.project.ImagesProjet
import com.codexlocal.project.ProjectStore
import com.codexlocal.project.Projet

/** Pour revenir en arriere : `ancien` vaut null quand le fichier a ete cree. */
data class Annulation(val fichier: FichierProjet, val ancien: String?)

data class LectureFichier(val chemin: String, val texte: String?, val erreur: String?)

/** Ce qui s'est reellement passe dans le projet pour une reponse de l'IA. */
data class Bilan(
    val crees: List<String> = emptyList(),
    val modifies: List<String> = emptyList(),
    val ignores: List<String> = emptyList(),
    val lectures: List<LectureFichier> = emptyList(),
    val annulations: List<Annulation> = emptyList()
) {
    val aEcrit: Boolean get() = crees.isNotEmpty() || modifies.isNotEmpty()

    /** Texte ajoute sous la reponse de l'IA dans le chat. Vide si rien ne s'est passe. */
    fun resume(): String = buildList {
        if (crees.isNotEmpty()) add("📁 Créé : " + crees.joinToString(", "))
        if (modifies.isNotEmpty()) add("✏️ Modifié : " + modifies.joinToString(", "))
        if (lectures.isNotEmpty()) add("📖 Lu : " + lectures.joinToString(", ") { it.chemin })
        ignores.forEach { add("⚠️ Ignoré : $it") }
    }.joinToString("\n")

    /** Message renvoye a l'IA avec le contenu des fichiers qu'elle a demande a lire. */
    fun texteLectures(): String = buildString {
        append("Voici le contenu des fichiers demandés.\n")
        lectures.forEach { l ->
            append("\n--- Fichier ").append(l.chemin).append(" ---\n")
            if (l.texte != null) append(l.texte).append('\n') else append("(").append(l.erreur).append(")\n")
        }
        append("\nContinue ta tâche avec ces fichiers.")
    }

    operator fun plus(autre: Bilan) = Bilan(
        crees + autre.crees, modifies + autre.modifies, ignores + autre.ignores,
        lectures + autre.lectures, annulations + autre.annulations
    )
}

/** Execute dans le projet ce que l'IA demande : creer, ecrire, lire. */
object AgentProjet {

    const val MAX_CARACTERES_ECRITURE = 200_000
    const val MAX_CARACTERES_LECTURE = 12_000
    private const val MAX_FICHIERS_CONTEXTE = 60

    /**
     * Applique les actions. Un fichier existant est remplace (l'ancien contenu est garde dans
     * les [Annulation]) ; un bloc possiblement coupe est ignore plutot qu'ecrit a moitie.
     * Aucune exception : chaque probleme devient une ligne dans `ignores`.
     */
    fun appliquer(
        context: Context,
        projet: Projet,
        actions: List<ActionFichier>,
        ignorerIncomplets: Boolean
    ): Bilan {
        val crees = ArrayList<String>()
        val modifies = ArrayList<String>()
        val ignores = ArrayList<String>()
        val lectures = ArrayList<LectureFichier>()
        val annulations = ArrayList<Annulation>()

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
                        else -> ecrire(context, projet, action, crees, modifies, annulations)
                    }
                } catch (e: Exception) {
                    // Fonction non suspendante : on peut tout attraper, un probleme sur un
                    // fichier ne doit pas empecher les suivants.
                    ignores.add("${action.chemin} : ${e.message ?: e.toString()}")
                }
                is ActionFichier.Lire -> lectures.add(lire(context, projet, action.chemin))
                is ActionFichier.SansNom -> ignores.add(
                    "un bloc de code ${action.langage} (${action.lignes} lignes) sans nom de fichier : " +
                        "rien n'a été créé. Demande-lui d'écrire toujours le nom du fichier avant le code."
                )
            }
        }
        return Bilan(crees, modifies, ignores, lectures, annulations)
    }

    private fun ecrire(
        context: Context,
        projet: Projet,
        action: ActionFichier.Ecrire,
        crees: MutableList<String>,
        modifies: MutableList<String>,
        annulations: MutableList<Annulation>
    ) {
        val existant = ProjectStore.trouverFichier(context, projet, action.chemin)
        if (existant == null) {
            val fichier = ProjectStore.creerFichier(context, projet, action.chemin, action.contenu)
            crees.add(fichier.chemin)
            annulations.add(Annulation(fichier, null))
            return
        }
        require(Langages.estTexte(existant.nom)) { "ce n'est pas un fichier texte" }
        val ancien = ProjectStore.lireTexte(context, existant)
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
            val coupe = if (texte.length > MAX_CARACTERES_LECTURE) {
                texte.take(MAX_CARACTERES_LECTURE) + "\n… (fichier tronqué)"
            } else {
                texte
            }
            LectureFichier(chemin, coupe, null)
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
