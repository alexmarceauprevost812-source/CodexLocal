package com.codexlocal.agent

import com.codexlocal.AppConfig
import com.codexlocal.ProviderType

/** Consignes donnees a l'IA pour travailler directement dans le projet. */
object AgentPrompts {

    const val FICHIERS =
        "Tu peux travailler directement dans le projet ouvert : tes fichiers sont crees " +
            "automatiquement sur le telephone. Pour creer ou remplacer un fichier, ecris " +
            "EXACTEMENT ces trois elements, chacun sur sa propre ligne :\n" +
            "=== FICHIER: chemin/relatif.ext ===\n" +
            "(le contenu complet du fichier)\n" +
            "=== FIN ===\n" +
            "Pour lire un fichier existant avant de le modifier, ecris seule sur une ligne " +
            "=== LIRE: chemin/relatif.ext === puis arrete-toi : son contenu te sera envoye. " +
            "Regles : chemins relatifs sans '..' ni dossier cache ; toujours le fichier " +
            "COMPLET (jamais de morceaux ni de « ... ») ; pas de triple backquote autour " +
            "d'un fichier ; une ou deux phrases d'explication avant ou apres sont bienvenues. " +
            "Pour une simple question ou explication, reponds normalement sans ces marqueurs.\n" +
            "Si on te demande une application, un jeu, un outil ou une page web, fabrique une " +
            "APPLICATION LOCALE pour telephone Android : un site en HTML, CSS et JavaScript qui " +
            "marche sans internet. La page d'accueil est TOUJOURS index.html a la racine du projet. " +
            "Utilise des chemins relatifs (css/style.css, js/app.js), aucune ressource en ligne " +
            "(pas de CDN, de police web, d'API distante), une mise en page adaptee a un ecran de " +
            "telephone (balise viewport, gros boutons). Les images deja ajoutees au projet sont " +
            "listees avec leur nom exact entre crochets : utilise-les telles quelles."

    /** Sans projet ouvert, l'equipe donne le code dans la conversation. */
    const val SANS_PROJET =
        "Aucun projet n'est ouvert : donne chaque fichier dans un bloc avec triple backquote, " +
            "precede du nom du fichier."
}

/** Les cinq etapes de l'equipe, dans l'ordre. */
enum class Role(
    val emoji: String,
    val titre: String,
    /** Libelle court affiche dans le panneau du haut. */
    val court: String,
    val ecrit: Boolean,
    private val consigne: String
) {
    CHEF(
        "⛑️", "Chef de chantier", "Chef", false,
        "Tu es le CHEF DE CHANTIER (casque blanc) d'une equipe de quatre : toi, le " +
            "Planificateur, le Redacteur et le Codeur. Lis la demande et reponds en cinq " +
            "lignes maximum, sans code : 1) l'objectif en une phrase ; 2) la consigne pour " +
            "le Planificateur ; 3) la consigne pour le Redacteur (textes) ; 4) la consigne " +
            "pour le Codeur. Reponds en francais."
    ),
    PLANIFICATEUR(
        "📐", "Planificateur", "Plan", false,
        "Tu es le PLANIFICATEUR de l'equipe. Tu ne fais PAS de code. Reponds uniquement " +
            "avec : « Objectif » (une phrase), puis « Fichiers » (une ligne par fichier au " +
            "format chemin/relatif.ext — role du fichier, 8 fichiers au maximum), puis " +
            "« Etapes » (3 a 6 etapes numerotees). Francais, concis."
    ),
    REDACTEUR(
        "✍️", "Rédacteur", "Textes", true,
        "Tu es le REDACTEUR de l'equipe. Tu ecris les textes du projet : README.md " +
            "(presentation et mode d'emploi), textes affiches a l'utilisateur, messages, " +
            "documentation. Tu n'ecris pas de code logiciel. Francais clair et concis."
    ),
    CODEUR(
        "💻", "Codeur", "Code", true,
        "Tu es le CODEUR de l'equipe : le meilleur developpeur. Ecris le code complet, " +
            "correct et pret a l'emploi des fichiers de code prevus au plan, en reprenant " +
            "les textes du Redacteur tels quels. Jamais de morceaux ni de « ... » : chaque " +
            "fichier est complet. Commentaires courts, en francais."
    ),
    REVUE(
        "⛑️", "Chef de chantier — revue", "Revue", false,
        "Tu es le CHEF DE CHANTIER (casque blanc). Relis le travail de ton equipe " +
            "(demande, plan, fichiers produits). Reponds en quatre lignes maximum, sans " +
            "code : ✅ ce qui est fait ; ⚠️ ce qui manque ou risque de planter ; ➡️ la " +
            "prochaine etape conseillee. Francais."
    );

    /** Premiere ligne du message de ce role dans le chat. */
    val entete: String get() = "$emoji $titre\n\n"

    /** Consignes completes du role, avec le protocole de fichiers quand il ecrit dans un projet. */
    fun systemPrompt(projetOuvert: Boolean): String = when {
        !ecrit -> consigne
        projetOuvert -> consigne + "\n\n" + AgentPrompts.FICHIERS
        else -> consigne + "\n\n" + AgentPrompts.SANS_PROJET
    }

    /** Longueur maximale de la reponse : courte pour les chefs, large pour le codeur. */
    fun maxTokens(cfg: AppConfig): Int {
        val large = if (cfg.provider == ProviderType.LOCAL) 3000 else 6000
        return when (this) {
            CHEF -> 350
            PLANIFICATEUR -> 700
            REDACTEUR -> 1500
            CODEUR -> maxOf(cfg.maxTokens, large)
            REVUE -> 450
        }
    }
}

/** Construction des demandes envoyees a chaque role. Code pur, sans Android. */
object Equipe {

    const val NB_ETAPES = 5
    private const val MAX_BRIEF = 1_200
    private const val MAX_PLAN = 2_500
    private const val MAX_TEXTES = 3_000

    fun demandeChef(demande: String, contexte: String?) =
        assembler("Demande de l'utilisateur :\n$demande", contexte)

    fun demandePlanificateur(demande: String, brief: String, contexte: String?) =
        assembler(
            "Demande de l'utilisateur :\n$demande\n\nConsignes du chef :\n${couper(brief, MAX_BRIEF)}",
            contexte
        )

    fun demandeRedacteur(demande: String, brief: String, plan: String, contexte: String?) =
        assembler(
            "Demande de l'utilisateur :\n$demande\n\n" +
                "Consignes du chef :\n${couper(brief, MAX_BRIEF)}\n\n" +
                "Plan :\n${couper(plan, MAX_PLAN)}",
            contexte
        )

    fun demandeCodeur(
        demande: String, brief: String, plan: String, textes: String, contexte: String?
    ) = assembler(
        "Demande de l'utilisateur :\n$demande\n\n" +
            "Consignes du chef :\n${couper(brief, MAX_BRIEF)}\n\n" +
            "Plan :\n${couper(plan, MAX_PLAN)}\n\n" +
            "Textes du rédacteur :\n${couper(textes, MAX_TEXTES)}",
        contexte
    )

    fun demandeRevue(demande: String, plan: String, bilan: Bilan) =
        "Demande de l'utilisateur :\n$demande\n\nPlan :\n${couper(plan, MAX_PLAN)}\n\n" +
            "Fichiers produits par l'équipe :\n" +
            (bilan.resume().ifBlank { "(aucun fichier écrit)" })

    private fun assembler(corps: String, contexte: String?) =
        if (contexte.isNullOrBlank()) corps else "$contexte\n\n$corps"

    private fun couper(texte: String, max: Int) =
        if (texte.length <= max) texte else texte.take(max).trimEnd() + "\n… (coupé)"

    /** Derniere ligne non vide d'un texte en cours d'ecriture, pour le panneau du haut. */
    fun derniereLigne(texte: String, max: Int = 90): String {
        val ligne = texte.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: return ""
        return if (ligne.length <= max) ligne else ligne.takeLast(max)
    }
}
