package com.codexlocal.agent

import com.codexlocal.project.FichierProjet

/** Pour revenir en arriere : `ancien` vaut null quand le fichier a ete cree. */
data class Annulation(val fichier: FichierProjet, val ancien: String?)

/**
 * Un fichier lu pour l'IA. `chemin` est le chemin exact dans le projet quand le fichier existe.
 * `tronque` : le fichier est trop long, l'IA n'en voit que le debut.
 */
data class LectureFichier(
    val chemin: String,
    val texte: String?,
    val erreur: String?,
    val tronque: Boolean = false
) {
    /** Vrai quand l'IA voit maintenant tout le fichier : elle peut le reecrire sans rien perdre. */
    val vuEnEntier: Boolean get() = texte != null && !tronque

    /** Empreinte de ce que l'IA voit (voir [FichiersConnus]), ou null si elle n'en voit qu'une partie. */
    val empreinte: String? get() = if (vuEnEntier) FichiersConnus.empreinte(texte!!) else null
}

/** Ce qui s'est reellement passe dans le projet pour une reponse de l'IA. */
data class Bilan(
    val crees: List<String> = emptyList(),
    val modifies: List<String> = emptyList(),
    val ignores: List<String> = emptyList(),
    val lectures: List<LectureFichier> = emptyList(),
    val annulations: List<Annulation> = emptyList(),
    /** Fichiers qui existent deja et que l'IA voulait remplacer sans les avoir lus : laisses tels quels. */
    val proteges: List<String> = emptyList(),
    /** Fichiers ecrits par l'IA, donc connus d'elle : chemin -> empreinte du contenu ecrit. */
    val ecrits: Map<String, String> = emptyMap()
) {
    val aEcrit: Boolean get() = crees.isNotEmpty() || modifies.isNotEmpty()

    /** Fichiers que l'IA verra en entier une fois les lectures renvoyees : chemin -> empreinte. */
    val lusEnEntier: Map<String, String>
        get() = lectures.mapNotNull { l -> l.empreinte?.let { l.chemin to it } }.toMap()

    /** Texte ajoute sous la reponse de l'IA dans le chat. Vide si rien ne s'est passe. */
    fun resume(): String = buildList {
        if (crees.isNotEmpty()) add("📁 Créé : " + crees.joinToString(", "))
        if (modifies.isNotEmpty()) add("✏️ Modifié : " + modifies.joinToString(", "))
        if (proteges.isNotEmpty()) {
            add(
                "🔒 Protégé : " + proteges.joinToString(", ") +
                    " (existe déjà, pas remplacé : l'IA doit d'abord le relire)"
            )
        }
        if (lectures.isNotEmpty()) add("📖 Lu : " + lectures.joinToString(", ") { it.chemin })
        ignores.forEach { add("⚠️ Ignoré : $it") }
    }.joinToString("\n")

    /** Message renvoye a l'IA avec le contenu des fichiers qu'elle a demande a lire. */
    fun texteLectures(): String = buildString {
        val protegesLus = lectures.filter { it.chemin in proteges }
        if (protegesLus.isEmpty()) {
            append("Voici le contenu des fichiers demandés.\n")
        } else {
            append("STOP : ").append(protegesLus.joinToString(", ") { it.chemin })
            append(
                if (protegesLus.size == 1) {
                    " existe déjà dans le projet et n'a PAS été remplacé, car tu ne l'avais pas lu."
                } else {
                    " existent déjà dans le projet et n'ont PAS été remplacés, car tu ne les avais pas lus."
                }
            )
            append(" Voici ce qu'ils contiennent.\n")
        }
        lectures.forEach { l ->
            append("\n--- Fichier ").append(l.chemin).append(" ---\n")
            if (l.texte != null) append(l.texte).append('\n') else append("(").append(l.erreur).append(")\n")
        }
        if (lectures.any { it.tronque }) {
            append(
                "\nUn fichier est trop long pour être montré en entier : ne le réécris pas, " +
                    "mets ton code dans un NOUVEAU fichier."
            )
        }
        if (protegesLus.isEmpty()) {
            append("\nContinue ta tâche avec ces fichiers.")
        } else {
            append(
                "\nPour modifier un de ces fichiers, réécris-le EN ENTIER (=== FICHIER: nom === " +
                    "puis le contenu puis === FIN ===) en gardant tout ce qui existe et en changeant " +
                    "seulement ce que je t'ai demandé. Si tu voulais créer autre chose, prends un autre " +
                    "nom de fichier. N'écris que les fichiers qui changent."
            )
        }
    }

    operator fun plus(autre: Bilan) = Bilan(
        crees + autre.crees, modifies + autre.modifies, ignores + autre.ignores,
        lectures + autre.lectures, annulations + autre.annulations, proteges + autre.proteges,
        ecrits + autre.ecrits
    )
}
