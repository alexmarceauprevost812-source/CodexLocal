package com.codexlocal

/** Un modele propose au telechargement en un clic. */
data class CatalogModel(
    val label: String,
    val taille: String,
    val url: String,
    val fichier: String
)

/**
 * Catalogue des modeles GGUF conseilles.
 * Les liens sont verifies : chaque fichier existe reellement sur Hugging Face.
 */
object ModelCatalog {

    private const val BASE = "https://huggingface.co/Qwen"

    const val AUTRE = "Autre adresse (URL personnalisee)…"

    val modeles = listOf(
        CatalogModel(
            "Qwen2.5-Coder 3B — recommandé",
            "2,0 Go",
            "$BASE/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            "qwen2.5-coder-3b-instruct-q4_k_m.gguf"
        ),
        CatalogModel(
            "Qwen2.5-Coder 1.5B — rapide",
            "1,0 Go",
            "$BASE/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        ),
        CatalogModel(
            "Qwen2.5-Coder 7B — meilleure qualité",
            "4,5 Go",
            "$BASE/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf",
            "qwen2.5-coder-7b-instruct-q4_k_m.gguf"
        ),
        CatalogModel(
            "Qwen2.5-Coder 0.5B — très léger",
            "0,4 Go",
            "$BASE/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf"
        )
    )

    /** Libelles affiches dans la liste deroulante. */
    val libelles: List<String> = modeles.map { "${it.label}  (${it.taille})" } + AUTRE

    /** Index de l'entree "autre adresse". */
    val indexAutre: Int get() = modeles.size
}
