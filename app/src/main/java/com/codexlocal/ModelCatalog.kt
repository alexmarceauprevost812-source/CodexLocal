package com.codexlocal

/** Un modele propose au telechargement en un clic. */
data class CatalogModel(
    val label: String,
    val taille: String,
    val url: String,
    val fichier: String
)

/**
 * Catalogue des modeles GGUF libres conseilles.
 *
 * Tous les liens ont ete verifies un par un : le depot existe, n'est pas
 * protege par une licence a accepter, et le fichier Q4_K_M (ou Q8_0 quand
 * c'est la seule quantification publiee) est bien present.
 */
object ModelCatalog {

    private const val QWEN25 = "https://huggingface.co/Qwen"
    private const val QWEN3 = "https://huggingface.co/Qwen"

    const val AUTRE = "Autre adresse (URL personnalisée)…"

    val modeles = listOf(
        CatalogModel(
            "Qwen2.5-Coder 3B — recommandé pour le code",
            "2,0 Go",
            "$QWEN25/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            "qwen2.5-coder-3b-instruct-q4_k_m.gguf"
        ),
        CatalogModel(
            "Qwen2.5-Coder 1.5B — rapide",
            "1,0 Go",
            "$QWEN25/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        ),
        CatalogModel(
            "Qwen2.5-Coder 7B — meilleure qualité (plus lent)",
            "4,7 Go",
            "$QWEN25/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf",
            "qwen2.5-coder-7b-instruct-q4_k_m.gguf"
        ),
        CatalogModel(
            "Qwen2.5-Coder 0.5B — ultra léger",
            "0,4 Go",
            "$QWEN25/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf"
        ),
        CatalogModel(
            "Qwen3 4B — raisonnement récent",
            "2,5 Go",
            "$QWEN3/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf",
            "Qwen3-4B-Q4_K_M.gguf"
        ),
        CatalogModel(
            "Qwen3 0.6B — très léger et récent",
            "0,6 Go",
            "$QWEN3/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q8_0.gguf",
            "Qwen3-0.6B-Q8_0.gguf"
        ),
        CatalogModel(
            "DeepSeek-R1 Distill 1.5B — raisonnement",
            "1,1 Go",
            "https://huggingface.co/unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"
        ),
        CatalogModel(
            "SmolLM2 1.7B — généraliste léger",
            "1,0 Go",
            "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
            "smollm2-1.7b-instruct-q4_k_m.gguf"
        )
    )

    /** Libelles affiches dans la liste deroulante. */
    val libelles: List<String> = modeles.map { "${it.label}  (${it.taille})" } + AUTRE

    /** Index de l'entree "autre adresse". */
    val indexAutre: Int get() = modeles.size
}
