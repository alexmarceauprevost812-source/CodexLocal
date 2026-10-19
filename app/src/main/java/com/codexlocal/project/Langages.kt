package com.codexlocal.project

/** Identite visuelle d'un langage : nom, couleur officielle, etiquette courte. */
data class Langage(val nom: String, val couleur: Int, val etiquette: String)

/**
 * Reconnaissance du langage d'un fichier par son extension.
 * Les couleurs sont celles des marques officielles des langages.
 */
object Langages {

    private fun l(nom: String, couleur: Long, etiq: String) =
        Langage(nom, couleur.toInt(), etiq)

    private val parExtension: Map<String, Langage> = mapOf(
        "kt" to l("Kotlin", 0xFFA97BFF, "kt"),
        "kts" to l("Kotlin", 0xFFA97BFF, "kts"),
        "java" to l("Java", 0xFFE76F00, "java"),
        "py" to l("Python", 0xFF3776AB, "py"),
        "pyw" to l("Python", 0xFF3776AB, "py"),
        "js" to l("JavaScript", 0xFFF7DF1E, "js"),
        "mjs" to l("JavaScript", 0xFFF7DF1E, "js"),
        "jsx" to l("React", 0xFF61DAFB, "jsx"),
        "ts" to l("TypeScript", 0xFF3178C6, "ts"),
        "tsx" to l("TypeScript", 0xFF3178C6, "tsx"),
        "c" to l("C", 0xFF5C6BC0, "c"),
        "h" to l("En-tête C", 0xFF5C6BC0, "h"),
        "cpp" to l("C++", 0xFF00599C, "cpp"),
        "cc" to l("C++", 0xFF00599C, "cpp"),
        "cxx" to l("C++", 0xFF00599C, "cpp"),
        "hpp" to l("C++", 0xFF00599C, "hpp"),
        "cs" to l("C#", 0xFF239120, "cs"),
        "go" to l("Go", 0xFF00ADD8, "go"),
        "rs" to l("Rust", 0xFFDEA584, "rs"),
        "rb" to l("Ruby", 0xFFCC342D, "rb"),
        "php" to l("PHP", 0xFF777BB4, "php"),
        "swift" to l("Swift", 0xFFFA7343, "swift"),
        "dart" to l("Dart", 0xFF0175C2, "dart"),
        "lua" to l("Lua", 0xFF2C2D72, "lua"),
        "r" to l("R", 0xFF276DC3, "r"),
        "sh" to l("Shell", 0xFF89E051, "sh"),
        "bash" to l("Bash", 0xFF89E051, "sh"),
        "zsh" to l("Zsh", 0xFF89E051, "zsh"),
        "ps1" to l("PowerShell", 0xFF012456, "ps1"),
        "bat" to l("Batch", 0xFFC1F12E, "bat"),
        "html" to l("HTML", 0xFFE34F26, "html"),
        "htm" to l("HTML", 0xFFE34F26, "html"),
        "css" to l("CSS", 0xFF1572B6, "css"),
        "scss" to l("SCSS", 0xFFCF649A, "scss"),
        "vue" to l("Vue", 0xFF41B883, "vue"),
        "svelte" to l("Svelte", 0xFFFF3E00, "svelte"),
        "json" to l("JSON", 0xFFCBCB41, "json"),
        "xml" to l("XML", 0xFFE37933, "xml"),
        "yml" to l("YAML", 0xFFCB171E, "yml"),
        "yaml" to l("YAML", 0xFFCB171E, "yaml"),
        "toml" to l("TOML", 0xFF9C4221, "toml"),
        "ini" to l("INI", 0xFF8C8C8C, "ini"),
        "cfg" to l("Config", 0xFF8C8C8C, "cfg"),
        "conf" to l("Config", 0xFF8C8C8C, "conf"),
        "properties" to l("Propriétés", 0xFF8C8C8C, "props"),
        "md" to l("Markdown", 0xFF083FA1, "md"),
        "txt" to l("Texte", 0xFF8C8C8C, "txt"),
        "sql" to l("SQL", 0xFFE38C00, "sql"),
        "gradle" to l("Gradle", 0xFF02303A, "gradle"),
        "pro" to l("ProGuard", 0xFF8C8C8C, "pro"),
        "cmake" to l("CMake", 0xFF064F8C, "cmake"),
        "dockerfile" to l("Dockerfile", 0xFF2496ED, "docker"),
        "makefile" to l("Makefile", 0xFF427819, "mk"),
        "gitignore" to l("Git", 0xFFF05032, "git"),
        "csv" to l("CSV", 0xFF217346, "csv"),
        "log" to l("Journal", 0xFF8C8C8C, "log")
    )

    private val langageColoration = mapOf(
        "kt" to "kotlin", "kts" to "kotlin", "java" to "java",
        "py" to "python", "pyw" to "python",
        "js" to "javascript", "mjs" to "javascript", "jsx" to "javascript",
        "ts" to "typescript", "tsx" to "typescript",
        "c" to "c", "h" to "c", "cpp" to "cpp", "cc" to "cpp", "cxx" to "cpp", "hpp" to "cpp",
        "cs" to "csharp", "go" to "go", "rs" to "rust", "rb" to "ruby",
        "php" to "php", "swift" to "swift", "dart" to "dart",
        "sh" to "bash", "bash" to "bash", "zsh" to "bash", "ps1" to "powershell",
        "html" to "html", "htm" to "html", "xml" to "xml", "svg" to "xml",
        "yml" to "yaml", "yaml" to "yaml", "toml" to "toml",
        "ini" to "ini", "cfg" to "ini", "conf" to "ini", "properties" to "ini",
        "sh2" to "bash", "sql" to "sql", "gradle" to "groovy", "cmake" to "cmake",
        "dockerfile" to "dockerfile", "makefile" to "makefile",
        "gitignore" to "plain", "dockerignore" to "plain", "gitattributes" to "plain"
    )

    private val extensionsTexte = parExtension.keys + setOf(
        "markdown", "text", "env", "lock", "tmpl", "patch", "diff"
    )

    /** D'apres le nom du fichier (avec ou sans chemin). */
    fun pour(nomFichier: String): Langage {
        val nom = nomFichier.substringAfterLast('/')
        val ext = nom.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) parExtension[ext]?.let { return it }
        // fichiers sans extension mais connus
        val bas = nom.lowercase()
        if (bas == "dockerfile") return l("Dockerfile", 0xFF2496ED, "docker")
        if (bas == "makefile") return l("Makefile", 0xFF427819, "mk")
        if (bas.startsWith(".git")) return l("Git", 0xFFF05032, "git")
        if (bas.startsWith("readme")) return l("Markdown", 0xFF083FA1, "md")
        return l("Fichier", 0xFF8C8C8C, if (ext.isEmpty()) "?" else ext.take(4))
    }

    /**
     * Langage au sens du colorateur syntaxique. Les fichiers connus par leur nom
     * (Dockerfile, Makefile, CMakeLists.txt) passent avant l'extension ; une extension
     * inconnue est rendue telle quelle : le colorateur sait lire d'autres noms (md, scss, vue, lua...).
     */
    fun pourColoration(nomFichier: String): String {
        val nom = nomFichier.substringAfterLast('/')
        val bas = nom.lowercase()
        when {
            bas == "dockerfile" || bas.startsWith("dockerfile.") -> return "dockerfile"
            bas == "makefile" || bas == "gnumakefile" -> return "makefile"
            bas == "cmakelists.txt" -> return "cmake"
        }
        val ext = bas.substringAfterLast('.', "")
        return langageColoration[ext] ?: ext
    }

    /** Un fichier qu'on peut ouvrir comme du texte. */
    fun estTexte(nomFichier: String): Boolean {
        val nom = nomFichier.substringAfterLast('/')
        val ext = nom.substringAfterLast('.', "").lowercase()
        if (ext in extensionsTexte) return true
        val bas = nom.lowercase()
        return bas == "dockerfile" || bas == "makefile" || bas.startsWith(".git") ||
            bas.startsWith("readme") || bas.startsWith("license")
    }
}
