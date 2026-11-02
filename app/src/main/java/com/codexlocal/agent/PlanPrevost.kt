package com.codexlocal.agent

/**
 * Plan Prévost : aucune modification de fichier ni commande n'est exécutée par cette classe.
 * Le moteur d'exécution doit afficher [previsualiser] et demander une confirmation explicite.
 */
data class EtapePrevost(
    val type: TypeEtapePrevost,
    val chemin: String,
    val contenu: String? = null
)

enum class TypeEtapePrevost { LIRE, ECRIRE, TESTER }

data class PlanPrevost(val projet: String, val etapes: List<EtapePrevost>) {
    init {
        require(projet.isNotBlank()) { "Projet manquant" }
        require(etapes.isNotEmpty() && etapes.size <= 30) { "Plan vide ou trop volumineux" }
        etapes.forEach { etape ->
            require(cheminRelatifSur(etape.chemin)) { "Chemin interdit : ${etape.chemin}" }
            if (etape.type == TypeEtapePrevost.ECRIRE) {
                require(etape.contenu != null && etape.contenu.length <= 200_000) {
                    "Contenu manquant ou trop volumineux"
                }
            } else {
                require(etape.contenu == null) { "Contenu non attendu" }
            }
        }
    }

    val exigeConfirmation: Boolean
        get() = etapes.any { it.type != TypeEtapePrevost.LIRE }

    fun previsualiser(): String = buildString {
        append("Projet : ").append(projet).append('\n')
        etapes.forEachIndexed { index, etape ->
            append(index + 1).append(". ").append(etape.type.name)
                .append(" : ").append(etape.chemin).append('\n')
        }
        append(if (exigeConfirmation) "Confirmation requise avant exécution." else "Lecture seule.")
    }
}

/**
 * N'autorise que des chemins logiques de projet. Le ProjectStore doit encore résoudre
 * chaque chemin via le Storage Access Framework et vérifier les permissions Android.
 */
internal fun cheminRelatifSur(chemin: String): Boolean {
    if (chemin.isBlank() || chemin.length > 240 || chemin.startsWith("/") ||
        chemin.startsWith("\\") || ':' in chemin || '\\' in chemin ||
        '\u0000' in chemin || '%' in chemin) return false
    val segments = chemin.split('/')
    if (segments.any { it.isBlank() || it == "." || it == ".." }) return false
    val interdits = setOf(".git", ".ssh", ".gnupg", "models", "modeles", "gguf", "secrets")
    return segments.none { it.lowercase() in interdits } &&
        !chemin.lowercase().endsWith(".gguf")
}
