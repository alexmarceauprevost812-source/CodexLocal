package com.codexlocal.agent

/**
 * Ce qui aide un modele local (petit) a bien coder dans le mode Codex : des consignes de methode
 * courtes, une temperature basse, et une relecture automatique ([VerificateurCode]) suivie d'au
 * plus [MAX_REPARATIONS] demandes de correction. Code pur, sans Android.
 */
object BonCodeur {

    /** Nombre maximal de tours « corrige ce que la relecture a trouve » par demande. */
    const val MAX_REPARATIONS = 2

    /**
     * Plafond de la temperature en mode code : un code qui compile demande peu de hasard.
     * Le reglage de l'utilisateur reste respecte quand il est plus bas.
     */
    const val TEMPERATURE_MAX_CODE = 0.2f

    fun temperature(demandee: Float): Float = minOf(demandee, TEMPERATURE_MAX_CODE)

    /**
     * Ajoutee aux consignes du mode Codex. Courte a dessein : chaque mot occupe la memoire du
     * modele, et un petit modele suit mieux cinq regles nettes que vingt.
     */
    const val METHODE =
        "Methode de travail pour le code :\n" +
            "1. Pour MODIFIER un fichier du projet qui existe deja, lis-le d'abord avec " +
            "=== LIRE: chemin === puis arrete-toi ; ne le recopie jamais de memoire.\n" +
            "2. Ecris des fichiers COMPLETS : jamais « ... », « reste du code », « inchange » ni " +
            "de morceaux a recopier.\n" +
            "3. Fais simple : une fonction = un role, des noms clairs, aucune bibliotheque externe " +
            "sans besoin reel.\n" +
            "4. Prevois les cas limites evidents : entree vide, fichier absent, valeur invalide.\n" +
            "5. Tu ne peux pas executer le code : ne dis jamais que tu l'as teste. Termine par une " +
            "ligne « A verifier : » avec une ou deux actions pour l'essayer."

    private fun liste(problemes: List<Probleme>) = problemes.joinToString("\n") { "• " + it.texte() }

    private fun pluriel(n: Int) = if (n > 1) "$n problèmes" else "1 problème"

    /** Message affiche dans le chat quand la relecture a trouve quelque chose (et que l'IA va corriger). */
    fun resume(problemes: List<Probleme>): String =
        "🔧 Relecture automatique : ${pluriel(problemes.size)} à corriger\n" + liste(problemes)

    /** Ce qui est envoye a l'IA : les problemes, et exactement quoi faire. */
    fun demandeReparation(problemes: List<Probleme>): String =
        "Relecture automatique de tes fichiers : ${pluriel(problemes.size)} trouvé(s).\n" +
            liste(problemes) + "\n\n" +
            "Corrige UNIQUEMENT les fichiers concernés : réécris-les en entier, chacun exactement ainsi :\n" +
            "=== FICHIER: chemin/du/fichier.ext ===\n" +
            "(contenu complet et corrigé)\n" +
            "=== FIN ===\n" +
            "Ne touche pas aux autres fichiers. Une seule phrase d'explication, pas plus."

    /** Ajoute sous la reponse quand il reste des problemes apres les corrections. */
    fun avertissement(problemes: List<Probleme>): String =
        "⚠️ Après relecture, il reste ${pluriel(problemes.size)} :\n" + liste(problemes) + "\n" +
            "Demande au modèle de les corriger, ou ouvre le fichier dans l'éditeur."
}
